package com.maverickignite.holmes.workflow;

import com.maverickignite.holmes.catalog.MonitoredService;
import com.maverickignite.holmes.catalog.ServiceCatalog;
import com.maverickignite.holmes.common.BadRequestException;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.incident.Incident;
import com.maverickignite.holmes.incident.IncidentService;
import com.maverickignite.holmes.incident.IncidentStatus;
import com.maverickignite.holmes.incident.IncidentStore;
import com.maverickignite.holmes.incident.Severity;
import com.maverickignite.holmes.investigation.Investigation;
import com.maverickignite.holmes.investigation.InvestigationRepository;
import com.maverickignite.holmes.investigation.InvestigationService;
import com.maverickignite.holmes.investigation.InvestigationStatus;
import com.maverickignite.holmes.notification.NotificationMessage;
import com.maverickignite.holmes.notification.NotificationService;
import com.maverickignite.holmes.remediation.Remediation;
import com.maverickignite.holmes.remediation.RemediationService;
import com.maverickignite.holmes.remediation.RemediationStatus;
import com.maverickignite.holmes.remediation.RepoWorkspace;
import com.maverickignite.holmes.remediation.WorkspaceManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The end-to-end pipeline for one incident: clone the code (if accessible), investigate, attempt a
 * fix when the analysis says a code change will fix it, then notify. Runs on a worker thread and
 * holds no database transaction while the LLM works.
 */
@Slf4j
@Service
public class IncidentWorkflow {

  private static final List<String> CONFIDENCE_ORDER = List.of("LOW", "MEDIUM", "HIGH");

  private static final Set<IncidentStatus> WORKING =
      EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.FIXING);

  private final IncidentService incidents;
  private final IncidentStore store;
  private final InFlightWork inFlight;
  private final ServiceCatalog catalog;
  private final WorkspaceManager workspaces;
  private final InvestigationService investigations;
  private final InvestigationRepository investigationRepository;
  private final RemediationService remediation;
  private final NotificationService notifications;
  private final HolmesProperties properties;
  private final ExecutorService workers;

  public IncidentWorkflow(
      IncidentService incidents,
      IncidentStore store,
      InFlightWork inFlight,
      ServiceCatalog catalog,
      WorkspaceManager workspaces,
      InvestigationService investigations,
      InvestigationRepository investigationRepository,
      RemediationService remediation,
      NotificationService notifications,
      HolmesProperties properties,
      ExecutorService holmesWorkers) {
    this.incidents = incidents;
    this.store = store;
    this.inFlight = inFlight;
    this.catalog = catalog;
    this.workspaces = workspaces;
    this.investigations = investigations;
    this.investigationRepository = investigationRepository;
    this.remediation = remediation;
    this.notifications = notifications;
    this.properties = properties;
    this.workers = holmesWorkers;
  }

  /** Investigates an incident already claimed (status INVESTIGATING) by the scheduler. */
  public void process(long incidentId) {
    IncidentStatus outcome = IncidentStatus.FAILED;
    Incident incident = null;
    MonitoredService service = null;
    Investigation inv = null;
    Remediation fix = null;
    try {
      incident = incidents.get(incidentId);
      service = catalog.findByName(incident.getServiceName()).orElse(null);
      try (RepoWorkspace ws = checkoutQuietly(service, "inc" + incidentId)) {
        inv = investigations.investigate(incident, service, ws);
        if (inv.getStatus() == InvestigationStatus.COMPLETED) {
          if (inv.getSeverity() != null) {
            incidents.updateSeverity(
                incidentId, Severity.parse(inv.getSeverity(), incident.getSeverity()));
          }
          if (shouldAutoFix(service, inv, ws)
              && store.transitionIf(incidentId, WORKING, IncidentStatus.FIXING)) {
            fix = remediation.attemptFix(incident, service, inv, ws);
          }
          outcome = statusAfter(fix);
        }
      }
    } catch (Exception e) {
      log.error("Workflow for incident #{} crashed", incidentId, e);
    } finally {
      transitionQuietly(incidentId, outcome);
    }
    if (outcome != IncidentStatus.FAILED) {
      notifyQuietly(incidentId, service, inv, fix);
    }
  }

  /** Operator-requested fix, regardless of the service's auto-fix flag. */
  public void requestFix(long incidentId) {
    Incident incident = incidents.get(incidentId);
    MonitoredService service =
        catalog
            .findByName(incident.getServiceName())
            .orElseThrow(() -> new BadRequestException("Service " + incident.getServiceName() + " is not registered"));
    if (!workspaces.canAccess(service)) {
      throw new BadRequestException("No repository or git credentials configured for " + service.getName());
    }
    Investigation inv =
        investigationRepository
            .findFirstByIncidentIdAndStatusOrderByStartedAtDesc(incidentId, InvestigationStatus.COMPLETED)
            .orElseThrow(() -> new BadRequestException("Incident has no completed investigation yet"));
    incidents.changeByOperator(incidentId, IncidentStatus.FIXING);
    inFlight.start(incidentId);

    workers.submit(
        () -> {
          Remediation fix = null;
          try (RepoWorkspace ws = workspaces.checkout(service, "fix" + incidentId)) {
            fix = remediation.attemptFix(incident, service, inv, ws);
          } catch (Throwable e) {
            log.warn("Manual fix of incident #{} failed", incidentId, e);
          } finally {
            transitionQuietly(incidentId, statusAfter(fix));
            inFlight.finish(incidentId);
          }
          if (fix != null && fix.getStatus() == RemediationStatus.PR_OPENED) {
            notifyQuietly(incidentId, service, inv, fix);
          }
        });
  }

  private void transitionQuietly(long incidentId, IncidentStatus to) {
    try {
      if (!store.transitionIf(incidentId, WORKING, to)) {
        log.warn("Incident #{} was no longer in progress; leaving its status unchanged", incidentId);
      }
    } catch (RuntimeException e) {
      log.error("Could not set incident #{} to {}", incidentId, to, e);
    }
  }

  /** Notification failures are logged only: they must never undo a finished investigation. */
  private void notifyQuietly(
      long incidentId, MonitoredService service, Investigation inv, Remediation fix) {
    try {
      notify(incidents.get(incidentId), service, inv, fix);
    } catch (RuntimeException e) {
      log.error("Notifying incident #{} failed", incidentId, e);
    }
  }

  private RepoWorkspace checkoutQuietly(MonitoredService service, String purpose) {
    if (!workspaces.canAccess(service)) {
      return null;
    }
    try {
      return workspaces.checkout(service, purpose);
    } catch (Exception e) {
      log.warn("Could not clone {} ({}); investigating without code", service.getRepoUrl(), e.getMessage());
      return null;
    }
  }

  private boolean shouldAutoFix(MonitoredService service, Investigation inv, RepoWorkspace ws) {
    return properties.getRemediation().isEnabled()
        && service != null
        && service.isAutoFix()
        && ws != null
        && inv.isCodeFixRecommended()
        && confidenceAtLeast(inv.getConfidence(), properties.getRemediation().getMinConfidence());
  }

  static boolean confidenceAtLeast(String actual, String minimum) {
    int a = CONFIDENCE_ORDER.indexOf(actual == null ? "LOW" : actual.toUpperCase(Locale.ROOT));
    int m = CONFIDENCE_ORDER.indexOf(minimum == null ? "MEDIUM" : minimum.toUpperCase(Locale.ROOT));
    return a >= 0 && a >= Math.max(m, 0);
  }

  private static IncidentStatus statusAfter(Remediation fix) {
    return fix != null && fix.getStatus() == RemediationStatus.PR_OPENED
        ? IncidentStatus.PR_OPENED
        : IncidentStatus.INVESTIGATED;
  }

  private void notify(Incident incident, MonitoredService service, Investigation inv, Remediation fix) {
    String link = properties.getDashboardUrl().replaceFirst("/$", "") + "/incidents/" + incident.getId();
    NotificationMessage message =
        new NotificationMessage(
            incident.getId(),
            incident.getTitle(),
            incident.getServiceName(),
            incident.getEnvironment(),
            incident.getSeverity() == null ? "HIGH" : incident.getSeverity().name(),
            incident.getOccurrenceCount(),
            inv.getSummary(),
            inv.getRootCause(),
            inv.getSuggestedFix(),
            inv.getConfidence(),
            fix == null ? null : fixStatusText(fix),
            fix != null && fix.getStatus() == RemediationStatus.PR_OPENED ? fix.getPrUrl() : null,
            fix == null ? null : fix.getDescription(),
            link,
            service == null ? null : service.getSlackChannel(),
            service == null ? List.of() : Texts.splitCsv(service.getEmailRecipients()),
            service == null ? null : service.getTelegramChatId());
    List<String> sent = notifications.send(message);
    log.info("Incident #{} notified via {}", incident.getId(), sent);
  }

  private static String fixStatusText(Remediation fix) {
    return switch (fix.getStatus()) {
      case PR_OPENED -> "pull request opened";
      case NO_CHANGE -> "no code change made: " + Texts.truncate(fix.getReason(), 300);
      case REJECTED -> "change rejected by safety rules: " + fix.getReason();
      case FAILED -> "failed: " + Texts.truncate(fix.getError(), 300);
      case RUNNING -> "in progress";
    };
  }
}

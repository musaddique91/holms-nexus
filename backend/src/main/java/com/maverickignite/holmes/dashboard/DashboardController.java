package com.maverickignite.holmes.dashboard;

import com.maverickignite.holmes.incident.IncidentRepository;
import com.maverickignite.holmes.investigation.InvestigationKind;
import com.maverickignite.holmes.investigation.InvestigationRepository;
import com.maverickignite.holmes.notification.NotificationService;
import com.maverickignite.holmes.remediation.RemediationRepository;
import com.maverickignite.holmes.remediation.RemediationStatus;
import com.maverickignite.holmes.toolset.ToolRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
class DashboardController {

  private final IncidentRepository incidents;
  private final InvestigationRepository investigations;
  private final RemediationRepository remediations;
  private final ToolRegistry toolsets;
  private final NotificationService notifications;

  record ServiceStat(String service, long incidents, long occurrences) {}

  record Dashboard(
      Map<String, Long> byStatus,
      long newIncidents24h,
      long investigations24h,
      long pullRequestsOpened,
      List<ServiceStat> topServices7d,
      List<String> enabledToolsets,
      List<String> notificationChannels) {}

  @GetMapping
  Dashboard get() {
    Instant now = Instant.now();
    Map<String, Long> byStatus = new LinkedHashMap<>();
    for (Object[] row : incidents.countByStatus()) {
      byStatus.put(row[0].toString(), (Long) row[1]);
    }
    List<ServiceStat> top =
        incidents.countByServiceSince(now.minus(Duration.ofDays(7))).stream()
            .limit(10)
            .map(r -> new ServiceStat((String) r[0], (Long) r[1], r[2] == null ? 0 : ((Number) r[2]).longValue()))
            .toList();
    return new Dashboard(
        byStatus,
        incidents.countByFirstSeenAtAfter(now.minus(Duration.ofDays(1))),
        investigations.countByKindAndStartedAtAfter(InvestigationKind.INCIDENT, now.minus(Duration.ofDays(1))),
        remediations.countByStatus(RemediationStatus.PR_OPENED),
        top,
        toolsets.status(false).stream().filter(ToolRegistry.ToolsetStatus::enabled).map(ToolRegistry.ToolsetStatus::name).toList(),
        notifications.enabledChannels());
  }
}

package com.maverickignite.holmes.ingestion;

import com.maverickignite.holmes.catalog.MonitoredService;
import com.maverickignite.holmes.catalog.ServiceCatalog;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.incident.IncidentStore;
import com.maverickignite.holmes.incident.IncidentStore.NewOccurrence;
import com.maverickignite.holmes.incident.IncidentStore.UpsertResult;
import com.maverickignite.holmes.incident.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Turns an error event into an incident occurrence (new incident or a repeat of a known one). */
@Slf4j
@Service
public class IngestionService {

  private final ServiceCatalog catalog;
  private final Fingerprinter fingerprinter;
  private final IncidentStore store;
  private final Set<String> acceptedLevels;
  private final List<Pattern> ignorePatterns;

  public IngestionService(
      ServiceCatalog catalog,
      Fingerprinter fingerprinter,
      IncidentStore store,
      HolmesProperties properties) {
    this.catalog = catalog;
    this.fingerprinter = fingerprinter;
    this.store = store;
    this.acceptedLevels =
        properties.getIngestion().getLevels().stream()
            .map(l -> l.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    this.ignorePatterns =
        properties.getIngestion().getIgnorePatterns().stream().map(Pattern::compile).toList();
  }

  /** Returns the incident id, or {@code null} when the event was filtered out. */
  public Long ingest(ErrorEvent event) {
    if (!accepts(event)) {
      return null;
    }
    String service = catalog.canonicalName(event.service());
    String environment =
        Texts.firstNonBlank(
            event.environment(),
            catalog.findByName(service).map(MonitoredService::getEnvironment).orElse(null));

    Fingerprinter.Fingerprint fp =
        fingerprinter.fingerprint(service, environment, event.message(), event.stackTrace());

    UpsertResult result =
        store.recordOccurrence(
            new NewOccurrence(
                fp.hash(),
                service,
                environment,
                fp.title(),
                Texts.truncate(fp.errorType(), 300),
                event.level(),
                Texts.truncate(event.message(), 8_000),
                Texts.truncateMiddle(event.stackTrace(), 30_000),
                Texts.truncate(event.logger(), 300),
                Texts.truncate(event.host(), 200),
                event.source(),
                initialSeverity(event.level()),
                event.timestamp()));
    if (result.inserted()) {
      log.info("New incident #{} for {}: {}", result.id(), service, fp.title());
    }
    return result.id();
  }

  boolean accepts(ErrorEvent event) {
    if (event.level() == null || !acceptedLevels.contains(event.level())) {
      return false;
    }
    if (Texts.isBlank(event.message()) && Texts.isBlank(event.stackTrace())) {
      return false;
    }
    String haystack = event.message() == null ? "" : event.message();
    return ignorePatterns.stream().noneMatch(p -> p.matcher(haystack).find());
  }

  private static Severity initialSeverity(String level) {
    return switch (level == null ? "" : level) {
      case "FATAL", "CRITICAL" -> Severity.CRITICAL;
      default -> Severity.HIGH;
    };
  }
}

package com.maverickignite.holmes.ingestion;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.openobserve.OpenObserveClient;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Continuously pulls new error rows from an OpenObserve stream and publishes them into the
 * pipeline. Unlike OpenObserve alerts it needs no per-stream alert setup, and it resumes from a
 * persisted checkpoint after restarts.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "holmes.ingestion.poller.enabled", havingValue = "true")
class OpenObservePoller implements SchedulingConfigurer {

  private static final String CHECKPOINT = "openobserve-poller";
  private static final Pattern STREAM_NAME = Pattern.compile("[A-Za-z0-9_]+");

  private final OpenObserveClient client;
  private final ErrorEventMapper mapper;
  private final ErrorEventSink sink;
  private final IngestionCheckpointRepository checkpoints;
  private final HolmesProperties.Poller config;

  OpenObservePoller(
      OpenObserveClient client,
      ErrorEventMapper mapper,
      ErrorEventSink sink,
      IngestionCheckpointRepository checkpoints,
      HolmesProperties properties) {
    this.client = client;
    this.mapper = mapper;
    this.sink = sink;
    this.checkpoints = checkpoints;
    this.config = properties.getIngestion().getPoller();
    if (!STREAM_NAME.matcher(config.getStream()).matches()) {
      throw new IllegalArgumentException("Invalid OpenObserve stream name: " + config.getStream());
    }
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.addFixedDelayTask(this::pollSafely, config.getInterval());
  }

  void pollSafely() {
    if (!client.isConfigured()) {
      log.warn("OpenObserve poller enabled but holmes.openobserve credentials are missing");
      return;
    }
    try {
      poll();
    } catch (RuntimeException e) {
      log.warn("OpenObserve poll failed: {}", e.getMessage());
    }
  }

  void poll() {
    // Rows become searchable a few seconds after their timestamp; querying up to "now" would
    // move the checkpoint past rows that are still being ingested.
    long nowMicros = toMicros(Instant.now().minusSeconds(30));
    long from =
        checkpoints
            .findById(CHECKPOINT)
            .map(c -> c.getPosition() + 1)
            .orElse(toMicros(Instant.now().minus(config.getInitialLookback())));
    String sql =
        "SELECT * FROM \"" + config.getStream() + "\" WHERE " + config.getFilter()
            + " ORDER BY _timestamp ASC";

    List<JsonNode> rows = client.search(sql, from, nowMicros, config.getBatchSize());
    long maxSeen = from - 1;
    for (JsonNode row : rows) {
      mapper.events(row, "openobserve-poller").forEach(sink::publish);
      maxSeen = Math.max(maxSeen, row.path("_timestamp").asLong(0));
    }
    // A full page means more rows may remain; resume just after the last one seen.
    long next = rows.size() >= config.getBatchSize() ? maxSeen : nowMicros;
    checkpoints.save(new IngestionCheckpoint(CHECKPOINT, next));
    if (!rows.isEmpty()) {
      log.info("Pulled {} error rows from OpenObserve stream {}", rows.size(), config.getStream());
    }
  }

  private static long toMicros(Instant i) {
    return i.getEpochSecond() * 1_000_000 + i.getNano() / 1_000;
  }
}

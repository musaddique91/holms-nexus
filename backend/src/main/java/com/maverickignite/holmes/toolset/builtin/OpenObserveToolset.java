package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.openobserve.OpenObserveClient;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Log search in OpenObserve: the error's surrounding context, other services, custom SQL. */
@Component
class OpenObserveToolset implements Toolset {

  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,120}");

  private final OpenObserveClient client;
  private final boolean enabled;
  private final String stream;

  OpenObserveToolset(OpenObserveClient client, HolmesProperties properties) {
    this.client = client;
    this.enabled = properties.getToolsets().isOpenobserve();
    this.stream = properties.getIngestion().getPoller().getStream();
  }

  @Override
  public String name() {
    return "openobserve";
  }

  @Override
  public String description() {
    return "Search application logs stored in OpenObserve.";
  }

  @Override
  public boolean enabled() {
    return enabled && client.isConfigured();
  }

  @Override
  public List<ToolSpec> tools() {
    return List.of(
        new ToolSpec(
            "openobserve_service_logs",
            "Recent log lines of one service from OpenObserve (all levels unless 'level' is set),"
                + " newest first. Use it to see what happened right before an error.",
            Schemas.object()
                .string("service", "Service or container name as it appears in logs", true)
                .integer("minutes", "Look-back window in minutes (default 30, max 1440)", false)
                .enumeration("level", "Only this level", false, "ERROR", "WARN", "INFO", "DEBUG")
                .integer("limit", "Max rows (default 100, max 500)", false)
                .build(),
            this::serviceLogs),
        new ToolSpec(
            "openobserve_sql",
            "Run a read-only SQL query against OpenObserve log streams, e.g. SELECT service,"
                + " count(*) AS n FROM \"" + stream + "\" WHERE level='ERROR' GROUP BY service."
                + " Timestamps are in _timestamp (microseconds).",
            Schemas.object()
                .string("sql", "SELECT statement", true)
                .integer("minutes", "Look-back window in minutes (default 60, max 10080)", false)
                .integer("limit", "Max rows (default 100, max 500)", false)
                .build(),
            this::sql),
        new ToolSpec(
            "openobserve_streams",
            "List log streams in OpenObserve with document counts.",
            Schemas.empty(),
            args -> client.streams().toString()));
  }

  private String serviceLogs(JsonNode args) {
    String service = Args.required(args, "service");
    if (!SAFE.matcher(service).matches()) {
      throw new ToolException("Invalid service name");
    }
    String level = Args.optional(args, "level");
    if (level != null && !java.util.Set.of("ERROR", "WARN", "INFO", "DEBUG").contains(level.toUpperCase(Locale.ROOT))) {
      throw new ToolException("level must be one of ERROR, WARN, INFO, DEBUG");
    }
    int minutes = Args.integer(args, "minutes", 30, 1, 1440);
    int limit = Args.integer(args, "limit", 100, 1, 500);
    String sql =
        "SELECT _timestamp, level, message, stack_trace, logger_name FROM \"" + stream + "\""
            + " WHERE (service = '" + service + "' OR container_name = '" + service + "')"
            + (level == null ? "" : " AND level = '" + level.toUpperCase(Locale.ROOT) + "'")
            + " ORDER BY _timestamp DESC";
    return run(sql, minutes, limit);
  }

  private String sql(JsonNode args) {
    String sql = Args.required(args, "sql").strip();
    if (!sql.toLowerCase(Locale.ROOT).startsWith("select")) {
      throw new ToolException("Only SELECT statements are allowed");
    }
    return run(sql, Args.integer(args, "minutes", 60, 1, 10_080), Args.integer(args, "limit", 100, 1, 500));
  }

  private String run(String sql, int minutes, int limit) {
    Instant now = Instant.now();
    List<JsonNode> rows =
        client.search(sql, micros(now.minus(Duration.ofMinutes(minutes))), micros(now), limit);
    if (rows.isEmpty()) {
      return "No rows in the last " + minutes + " minutes.";
    }
    StringBuilder sb = new StringBuilder(rows.size() + " rows:\n");
    rows.forEach(r -> sb.append(r).append('\n'));
    return sb.toString();
  }

  private static long micros(Instant i) {
    return i.getEpochSecond() * 1_000_000 + i.getNano() / 1_000;
  }

  @Override
  public ToolsetHealth health() {
    JsonNode streams = client.streams();
    return ToolsetHealth.ok(streams.path("list").size() + " streams");
  }
}

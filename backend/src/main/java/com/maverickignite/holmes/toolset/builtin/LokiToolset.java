package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Loki: LogQL log stream queries, metric aggregations, indexed labels and stream inspection.
 */
@Component
class LokiToolset implements Toolset {

  private final HolmesProperties.Loki config;
  private final RestClient http;

  LokiToolset(HolmesProperties properties, RestClient.Builder builder) {
    this.config = properties.getToolsets().getLoki();
    RestClient.Builder b = builder.clone().requestFactory(HttpClients.standard());
    if (!Texts.isBlank(config.getUrl())) {
      b.baseUrl(config.getUrl());
    }
    if (!Texts.isBlank(config.getBearerToken())) {
      b.defaultHeader("Authorization", "Bearer " + config.getBearerToken());
    }
    if (!Texts.isBlank(config.getTenantId())) {
      b.defaultHeader("X-Scope-OrgID", config.getTenantId());
    }
    this.http = b.build();
  }

  @Override
  public String name() {
    return "loki";
  }

  @Override
  public String description() {
    return "Loki log aggregation: LogQL stream search, container logs, labels and log metrics.";
  }

  @Override
  public boolean enabled() {
    return config.isEnabled() && !Texts.isBlank(config.getUrl());
  }

  @Override
  public List<ToolSpec> tools() {
    return List.of(
        new ToolSpec(
            "loki_query_range",
            "Range LogQL query to search log streams over a time window, e.g."
                + " {app=\"water-backend\"} |= \"error\" or"
                + " {container_name=~\"maverick.*\"} | json | level=\"ERROR\".",
            Schemas.object()
                .string("query", "LogQL query expression", true)
                .string(
                    "start",
                    "Start time (e.g. '30m' ago, RFC3339 timestamp, or Unix nanoseconds; default: 30m ago)",
                    false)
                .string("end", "End time (RFC3339, Unix nanoseconds, or omit for now)", false)
                .integer("limit", "Maximum number of log entries to return (default: 100, max: 1000)", false)
                .enumeration("direction", "Sort direction (default: BACKWARD)", false, "BACKWARD", "FORWARD")
                .build(),
            args -> {
              String q = Args.required(args, "query");
              String start = Texts.firstNonBlank(Args.optional(args, "start"), resolveOffset("30m"));
              String end = Texts.firstNonBlank(Args.optional(args, "end"), Instant.now().toString());
              int limit = Args.integer(args, "limit", 100, 1, 1000);
              String direction = Texts.firstNonBlank(Args.optional(args, "direction"), "BACKWARD");

              JsonNode root =
                  http.get()
                      .uri(
                          "/loki/api/v1/query_range?query={q}&start={start}&end={end}&limit={limit}&direction={direction}",
                          q,
                          start,
                          end,
                          limit,
                          direction)
                      .retrieve()
                      .body(JsonNode.class);

              return formatQueryResult(root, q);
            }),
        new ToolSpec(
            "loki_instant_query",
            "Instant LogQL metric query evaluated at a single point in time, e.g."
                + " rate({app=\"water-backend\"}[5m]) or"
                + " count_over_time({container_name=\"orders-backend\"}[1h]).",
            Schemas.object()
                .string("query", "LogQL metric query expression", true)
                .string("time", "Evaluation timestamp (RFC3339 or Unix seconds, optional)", false)
                .build(),
            args -> {
              String q = Args.required(args, "query");
              String time = Args.optional(args, "time");
              if (Texts.isBlank(time)) {
                return http.get()
                    .uri("/loki/api/v1/query?query={q}", q)
                    .retrieve()
                    .body(String.class);
              } else {
                return http.get()
                    .uri("/loki/api/v1/query?query={q}&time={time}", q, time)
                    .retrieve()
                    .body(String.class);
              }
            }),
        new ToolSpec(
            "loki_labels",
            "List all indexed label names in Loki (e.g. app, container_name, level, environment, client).",
            Schemas.empty(),
            args -> {
              JsonNode root = http.get().uri("/loki/api/v1/labels").retrieve().body(JsonNode.class);
              if (root == null || !root.path("data").isArray()) {
                return "[]";
              }
              StringBuilder sb = new StringBuilder("Loki labels:\n");
              for (JsonNode item : root.path("data")) {
                sb.append("  - ").append(item.asString()).append('\n');
              }
              return sb.toString();
            }),
        new ToolSpec(
            "loki_label_values",
            "List all indexed values for a specific label name (e.g. label 'app' or 'container_name').",
            Schemas.object().string("name", "Label name (e.g. 'app', 'environment')", true).build(),
            args -> {
              String name = Args.required(args, "name");
              JsonNode root =
                  http.get().uri("/loki/api/v1/label/{name}/values", name).retrieve().body(JsonNode.class);
              if (root == null || !root.path("data").isArray()) {
                return "[]";
              }
              StringBuilder sb = new StringBuilder("Values for '").append(name).append("':\n");
              for (JsonNode item : root.path("data")) {
                sb.append("  - ").append(item.asString()).append('\n');
              }
              return sb.toString();
            }));
  }

  private String formatQueryResult(JsonNode root, String query) {
    if (root == null) {
      return "No response from Loki.";
    }
    String resultType = root.path("data").path("resultType").asString("");
    JsonNode results = root.path("data").path("result");
    if (!results.isArray() || results.isEmpty()) {
      return "No logs found for query: " + query;
    }

    if ("streams".equals(resultType)) {
      StringBuilder sb = new StringBuilder();
      int totalEntries = 0;
      for (JsonNode streamObj : results) {
        JsonNode streamLabels = streamObj.path("stream");
        JsonNode values = streamObj.path("values");
        sb.append("--- stream: ").append(streamLabels.toString()).append(" ---\n");
        if (values.isArray()) {
          for (JsonNode entry : values) {
            if (entry.isArray() && entry.size() >= 2) {
              String ts = entry.get(0).asString();
              String line = entry.get(1).asString();
              sb.append('[').append(ts).append("] ").append(line).append('\n');
              totalEntries++;
            }
          }
        }
        sb.append('\n');
      }
      return "Returned " + totalEntries + " log line(s):\n" + sb;
    }

    // Metric / vector / matrix results
    return results.toString();
  }

  private static String resolveOffset(String offset) {
    if (offset.endsWith("m") || offset.endsWith("h") || offset.endsWith("d")) {
      try {
        long minutes = parseDurationMinutes(offset);
        return Instant.now().minus(Duration.ofMinutes(minutes)).toString();
      } catch (Exception ignored) {
      }
    }
    return offset;
  }

  private static long parseDurationMinutes(String s) {
    if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1));
    if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60;
    if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1440;
    return 30;
  }

  @Override
  public ToolsetHealth health() {
    try {
      String ready = http.get().uri("/ready").retrieve().body(String.class);
      if (ready != null && ready.contains("ready")) {
        return ToolsetHealth.ok("Loki ready");
      }
      return ToolsetHealth.ok("Loki reachable");
    } catch (Exception ex) {
      return ToolsetHealth.failed("Loki unreachable: " + ex.getMessage());
    }
  }
}

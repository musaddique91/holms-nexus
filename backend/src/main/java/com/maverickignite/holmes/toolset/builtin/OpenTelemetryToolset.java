package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.openobserve.OpenObserveClient;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * OpenTelemetry: distributed trace inspection, span hierarchy, and error span diagnostics.
 */
@Component
class OpenTelemetryToolset implements Toolset {

  private final HolmesProperties.OpenTelemetry config;
  private final OpenObserveClient openobserve;
  private final RestClient http;

  OpenTelemetryToolset(
      HolmesProperties properties, OpenObserveClient openobserve, RestClient.Builder builder) {
    this.config = properties.getToolsets().getOpentelemetry();
    this.openobserve = openobserve;
    RestClient.Builder b = builder.clone().requestFactory(HttpClients.standard());
    String base = Texts.firstNonBlank(config.getTracesUrl(), config.getUrl());
    if (!Texts.isBlank(base)) {
      b.baseUrl(base);
    }
    if (!Texts.isBlank(config.getBearerToken())) {
      b.defaultHeader("Authorization", "Bearer " + config.getBearerToken());
    }
    this.http = b.build();
  }

  @Override
  public String name() {
    return "opentelemetry";
  }

  @Override
  public String description() {
    return "OpenTelemetry distributed traces, span hierarchy, and latency/error diagnostics.";
  }

  @Override
  public boolean enabled() {
    return config.isEnabled()
        && (!Texts.isBlank(config.getUrl()) || !Texts.isBlank(config.getTracesUrl()) || openobserve.isConfigured());
  }

  @Override
  public List<ToolSpec> tools() {
    return List.of(
        new ToolSpec(
            "otel_trace_by_id",
            "Fetch distributed trace spans by 32-character hexadecimal trace ID (e.g. from error context or logs)."
                + " Shows full span execution graph, service hops, error events and latencies.",
            Schemas.object().string("trace_id", "32-hex trace ID", true).build(),
            args -> {
              String traceId = Args.required(args, "trace_id").trim();
              // 1. Try dedicated traces API if configured
              if (!Texts.isBlank(config.getTracesUrl())) {
                try {
                  return http.get().uri("/api/traces/{id}", traceId).retrieve().body(String.class);
                } catch (Exception ignored) {
                }
              }

              // 2. Query OpenObserve traces stream if available
              if (openobserve.isConfigured()) {
                try {
                  String sql =
                      "SELECT * FROM \"traces\" WHERE trace_id = '"
                          + traceId.replace("'", "''")
                          + "' ORDER BY start_time ASC";
                  var rows = openobserve.search(sql, 0, System.currentTimeMillis() * 1000, 100);
                  if (!rows.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Found " + rows.size() + " trace span(s):\n");
                    rows.forEach(r -> sb.append(r.toString()).append('\n'));
                    return sb.toString();
                  }
                } catch (Exception ignored) {
                }
              }

              return "Trace " + traceId + " not found or no trace backend returned results.";
            }),
        new ToolSpec(
            "otel_search_error_traces",
            "Search recent distributed traces that contain errors across services.",
            Schemas.object()
                .string("service", "Service name filter (optional)", false)
                .integer("minutes", "Lookback window in minutes (default: 30)", false)
                .integer("limit", "Maximum traces to return (default: 20)", false)
                .build(),
            args -> {
              if (openobserve.isConfigured()) {
                try {
                  String service = Args.optional(args, "service");
                  int minutes = Args.integer(args, "minutes", 30, 1, 1440);
                  int limit = Args.integer(args, "limit", 20, 1, 100);
                  long nowMicros = System.currentTimeMillis() * 1000;
                  long startMicros = nowMicros - (minutes * 60L * 1_000_000L);

                  StringBuilder sql = new StringBuilder("SELECT trace_id, service_name, span_name, duration, error_message FROM \"traces\" WHERE (status_code = 2 OR error = true)");
                  if (!Texts.isBlank(service)) {
                    sql.append(" AND service_name = '").append(service.replace("'", "''")).append("'");
                  }
                  sql.append(" ORDER BY start_time DESC");

                  var rows = openobserve.search(sql.toString(), startMicros, nowMicros, limit);
                  if (rows.isEmpty()) {
                    return "No recent error traces found.";
                  }
                  StringBuilder sb = new StringBuilder("Found " + rows.size() + " error trace(s):\n");
                  rows.forEach(r -> sb.append(r.toString()).append('\n'));
                  return sb.toString();
                } catch (Exception ex) {
                  return "Failed to search traces: " + ex.getMessage();
                }
              }
              return "OpenObserve traces not configured.";
            }));
  }

  @Override
  public ToolsetHealth health() {
    if (openobserve.isConfigured()) {
      return ToolsetHealth.ok("OpenTelemetry via OpenObserve");
    }
    if (!Texts.isBlank(config.getTracesUrl()) || !Texts.isBlank(config.getUrl())) {
      return ToolsetHealth.ok("OpenTelemetry traces configured");
    }
    return ToolsetHealth.failed("No trace backend configured");
  }
}

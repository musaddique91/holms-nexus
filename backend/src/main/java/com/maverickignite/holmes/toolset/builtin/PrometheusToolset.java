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
 * Prometheus & Alertmanager: PromQL queries, time-series metrics, scrape targets and active alerts.
 */
@Component
class PrometheusToolset implements Toolset {

  private final HolmesProperties.Prometheus config;
  private final RestClient http;
  private final RestClient alertmanagerHttp;

  PrometheusToolset(HolmesProperties properties, RestClient.Builder builder) {
    this.config = properties.getToolsets().getPrometheus();
    RestClient.Builder b = builder.clone().requestFactory(HttpClients.standard());
    if (!Texts.isBlank(config.getUrl())) {
      b.baseUrl(config.getUrl());
    }
    if (!Texts.isBlank(config.getBearerToken())) {
      b.defaultHeader("Authorization", "Bearer " + config.getBearerToken());
    }
    this.http = b.build();

    if (!Texts.isBlank(config.getAlertmanagerUrl())) {
      RestClient.Builder amb =
          builder.clone().requestFactory(HttpClients.standard()).baseUrl(config.getAlertmanagerUrl());
      if (!Texts.isBlank(config.getBearerToken())) {
        amb.defaultHeader("Authorization", "Bearer " + config.getBearerToken());
      }
      this.alertmanagerHttp = amb.build();
    } else {
      this.alertmanagerHttp = null;
    }
  }

  @Override
  public String name() {
    return "prometheus";
  }

  @Override
  public String description() {
    return "Prometheus time-series metrics, PromQL queries, targets and active alerts.";
  }

  @Override
  public boolean enabled() {
    return config.isEnabled() && !Texts.isBlank(config.getUrl());
  }

  @Override
  public List<ToolSpec> tools() {
    return List.of(
        new ToolSpec(
            "prometheus_instant_query",
            "Instant PromQL query against Prometheus at a point in time, e.g."
                + " sum(rate(http_requests_total{status=~\"5..\"}[5m])) or"
                + " node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes * 100.",
            Schemas.object()
                .string("query", "PromQL expression to evaluate", true)
                .string("time", "Evaluation timestamp (RFC3339 or Unix seconds, optional)", false)
                .build(),
            args -> {
              String q = Args.required(args, "query");
              String time = Args.optional(args, "time");
              if (Texts.isBlank(time)) {
                return http.get()
                    .uri("/api/v1/query?query={q}", q)
                    .retrieve()
                    .body(String.class);
              } else {
                return http.get()
                    .uri("/api/v1/query?query={q}&time={time}", q, time)
                    .retrieve()
                    .body(String.class);
              }
            }),
        new ToolSpec(
            "prometheus_range_query",
            "Range PromQL query evaluated over a time window, returning a matrix of metric values over time.",
            Schemas.object()
                .string("query", "PromQL expression to evaluate", true)
                .string(
                    "start",
                    "Start time (e.g. '30m' ago, RFC3339 timestamp, or Unix seconds; default: 30m ago)",
                    false)
                .string("end", "End time (RFC3339, Unix seconds, or omit for now)", false)
                .string("step", "Query resolution step interval (e.g. '15s', '1m', '5m'; default: '1m')", false)
                .build(),
            args -> {
              String q = Args.required(args, "query");
              String start = Texts.firstNonBlank(Args.optional(args, "start"), resolveOffset("30m"));
              String end = Texts.firstNonBlank(Args.optional(args, "end"), Instant.now().toString());
              String step = Texts.firstNonBlank(Args.optional(args, "step"), "1m");
              return http.get()
                  .uri(
                      "/api/v1/query_range?query={q}&start={start}&end={end}&step={step}",
                      q,
                      start,
                      end,
                      step)
                  .retrieve()
                  .body(String.class);
            }),
        new ToolSpec(
            "prometheus_active_alerts",
            "List active alerts currently firing in Prometheus or Alertmanager.",
            Schemas.empty(),
            args -> {
              if (alertmanagerHttp != null) {
                try {
                  return alertmanagerHttp
                      .get()
                      .uri("/api/v2/alerts?active=true&silenced=false")
                      .retrieve()
                      .body(String.class);
                } catch (Exception ignored) {
                  // Fall back to Prometheus alerts endpoint
                }
              }
              return http.get().uri("/api/v1/alerts").retrieve().body(String.class);
            }),
        new ToolSpec(
            "prometheus_targets",
            "List active Prometheus scrape targets and their health status (UP, DOWN, last scrape error).",
            Schemas.empty(),
            args -> http.get().uri("/api/v1/targets?state=active").retrieve().body(String.class)));
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
      JsonNode info = http.get().uri("/api/v1/status/buildinfo").retrieve().body(JsonNode.class);
      String version = info != null ? info.path("data").path("version").asString(null) : null;
      return ToolsetHealth.ok("Prometheus " + (version != null ? version : "healthy"));
    } catch (Exception ex) {
      try {
        http.get().uri("/-/healthy").retrieve().toBodilessEntity();
        return ToolsetHealth.ok("Prometheus healthy");
      } catch (Exception e) {
        return ToolsetHealth.failed("Prometheus unreachable: " + ex.getMessage());
      }
    }
  }
}

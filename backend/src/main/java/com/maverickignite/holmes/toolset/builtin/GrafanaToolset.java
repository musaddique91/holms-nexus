package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Grafana: dashboards, firing alerts and PromQL through Grafana's datasource proxy. */
@Component
class GrafanaToolset implements Toolset {

  private final HolmesProperties.Grafana config;
  private final RestClient http;

  GrafanaToolset(HolmesProperties properties, RestClient.Builder builder) {
    this.config = properties.getToolsets().getGrafana();
    RestClient.Builder b = builder.clone().requestFactory(HttpClients.standard());
    if (!Texts.isBlank(config.getUrl())) {
      b.baseUrl(config.getUrl());
    }
    if (!Texts.isBlank(config.getApiKey())) {
      b.defaultHeader("Authorization", "Bearer " + config.getApiKey());
    }
    this.http = b.build();
  }

  @Override
  public String name() {
    return "grafana";
  }

  @Override
  public String description() {
    return "Grafana dashboards, alerts and Prometheus metrics.";
  }

  @Override
  public boolean enabled() {
    return config.isEnabled() && !Texts.isBlank(config.getUrl());
  }

  @Override
  public List<ToolSpec> tools() {
    return List.of(
        new ToolSpec(
            "grafana_firing_alerts",
            "Alerts currently firing in Grafana Alerting.",
            Schemas.empty(),
            args -> get("/api/alertmanager/grafana/api/v2/alerts?active=true&silenced=false")),
        new ToolSpec(
            "grafana_datasources",
            "List Grafana datasources with uid and type (find the Prometheus uid here).",
            Schemas.empty(),
            args -> {
              StringBuilder sb = new StringBuilder();
              for (JsonNode ds : http.get().uri("/api/datasources").retrieve().body(JsonNode.class)) {
                sb.append(ds.path("uid").asString()).append('\t')
                    .append(ds.path("type").asString()).append('\t')
                    .append(ds.path("name").asString()).append('\n');
              }
              return sb.toString();
            }),
        new ToolSpec(
            "grafana_prometheus_query",
            "Instant PromQL query through a Grafana Prometheus datasource, e.g."
                + " rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]).",
            Schemas.object()
                .string("datasource_uid", "Prometheus datasource uid", true)
                .string("promql", "PromQL expression", true)
                .build(),
            args ->
                http.get()
                    .uri(
                        "/api/datasources/proxy/uid/{uid}/api/v1/query?query={q}",
                        Args.required(args, "datasource_uid"),
                        Args.required(args, "promql"))
                    .retrieve()
                    .body(String.class)),
        new ToolSpec(
            "grafana_search_dashboards",
            "Search dashboards by title.",
            Schemas.object().string("query", "Title text", false).build(),
            args ->
                http.get()
                    .uri("/api/search?type=dash-db&query={q}", Texts.firstNonBlank(Args.optional(args, "query"), ""))
                    .retrieve()
                    .body(String.class)));
  }

  private String get(String path) {
    return http.get().uri(path).retrieve().body(String.class);
  }

  @Override
  public ToolsetHealth health() {
    JsonNode h = http.get().uri("/api/health").retrieve().body(JsonNode.class);
    return ToolsetHealth.ok("Grafana " + h.path("version").asString());
  }
}

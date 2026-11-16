package com.maverickignite.holmes.openobserve;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Thin client for OpenObserve's SQL search API. */
@Component
public class OpenObserveClient {

  private final HolmesProperties.OpenObserve config;
  private final RestClient http;

  public OpenObserveClient(HolmesProperties properties, RestClient.Builder builder) {
    this.config = properties.getOpenobserve();
    RestClient.Builder b =
        builder.clone().baseUrl(config.getUrl()).requestFactory(HttpClients.standard());
    if (isConfigured()) {
      b.defaultHeaders(h -> h.setBasicAuth(config.getUsername(), config.getPassword()));
    }
    this.http = b.build();
  }

  public boolean isConfigured() {
    return !Texts.isBlank(config.getUsername()) && !Texts.isBlank(config.getPassword());
  }

  public String organization() {
    return config.getOrganization();
  }

  /**
   * Runs a SQL query over [startMicros, endMicros]. OpenObserve timestamps are microseconds since
   * the epoch, and both bounds are mandatory.
   */
  public List<JsonNode> search(String sql, long startMicros, long endMicros, int size) {
    Map<String, Object> body =
        Map.of(
            "query",
            Map.of(
                "sql", sql,
                "start_time", startMicros,
                "end_time", endMicros,
                "from", 0,
                "size", size));
    JsonNode response =
        http.post()
            .uri("/api/{org}/_search?type=logs", config.getOrganization())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);
    List<JsonNode> hits = new ArrayList<>();
    if (response != null && response.path("hits").isArray()) {
      response.path("hits").forEach(hits::add);
    }
    return hits;
  }

  /** Lists streams with their document counts; used by the toolset and the health check. */
  public JsonNode streams() {
    return http.get()
        .uri("/api/{org}/streams?type=logs", config.getOrganization())
        .retrieve()
        .body(JsonNode.class);
  }
}

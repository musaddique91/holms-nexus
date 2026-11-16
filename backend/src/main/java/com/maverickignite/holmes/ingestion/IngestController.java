package com.maverickignite.holmes.ingestion;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Push ingestion: OpenObserve alert destinations (webhook) and any app that wants to report errors
 * directly. Authenticated with a shared token in the {@code X-Holmes-Token} header (never a query
 * parameter, which would end up in access logs).
 */
@RestController
@RequestMapping("/api/ingest")
class IngestController {

  private static final int MAX_EVENTS_PER_REQUEST = 500;

  private final ErrorEventMapper mapper;
  private final ErrorEventSink sink;
  private final String token;

  IngestController(ErrorEventMapper mapper, ErrorEventSink sink, HolmesProperties properties) {
    this.mapper = mapper;
    this.sink = sink;
    this.token = properties.getIngestion().getWebhookToken();
  }

  @PostMapping({"/openobserve", "/events"})
  ResponseEntity<Map<String, Object>> ingest(
      @RequestBody JsonNode body,
      @RequestHeader(value = "X-Holmes-Token", required = false) String headerToken) {
    if (!authorized(headerToken, null, false)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    List<ErrorEvent> events = mapper.events(body, "webhook");
    if (events.size() > MAX_EVENTS_PER_REQUEST) {
      return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
          .body(Map.of("detail", "At most " + MAX_EVENTS_PER_REQUEST + " events per request"));
    }
    int published = 0;
    try {
      for (ErrorEvent event : events) {
        sink.publish(event);
        published++;
      }
    } catch (IllegalStateException e) {
      // Delivery is at-least-once: the sender retries and already-published events are
      // counted again, which only inflates an occurrence count.
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("accepted", published, "detail", "Event pipeline unavailable"));
    }
    return ResponseEntity.accepted().body(Map.of("accepted", published));
  }

  @PostMapping("/alertmanager")
  ResponseEntity<Map<String, Object>> ingestAlertmanager(
      @RequestBody JsonNode body,
      @RequestHeader(value = "X-Holmes-Token", required = false) String headerToken,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    if (!authorized(headerToken, authHeader, true)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    List<ErrorEvent> events = mapper.fromAlertmanager(body);
    if (events.isEmpty()) {
      return ResponseEntity.ok(Map.of("accepted", 0, "status", "ignored_or_resolved"));
    }
    return publishEvents(events, "alerts");
  }

  @PostMapping({"/otlp/v1/traces", "/v1/traces"})
  ResponseEntity<Map<String, Object>> ingestOtlpTraces(
      @RequestBody JsonNode body,
      @RequestHeader(value = "X-Holmes-Token", required = false) String headerToken,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    if (!authorized(headerToken, authHeader, true)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    List<ErrorEvent> events = mapper.fromOtlpTraces(body);
    if (events.isEmpty()) {
      return ResponseEntity.ok(Map.of("accepted", 0, "status", "no_errors"));
    }
    return publishEvents(events, "spans");
  }

  @PostMapping({"/otlp/v1/logs", "/v1/logs"})
  ResponseEntity<Map<String, Object>> ingestOtlpLogs(
      @RequestBody JsonNode body,
      @RequestHeader(value = "X-Holmes-Token", required = false) String headerToken,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    if (!authorized(headerToken, authHeader, true)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    List<ErrorEvent> events = mapper.fromOtlpLogs(body);
    if (events.isEmpty()) {
      return ResponseEntity.ok(Map.of("accepted", 0, "status", "no_errors"));
    }
    return publishEvents(events, "logs");
  }

  private ResponseEntity<Map<String, Object>> publishEvents(List<ErrorEvent> events, String countKey) {
    if (events.size() > MAX_EVENTS_PER_REQUEST) {
      return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
          .body(Map.of("detail", "At most " + MAX_EVENTS_PER_REQUEST + " events per request"));
    }
    int published = 0;
    try {
      for (ErrorEvent event : events) {
        sink.publish(event);
        published++;
      }
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("accepted", published, "detail", "Event pipeline unavailable"));
    }
    return ResponseEntity.accepted().body(Map.of("accepted", published, countKey, events.size()));
  }

  private boolean authorized(String presented, String authHeader, boolean allowIfNoToken) {
    if (Texts.isBlank(token)) {
      return allowIfNoToken;
    }
    if (presented != null
        && MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))) {
      return true;
    }
    if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
      String bearer = authHeader.substring(7).trim();
      return MessageDigest.isEqual(
          token.getBytes(StandardCharsets.UTF_8), bearer.getBytes(StandardCharsets.UTF_8));
    }
    return false;
  }
}

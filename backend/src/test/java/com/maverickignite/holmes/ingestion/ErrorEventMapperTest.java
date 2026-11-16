package com.maverickignite.holmes.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ErrorEventMapperTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final ErrorEventMapper mapper = new ErrorEventMapper();

  @Test
  void mapsSpringLogstashStructuredLog() {
    String line =
        """
        {"@timestamp":"2026-11-05T10:15:30.123Z","level":"ERROR","logger_name":"c.m.OrderService",
         "message":"Order failed","stack_trace":"java.lang.IllegalStateException: boom\\n\\tat a.B.c(B.java:1)",
         "service":"orders-backend","host":"vm-1"}
        """;
    ErrorEvent e = mapper.events(json.readTree(line), "kafka").getFirst();
    assertThat(e.service()).isEqualTo("orders-backend");
    assertThat(e.level()).isEqualTo("ERROR");
    assertThat(e.stackTrace()).startsWith("java.lang.IllegalStateException");
    assertThat(e.timestamp()).isEqualTo(Instant.parse("2026-11-05T10:15:30.123Z"));
  }

  @Test
  void splitsPlainTextStackTraceAndInfersLevel() {
    String row =
        """
        {"container_name":"crm-backend","_timestamp":1762337730123456,
         "log":"2026-11-05 10:15:30 ERROR 1 --- [exec-1] c.m.Lead : Save failed\\njava.lang.NullPointerException: x\\n\\tat c.m.Lead.save(Lead.java:9)"}
        """;
    ErrorEvent e = mapper.events(json.readTree(row), "poller").getFirst();
    assertThat(e.service()).isEqualTo("crm-backend");
    assertThat(e.level()).isEqualTo("ERROR");
    assertThat(e.message()).isEqualTo("Save failed");
    assertThat(e.stackTrace()).startsWith("java.lang.NullPointerException");
    assertThat(e.timestamp().getEpochSecond()).isEqualTo(1762337730L);
  }

  @Test
  void expandsOpenObserveAlertEnvelope() {
    String payload =
        """
        {"alert":"errors","rows":[{"service":"a","level":"ERROR","message":"x"},
                                  {"service":"b","level":"ERROR","message":"y"}]}
        """;
    List<ErrorEvent> events = mapper.events(json.readTree(payload), "webhook");
    assertThat(events).extracting(ErrorEvent::service).containsExactly("a", "b");
  }

  @Test
  void mapsAlertmanagerWebhookPayload() {
    String payload =
        """
        {
          "receiver": "holms-nexus",
          "status": "firing",
          "alerts": [
            {
              "status": "firing",
              "labels": {
                "alertname": "KubePodCrashLooping",
                "severity": "critical",
                "service": "orders-backend",
                "namespace": "production",
                "pod": "orders-backend-7f8d9-abc",
                "instance": "node-1"
              },
              "annotations": {
                "summary": "Pod orders-backend is crash looping",
                "description": "Container restarted 5 times in 5m",
                "runbook_url": "https://wiki.internal/runbooks/crashloop"
              },
              "startsAt": "2026-11-11T12:00:00Z",
              "generatorURL": "http://prometheus:9090/graph?g0.expr=..."
            },
            {
              "status": "resolved",
              "labels": {
                "alertname": "KubeMemoryPressure",
                "service": "infra"
              }
            }
          ]
        }
        """;
    List<ErrorEvent> events = mapper.fromAlertmanager(json.readTree(payload));
    assertThat(events).hasSize(1);

    ErrorEvent e = events.getFirst();
    assertThat(e.service()).isEqualTo("orders-backend");
    assertThat(e.environment()).isEqualTo("production");
    assertThat(e.level()).isEqualTo("CRITICAL");
    assertThat(e.message()).isEqualTo("[KubePodCrashLooping] Pod orders-backend is crash looping");
    assertThat(e.host()).isEqualTo("node-1");
    assertThat(e.source()).isEqualTo("alertmanager");
    assertThat(e.logger()).isEqualTo("KubePodCrashLooping");
    assertThat(e.timestamp()).isEqualTo(Instant.parse("2026-11-11T12:00:00Z"));
    assertThat(e.stackTrace()).contains("Container restarted 5 times in 5m");
    assertThat(e.stackTrace()).contains("Runbook: https://wiki.internal/runbooks/crashloop");
  }

  @Test
  void mapsOtlpTracePayload() {
    String payload =
        """
        {
          "resourceSpans": [
            {
              "resource": {
                "attributes": [
                  {"key": "service.name", "value": {"stringValue": "orders-api"}},
                  {"key": "deployment.environment", "value": {"stringValue": "production"}},
                  {"key": "host.name", "value": {"stringValue": "worker-1"}}
                ]
              },
              "scopeSpans": [
                {
                  "spans": [
                    {
                      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                      "spanId": "00f067aa0ba902b7",
                      "name": "POST /api/orders",
                      "status": {"code": 2, "message": "Failed to create order"},
                      "events": [
                        {
                          "name": "exception",
                          "attributes": [
                            {"key": "exception.type", "value": {"stringValue": "java.lang.NullPointerException"}},
                            {"key": "exception.message", "value": {"stringValue": "Customer ID required"}}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
    List<ErrorEvent> events = mapper.fromOtlpTraces(json.readTree(payload));
    assertThat(events).hasSize(1);
    ErrorEvent e = events.getFirst();
    assertThat(e.service()).isEqualTo("orders-api");
    assertThat(e.environment()).isEqualTo("production");
    assertThat(e.level()).isEqualTo("ERROR");
    assertThat(e.message()).isEqualTo("java.lang.NullPointerException: Customer ID required");
    assertThat(e.source()).isEqualTo("otlp");
  }

  @Test
  void mapsOtlpLogPayload() {
    String payload =
        """
        {
          "resourceLogs": [
            {
              "resource": {
                "attributes": [
                  {"key": "service.name", "value": {"stringValue": "inventory-backend"}}
                ]
              },
              "scopeLogs": [
                {
                  "logRecords": [
                    {
                      "severityNumber": 17,
                      "severityText": "ERROR",
                      "body": {"stringValue": "Database connection timeout"},
                      "traceId": "1234567890abcdef1234567890abcdef"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
    List<ErrorEvent> events = mapper.fromOtlpLogs(json.readTree(payload));
    assertThat(events).hasSize(1);
    ErrorEvent e = events.getFirst();
    assertThat(e.service()).isEqualTo("inventory-backend");
    assertThat(e.level()).isEqualTo("ERROR");
    assertThat(e.message()).isEqualTo("Database connection timeout");
    assertThat(e.source()).isEqualTo("otlp");
  }
}

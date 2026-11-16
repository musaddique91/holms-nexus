package com.maverickignite.holmes.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.maverickignite.holmes.config.HolmesProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

class IngestControllerTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final ErrorEventMapper mapper = new ErrorEventMapper();
  private final ErrorEventSink sink = mock(ErrorEventSink.class);

  @Test
  void ingestsAlertmanagerPayloadWithoutAuthWhenNoTokenConfigured() {
    HolmesProperties props = new HolmesProperties();
    IngestController controller = new IngestController(mapper, sink, props);

    String payload =
        """
        {
          "receiver": "holms-nexus",
          "status": "firing",
          "alerts": [
            {
              "status": "firing",
              "labels": {
                "alertname": "KubeMemoryPressure",
                "service": "orders-api",
                "severity": "critical"
              },
              "annotations": {
                "summary": "Node memory pressure detected"
              }
            }
          ]
        }
        """;

    ResponseEntity<Map<String, Object>> response =
        controller.ingestAlertmanager(json.readTree(payload), null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).containsEntry("accepted", 1);
    assertThat(response.getBody()).containsEntry("alerts", 1);
    verify(sink, times(1)).publish(any(ErrorEvent.class));
  }

  @Test
  void rejectsAlertmanagerWhenTokenConfiguredAndHeaderMissing() {
    HolmesProperties props = new HolmesProperties();
    props.getIngestion().setWebhookToken("secret-token-123");
    IngestController controller = new IngestController(mapper, sink, props);

    String payload =
        """
        {"receiver":"holms","status":"firing","alerts":[{"status":"firing","labels":{"alertname":"A"}}]}
        """;

    ResponseEntity<Map<String, Object>> response =
        controller.ingestAlertmanager(json.readTree(payload), null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void acceptsAlertmanagerWithBearerAuth() {
    HolmesProperties props = new HolmesProperties();
    props.getIngestion().setWebhookToken("secret-token-123");
    IngestController controller = new IngestController(mapper, sink, props);

    String payload =
        """
        {"receiver":"holms","status":"firing","alerts":[{"status":"firing","labels":{"alertname":"KubeCrash"}}]}
        """;

    ResponseEntity<Map<String, Object>> response =
        controller.ingestAlertmanager(
            json.readTree(payload), null, "Bearer secret-token-123");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).containsEntry("accepted", 1);
  }

  @Test
  void acceptsOtlpTraces() {
    HolmesProperties props = new HolmesProperties();
    IngestController controller = new IngestController(mapper, sink, props);

    String payload =
        """
        {"resourceSpans":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"orders-backend"}}]},"scopeSpans":[{"spans":[{"name":"GET /api","status":{"code":2,"message":"Failed"}}]}]}]}
        """;

    ResponseEntity<Map<String, Object>> response =
        controller.ingestOtlpTraces(json.readTree(payload), null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).containsEntry("accepted", 1);
  }

  @Test
  void acceptsOtlpLogs() {
    HolmesProperties props = new HolmesProperties();
    IngestController controller = new IngestController(mapper, sink, props);

    String payload =
        """
        {"resourceLogs":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"billing-backend"}}]},"scopeLogs":[{"logRecords":[{"severityNumber":17,"body":{"stringValue":"Out of memory"}}]}]}]}
        """;

    ResponseEntity<Map<String, Object>> response =
        controller.ingestOtlpLogs(json.readTree(payload), null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).containsEntry("accepted", 1);
  }
}

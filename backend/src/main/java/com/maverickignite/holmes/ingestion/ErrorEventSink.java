package com.maverickignite.holmes.ingestion;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Entry point for events Holmes collects itself (poller, webhook). With Kafka on, they go through
 * the topic, which buffers bursts and lets other producers feed the same pipeline; with Kafka off
 * they are ingested in-process.
 */
@Component
public class ErrorEventSink {

  private final HolmesProperties.Kafka kafka;
  private final ObjectProvider<KafkaTemplate<String, String>> template;
  private final IngestionService ingestion;
  private final ObjectMapper json;

  public ErrorEventSink(
      HolmesProperties properties,
      ObjectProvider<KafkaTemplate<String, String>> template,
      IngestionService ingestion,
      ObjectMapper json) {
    this.kafka = properties.getIngestion().getKafka();
    this.template = template;
    this.ingestion = ingestion;
    this.json = json;
  }

  /** Blocks until Kafka acknowledges, so callers only report success for stored events. */
  public void publish(ErrorEvent event) {
    if (kafka.isEnabled()) {
      String key = Texts.firstNonBlank(event.service(), "unknown");
      try {
        template
            .getObject()
            .send(kafka.getTopic(), key, json.writeValueAsString(event))
            .get(10, java.util.concurrent.TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while publishing to Kafka", e);
      } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
        throw new IllegalStateException("Kafka publish failed: " + e.getMessage(), e);
      }
    } else {
      ingestion.ingest(event);
    }
  }
}

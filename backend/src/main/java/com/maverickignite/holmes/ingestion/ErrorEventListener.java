package com.maverickignite.holmes.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes error events from Kafka. Payloads may be a single event, an array, or an envelope with a
 * {@code rows} array. The consumer only records occurrences; investigations are driven separately
 * from the database, so a slow LLM never causes consumer lag or rebalances.
 */
@Component
@RequiredArgsConstructor
class ErrorEventListener {

  private final ObjectMapper json;
  private final ErrorEventMapper mapper;
  private final IngestionService ingestion;

  @KafkaListener(
      id = "holmes-errors",
      topics = "${holmes.ingestion.kafka.topic}",
      groupId = "${holmes.ingestion.kafka.group-id}",
      autoStartup = "${holmes.ingestion.kafka.enabled}")
  void onMessage(String payload) {
    JsonNode node = json.readTree(payload);
    for (ErrorEvent event : mapper.events(node, "kafka")) {
      ingestion.ingest(event);
    }
  }
}

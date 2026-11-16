package com.maverickignite.holmes.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Malformed payloads go straight to {@code <topic>.DLT}; transient failures (database down) are
 * retried a few times first, then dead-lettered so one bad record never blocks a partition.
 */
@Configuration
@ConditionalOnProperty(
    name = "holmes.ingestion.kafka.enabled",
    havingValue = "true",
    matchIfMissing = true)
class KafkaConfig {

  @Bean
  NewTopic errorsTopic(HolmesProperties properties) {
    HolmesProperties.Kafka kafka = properties.getIngestion().getKafka();
    return TopicBuilder.name(kafka.getTopic())
        .partitions(kafka.getPartitions())
        .replicas(kafka.getReplicas())
        .build();
  }

  @Bean
  NewTopic errorsDeadLetterTopic(HolmesProperties properties) {
    HolmesProperties.Kafka kafka = properties.getIngestion().getKafka();
    return TopicBuilder.name(kafka.getTopic() + ".DLT")
        .partitions(kafka.getPartitions())
        .replicas(kafka.getReplicas())
        .build();
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3));
    handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
    return handler;
  }
}

package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Kafka cluster health: topics, partitions/ISR and consumer-group lag. */
@Slf4j
@Component
class KafkaToolset implements Toolset, DisposableBean {

  private static final long TIMEOUT_S = 15;

  private final Map<String, HolmesProperties.KafkaCluster> clusters;
  private final Map<String, AdminClient> admins = new ConcurrentHashMap<>();

  KafkaToolset(HolmesProperties properties) {
    this.clusters =
        properties.getToolsets().getKafka().stream()
            .collect(Collectors.toMap(HolmesProperties.KafkaCluster::getName, c -> c));
  }

  @Override
  public String name() {
    return "kafka";
  }

  @Override
  public String description() {
    return "Kafka topics, partition health and consumer lag.";
  }

  @Override
  public boolean enabled() {
    return !clusters.isEmpty();
  }

  @Override
  public List<ToolSpec> tools() {
    String[] names = clusters.keySet().toArray(String[]::new);
    return List.of(
        new ToolSpec(
            "kafka_topics",
            "List topics in a Kafka cluster.",
            Schemas.object().enumeration("cluster", "Cluster name", true, names).build(),
            args ->
                String.join(
                    "\n",
                    new java.util.TreeSet<>(
                        admin(args).listTopics().names().get(TIMEOUT_S, TimeUnit.SECONDS)))),
        new ToolSpec(
            "kafka_describe_topic",
            "Partitions of a topic with leader, replicas and in-sync replicas"
                + " (under-replicated partitions point at broker trouble).",
            Schemas.object()
                .enumeration("cluster", "Cluster name", true, names)
                .string("topic", "Topic name", true)
                .build(),
            args -> {
              String topic = Args.required(args, "topic");
              TopicDescription d =
                  admin(args)
                      .describeTopics(List.of(topic))
                      .allTopicNames()
                      .get(TIMEOUT_S, TimeUnit.SECONDS)
                      .get(topic);
              StringBuilder sb = new StringBuilder(topic + "\n");
              d.partitions()
                  .forEach(
                      p ->
                          sb.append("partition ").append(p.partition())
                              .append(" leader=").append(p.leader() == null ? "NONE" : p.leader().id())
                              .append(" replicas=").append(p.replicas().size())
                              .append(" isr=").append(p.isr().size()).append('\n'));
              return sb.toString();
            }),
        new ToolSpec(
            "kafka_consumer_groups",
            "List consumer groups and their state.",
            Schemas.object().enumeration("cluster", "Cluster name", true, names).build(),
            args ->
                admin(args).listConsumerGroups().all().get(TIMEOUT_S, TimeUnit.SECONDS).stream()
                    .map(ConsumerGroupListing::groupId)
                    .sorted()
                    .collect(Collectors.joining("\n"))),
        new ToolSpec(
            "kafka_consumer_lag",
            "Per-partition lag of a consumer group (end offset minus committed offset). Growing"
                + " lag means consumers are stuck or too slow.",
            Schemas.object()
                .enumeration("cluster", "Cluster name", true, names)
                .string("group", "Consumer group id", true)
                .build(),
            this::lag));
  }

  private String lag(JsonNode args) throws Exception {
    AdminClient admin = admin(args);
    String group = Args.required(args, "group");
    Map<TopicPartition, OffsetAndMetadata> committed =
        admin
            .listConsumerGroupOffsets(group)
            .partitionsToOffsetAndMetadata()
            .get(TIMEOUT_S, TimeUnit.SECONDS);
    if (committed.isEmpty()) {
      return "Group " + group + " has no committed offsets.";
    }
    Map<TopicPartition, OffsetSpec> request =
        committed.keySet().stream().collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
        admin.listOffsets(request).all().get(TIMEOUT_S, TimeUnit.SECONDS);
    StringBuilder sb = new StringBuilder();
    long total = 0;
    for (var e : new TreeMap<>(committed.entrySet().stream()
        .collect(Collectors.toMap(x -> x.getKey().toString(), x -> x))).values()) {
      long end = ends.get(e.getKey()).offset();
      long lag = Math.max(0, end - e.getValue().offset());
      total += lag;
      sb.append(e.getKey()).append(" committed=").append(e.getValue().offset())
          .append(" end=").append(end).append(" lag=").append(lag).append('\n');
    }
    return "total lag=" + total + "\n" + sb;
  }

  private AdminClient admin(JsonNode args) {
    String name = Args.required(args, "cluster");
    HolmesProperties.KafkaCluster cluster = clusters.get(name);
    if (cluster == null) {
      throw new ToolException("Unknown cluster '" + name + "'. Known: " + clusters.keySet());
    }
    return admins.computeIfAbsent(
        name,
        n -> {
          Properties p = new Properties();
          p.putAll(cluster.getProperties());
          p.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
          p.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
          p.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 15_000);
          return AdminClient.create(p);
        });
  }

  @Override
  public ToolsetHealth health() {
    StringBuilder sb = new StringBuilder();
    boolean ok = true;
    for (String name : clusters.keySet()) {
      try {
        int brokers =
            admin(Schemas.args("cluster", name))
                .describeCluster()
                .nodes()
                .get(TIMEOUT_S, TimeUnit.SECONDS)
                .size();
        sb.append(name).append(": ").append(brokers).append(" brokers. ");
      } catch (Exception e) {
        ok = false;
        sb.append(name).append(": ").append(e.getMessage()).append(". ");
      }
    }
    return new ToolsetHealth(ok, sb.toString().strip());
  }

  @Override
  public void destroy() {
    admins.values().forEach(AdminClient::close);
  }
}

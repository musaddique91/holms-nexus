package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** RabbitMQ via the management HTTP API: backlog, consumers, node alarms. */
@Component
class RabbitMqToolset implements Toolset {

  private final Map<String, RestClient> brokers;

  RabbitMqToolset(HolmesProperties properties, RestClient.Builder builder) {
    this.brokers =
        properties.getToolsets().getRabbitmq().stream()
            .collect(
                Collectors.toMap(
                    HolmesProperties.RabbitMq::getName,
                    r ->
                        builder
                            .clone()
                            .baseUrl(r.getUrl())
                            .requestFactory(HttpClients.standard())
                            .defaultHeaders(h -> h.setBasicAuth(r.getUsername(), r.getPassword()))
                            .build()));
  }

  @Override
  public String name() {
    return "rabbitmq";
  }

  @Override
  public String description() {
    return "RabbitMQ queues, consumers and broker alarms.";
  }

  @Override
  public boolean enabled() {
    return !brokers.isEmpty();
  }

  @Override
  public List<ToolSpec> tools() {
    String[] names = brokers.keySet().toArray(String[]::new);
    return List.of(
        new ToolSpec(
            "rabbitmq_queues",
            "Queues sorted by backlog with ready/unacked counts and consumer count. A growing"
                + " backlog with 0 consumers means the consumer service is down.",
            Schemas.object().enumeration("broker", "Broker name", true, names).build(),
            args -> {
              JsonNode queues =
                  broker(args)
                      .get()
                      .uri("/api/queues?columns=name,vhost,messages,messages_ready,messages_unacknowledged,consumers,state")
                      .retrieve()
                      .body(JsonNode.class);
              List<JsonNode> list = new ArrayList<>();
              queues.forEach(list::add);
              list.sort(Comparator.comparingLong((JsonNode q) -> q.path("messages").asLong()).reversed());
              StringBuilder sb = new StringBuilder("vhost\tqueue\tmessages\tready\tunacked\tconsumers\tstate\n");
              list.stream()
                  .limit(30)
                  .forEach(
                      q ->
                          sb.append(q.path("vhost").asString()).append('\t')
                              .append(q.path("name").asString()).append('\t')
                              .append(q.path("messages").asLong()).append('\t')
                              .append(q.path("messages_ready").asLong()).append('\t')
                              .append(q.path("messages_unacknowledged").asLong()).append('\t')
                              .append(q.path("consumers").asLong()).append('\t')
                              .append(q.path("state").asString()).append('\n'));
              return sb.toString();
            }),
        new ToolSpec(
            "rabbitmq_nodes",
            "Broker nodes with memory/disk alarms, file descriptors and running state.",
            Schemas.object().enumeration("broker", "Broker name", true, names).build(),
            args -> {
              StringBuilder sb = new StringBuilder();
              for (JsonNode n :
                  broker(args).get().uri("/api/nodes").retrieve().body(JsonNode.class)) {
                sb.append(n.path("name").asString())
                    .append(" running=").append(n.path("running").asBoolean())
                    .append(" mem_alarm=").append(n.path("mem_alarm").asBoolean())
                    .append(" disk_alarm=").append(n.path("disk_free_alarm").asBoolean())
                    .append(" mem_used=").append(n.path("mem_used").asLong() / 1_048_576).append("MB")
                    .append(" fd_used=").append(n.path("fd_used").asLong()).append('\n');
              }
              return sb.toString();
            }),
        new ToolSpec(
            "rabbitmq_overview",
            "Cluster-wide message rates and totals.",
            Schemas.object().enumeration("broker", "Broker name", true, names).build(),
            args -> {
              JsonNode o = broker(args).get().uri("/api/overview").retrieve().body(JsonNode.class);
              return "version=" + o.path("rabbitmq_version").asString()
                  + " queue_totals=" + o.path("queue_totals")
                  + " message_stats=" + o.path("message_stats")
                  + " object_totals=" + o.path("object_totals");
            }));
  }

  private RestClient broker(JsonNode args) {
    String name = Args.required(args, "broker");
    RestClient c = brokers.get(name);
    if (c == null) {
      throw new ToolException("Unknown broker '" + name + "'. Known: " + brokers.keySet());
    }
    return c;
  }

  @Override
  public ToolsetHealth health() {
    StringBuilder sb = new StringBuilder();
    boolean ok = true;
    for (var e : brokers.entrySet()) {
      try {
        JsonNode o = e.getValue().get().uri("/api/overview").retrieve().body(JsonNode.class);
        sb.append(e.getKey()).append(": RabbitMQ ").append(o.path("rabbitmq_version").asString()).append(". ");
      } catch (RuntimeException ex) {
        ok = false;
        sb.append(e.getKey()).append(": ").append(ex.getMessage()).append(". ");
      }
    }
    return new ToolsetHealth(ok, sb.toString().strip());
  }
}

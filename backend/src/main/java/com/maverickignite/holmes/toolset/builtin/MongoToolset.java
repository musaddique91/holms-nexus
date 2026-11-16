package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** MongoDB diagnostics: server status, slow operations, collections, indexes, sample documents. */
@Component
class MongoToolset implements Toolset, DisposableBean {

  private final Map<String, HolmesProperties.Mongo> instances;
  private final Map<String, MongoClient> clients = new ConcurrentHashMap<>();

  MongoToolset(HolmesProperties properties) {
    this.instances =
        properties.getToolsets().getMongo().stream()
            .collect(Collectors.toMap(HolmesProperties.Mongo::getName, m -> m));
  }

  @Override
  public String name() {
    return "mongodb";
  }

  @Override
  public String description() {
    return "MongoDB server health, slow operations and collection inspection.";
  }

  @Override
  public boolean enabled() {
    return !instances.isEmpty();
  }

  @Override
  public List<ToolSpec> tools() {
    String[] names = instances.keySet().toArray(String[]::new);
    return List.of(
        new ToolSpec(
            "mongo_server_status",
            "Connections, operation counters, memory and replication state.",
            Schemas.object().enumeration("instance", "Instance name", true, names).build(),
            args -> {
              Document s = admin(args).runCommand(new Document("serverStatus", 1));
              Document out = new Document();
              for (String key : List.of("version", "uptime", "connections", "opcounters", "mem", "repl", "asserts")) {
                if (s.containsKey(key)) {
                  out.put(key, s.get(key));
                }
              }
              return out.toJson();
            }),
        new ToolSpec(
            "mongo_slow_operations",
            "Operations running longer than N seconds (default 3).",
            Schemas.object()
                .enumeration("instance", "Instance name", true, names)
                .integer("seconds", "Minimum running time", false)
                .build(),
            args -> {
              int secs = Args.integer(args, "seconds", 3, 0, 3600);
              Document cmd =
                  new Document("currentOp", 1)
                      .append("active", true)
                      .append("secs_running", new Document("$gte", secs));
              return admin(args).runCommand(cmd).toJson();
            }),
        new ToolSpec(
            "mongo_collections",
            "Collections of a database with document counts and indexes.",
            Schemas.object()
                .enumeration("instance", "Instance name", true, names)
                .string("database", "Database (default: the configured one)", false)
                .build(),
            args -> {
              MongoDatabase db = database(args);
              StringBuilder sb = new StringBuilder();
              for (String name : db.listCollectionNames()) {
                var coll = db.getCollection(name);
                List<String> indexes = new ArrayList<>();
                coll.listIndexes().forEach(i -> indexes.add(i.get("key").toString()));
                sb.append(name).append(" ~").append(coll.estimatedDocumentCount())
                    .append(" docs, indexes ").append(indexes).append('\n');
              }
              return sb.toString();
            }),
        new ToolSpec(
            "mongo_find",
            "Up to 10 documents matching a filter, e.g. {\"status\":\"FAILED\"}. Only fetch what"
                + " the investigation needs.",
            Schemas.object()
                .enumeration("instance", "Instance name", true, names)
                .string("database", "Database (default: the configured one)", false)
                .string("collection", "Collection", true)
                .string("filter", "JSON filter document", false)
                .integer("limit", "Max documents (default 5, max 10)", false)
                .build(),
            args -> {
              Document filter = Document.parse(Texts.firstNonBlank(Args.optional(args, "filter"), "{}"));
              rejectUnsafeOperators(filter);
              String collection = Args.required(args, "collection");
              if (collection.startsWith("system.")) {
                throw new ToolException("System collections are not allowed");
              }
              List<String> docs = new ArrayList<>();
              database(args)
                  .getCollection(collection)
                  .find(filter)
                  .limit(Args.integer(args, "limit", 5, 1, 10))
                  .maxTime(10, TimeUnit.SECONDS)
                  .forEach(d -> docs.add(Texts.truncate(d.toJson(), 3_000)));
              return docs.isEmpty() ? "No documents." : String.join("\n", docs);
            }));
  }

  private MongoClient client(JsonNode args) {
    String name = Args.required(args, "instance");
    HolmesProperties.Mongo cfg = instances.get(name);
    if (cfg == null) {
      throw new ToolException("Unknown instance '" + name + "'. Known: " + instances.keySet());
    }
    return clients.computeIfAbsent(
        name,
        n ->
            MongoClients.create(
                MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(cfg.getUri()))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(10, TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.readTimeout(20, TimeUnit.SECONDS))
                    .build()));
  }

  private MongoDatabase admin(JsonNode args) {
    return client(args).getDatabase("admin");
  }

  /** Only the configured database; admin/local/config are never reachable. */
  private MongoDatabase database(JsonNode args) {
    HolmesProperties.Mongo cfg = instances.get(Args.required(args, "instance"));
    String configured = cfg == null ? null : cfg.getDatabase();
    String requested = Args.optional(args, "database");
    String db = Texts.firstNonBlank(configured, requested);
    if (Texts.isBlank(db)) {
      throw new ToolException("No database configured for this instance");
    }
    if (configured != null && requested != null && !configured.equals(requested)) {
      throw new ToolException("Only database '" + configured + "' may be queried");
    }
    if (java.util.Set.of("admin", "local", "config").contains(db)) {
      throw new ToolException("System databases are not allowed");
    }
    return client(args).getDatabase(db);
  }

  private static final java.util.Set<String> SAFE_OPERATORS =
      java.util.Set.of("$eq", "$ne", "$gt", "$gte", "$lt", "$lte", "$in", "$nin", "$and", "$or",
          "$nor", "$not", "$exists", "$type", "$regex", "$options", "$size", "$all", "$elemMatch");

  /** Walks the parsed filter (escapes already decoded) and rejects any non-query operator. */
  static void rejectUnsafeOperators(Object node) {
    if (node instanceof java.util.Map<?, ?> map) {
      for (var e : map.entrySet()) {
        String key = String.valueOf(e.getKey());
        if (key.startsWith("$") && !SAFE_OPERATORS.contains(key)) {
          throw new ToolException("Operator " + key + " is not allowed");
        }
        rejectUnsafeOperators(e.getValue());
      }
    } else if (node instanceof java.util.List<?> list) {
      list.forEach(MongoToolset::rejectUnsafeOperators);
    }
  }

  @Override
  public ToolsetHealth health() {
    StringBuilder sb = new StringBuilder();
    boolean ok = true;
    for (String name : instances.keySet()) {
      try {
        JsonNode args = Schemas.args("instance", name);
        admin(args).runCommand(new Document("ping", 1));
        sb.append(name).append(": ok. ");
      } catch (RuntimeException e) {
        ok = false;
        sb.append(name).append(": ").append(e.getMessage()).append(". ");
      }
    }
    return new ToolsetHealth(ok, sb.toString().strip());
  }

  @Override
  public void destroy() {
    clients.values().forEach(MongoClient::close);
  }
}

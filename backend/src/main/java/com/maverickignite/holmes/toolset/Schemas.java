package com.maverickignite.holmes.toolset;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Fluent builder for the small JSON Schemas tools declare. */
public final class Schemas {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final ObjectNode schema = MAPPER.createObjectNode();
  private final ObjectNode properties;
  private final ArrayNode required;

  private Schemas() {
    schema.put("type", "object");
    properties = schema.putObject("properties");
    required = schema.putArray("required");
  }

  public static Schemas object() {
    return new Schemas();
  }

  /** A one-entry argument object, for calling tools from code (health checks, tests). */
  public static ObjectNode args(String name, String value) {
    return MAPPER.createObjectNode().put(name, value);
  }

  public static JsonNode empty() {
    return object().build();
  }

  public Schemas string(String name, String description, boolean isRequired) {
    return prop(name, "string", description, isRequired);
  }

  public Schemas integer(String name, String description, boolean isRequired) {
    return prop(name, "integer", description, isRequired);
  }

  public Schemas bool(String name, String description, boolean isRequired) {
    return prop(name, "boolean", description, isRequired);
  }

  public Schemas enumeration(
      String name, String description, boolean isRequired, String... values) {
    prop(name, "string", description, isRequired);
    ArrayNode e = ((ObjectNode) properties.get(name)).putArray("enum");
    for (String v : values) {
      e.add(v);
    }
    return this;
  }

  private Schemas prop(String name, String type, String description, boolean isRequired) {
    properties.putObject(name).put("type", type).put("description", description);
    if (isRequired) {
      required.add(name);
    }
    return this;
  }

  public JsonNode build() {
    return schema.deepCopy();
  }
}

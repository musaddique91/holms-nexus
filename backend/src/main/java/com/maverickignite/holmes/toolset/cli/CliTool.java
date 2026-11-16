package com.maverickignite.holmes.toolset.cli;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One command template. Elements of {@code command} are either strings with {@code ${param}}
 * placeholders, or {@code {if: param, args: [...]}} / {@code {unless: param, args: [...]}} blocks
 * for optional flags. Every substituted value is validated before the command runs.
 */
final class CliTool {

  /** No whitespace, no shell metacharacters, and no leading '-' (blocks option injection). */
  static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_.][A-Za-z0-9._:/@=,+%*\\-]{0,252}");

  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  record Param(
      String name,
      String type,
      String description,
      boolean required,
      String defaultValue,
      Pattern pattern,
      List<String> allowed) {}

  private final String name;
  private final String description;
  private final List<Param> params;
  private final JsonNode command;

  CliTool(String name, String description, List<Param> params, JsonNode command) {
    this.name = name;
    this.description = description;
    this.params = List.copyOf(params);
    this.command = command;
  }

  ToolSpec spec(HolmesProperties.CliToolset settings) {
    return new ToolSpec(
        name,
        description,
        schema(),
        args -> {
          List<String> argv = render(args);
          CommandRunner.Result result =
              CommandRunner.run(argv, settings.getEnv(), settings.getTimeout());
          if (result.timedOut()) {
            return "Command timed out after " + settings.getTimeout().toSeconds() + "s.\n"
                + result.output();
          }
          if (result.exitCode() != 0) {
            return "Command exited with " + result.exitCode() + ":\n" + result.output();
          }
          return result.output();
        });
  }

  List<String> render(JsonNode args) {
    Map<String, String> values = resolve(args);
    List<String> argv = new ArrayList<>();
    appendAll(command, values, argv);
    return argv;
  }

  private void appendAll(JsonNode elements, Map<String, String> values, List<String> argv) {
    for (JsonNode element : elements) {
      if (element.isString()) {
        argv.add(substitute(element.asString(), values));
      } else if (element.isObject()) {
        String ifParam = element.path("if").asString(null);
        String unlessParam = element.path("unless").asString(null);
        boolean include =
            ifParam != null ? truthy(values.get(ifParam)) : !truthy(values.get(unlessParam));
        if (include) {
          appendAll(element.path("args"), values, argv);
        }
      }
    }
  }

  private static boolean truthy(String v) {
    return v != null && !v.isEmpty() && !"false".equalsIgnoreCase(v);
  }

  private static String substitute(String template, Map<String, String> values) {
    Matcher m = PLACEHOLDER.matcher(template);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String v = values.get(m.group(1));
      if (v == null) {
        throw new ToolException("Argument '" + m.group(1) + "' is required");
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(v));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private Map<String, String> resolve(JsonNode args) {
    Map<String, String> values = new java.util.HashMap<>();
    for (Param p : params) {
      JsonNode raw = args == null ? null : args.get(p.name());
      String v = raw == null || raw.isNull() || raw.asString().isBlank() ? p.defaultValue() : raw.asString();
      if (v == null || v.isEmpty()) {
        if (p.required()) {
          throw new ToolException("Missing required argument '" + p.name() + "'");
        }
        continue;
      }
      switch (p.type()) {
        case "integer" -> {
          if (!v.matches("\\d{1,9}")) {
            throw new ToolException("Argument '" + p.name() + "' must be a positive integer");
          }
        }
        case "boolean" -> v = Boolean.toString(Boolean.parseBoolean(v));
        default -> {
          if (!p.allowed().isEmpty() && !p.allowed().contains(v)) {
            throw new ToolException(
                "Argument '" + p.name() + "' must be one of " + p.allowed());
          }
          Pattern pattern = p.pattern() != null ? p.pattern() : SAFE_VALUE;
          // "file://", "fileb://" (AWS) and "@file" (Azure) make CLIs read local files.
          if (!pattern.matcher(v).matches()
              || v.startsWith("-")
              || v.startsWith("@")
              || v.contains("://")) {
            throw new ToolException(
                "Argument '" + p.name() + "' contains characters that are not allowed");
          }
        }
      }
      values.put(p.name(), v);
    }
    return values;
  }

  private JsonNode schema() {
    ObjectNode schema = MAPPER.createObjectNode().put("type", "object");
    ObjectNode props = schema.putObject("properties");
    ArrayNode required = schema.putArray("required");
    for (Param p : params) {
      ObjectNode prop = props.putObject(p.name());
      prop.put("type", p.type());
      String desc = p.description();
      if (p.defaultValue() != null) {
        desc = desc + " (default: " + p.defaultValue() + ")";
      }
      prop.put("description", desc);
      if (!p.allowed().isEmpty()) {
        ArrayNode e = prop.putArray("enum");
        p.allowed().forEach(e::add);
      }
      if (p.required()) {
        required.add(p.name());
      }
    }
    return schema;
  }
}

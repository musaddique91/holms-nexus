package com.maverickignite.holmes.toolset;

import com.maverickignite.holmes.common.Texts;
import tools.jackson.databind.JsonNode;

/** Typed access to model-supplied tool arguments, with model-readable errors. */
public final class Args {

  private Args() {}

  public static String required(JsonNode args, String name) {
    String v = optional(args, name);
    if (Texts.isBlank(v)) {
      throw new ToolException("Missing required argument '" + name + "'");
    }
    return v;
  }

  public static String optional(JsonNode args, String name) {
    JsonNode v = args == null ? null : args.get(name);
    if (v == null || v.isNull()) {
      return null;
    }
    String s = v.isValueNode() ? v.asString() : v.toString();
    // Models often send "" for optional arguments they do not want to set.
    return Texts.isBlank(s) ? null : s;
  }

  public static int integer(JsonNode args, String name, int fallback, int min, int max) {
    JsonNode v = args == null ? null : args.get(name);
    int value = fallback;
    if (v != null && !v.isNull()) {
      try {
        value = v.isNumber() ? v.asInt() : Integer.parseInt(v.asString().trim());
      } catch (NumberFormatException e) {
        throw new ToolException("Argument '" + name + "' must be an integer");
      }
    }
    return Math.clamp(value, min, max);
  }

  public static boolean bool(JsonNode args, String name, boolean fallback) {
    JsonNode v = args == null ? null : args.get(name);
    if (v == null || v.isNull()) {
      return fallback;
    }
    return v.isBoolean() ? v.asBoolean() : Boolean.parseBoolean(v.asString());
  }
}

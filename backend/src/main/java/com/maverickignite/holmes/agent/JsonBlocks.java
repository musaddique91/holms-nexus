package com.maverickignite.holmes.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Extracts the structured JSON object a model was asked to end its answer with. */
public final class JsonBlocks {

  private static final Pattern FENCED =
      Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

  private JsonBlocks() {}

  /** The last fenced JSON object, else the last balanced top-level object; null if none parses. */
  public static JsonNode last(String text, ObjectMapper json) {
    if (text == null) {
      return null;
    }
    Matcher m = FENCED.matcher(text);
    String candidate = null;
    while (m.find()) {
      candidate = m.group(1);
    }
    if (candidate == null) {
      candidate = lastBalancedObject(text);
    }
    if (candidate == null) {
      return null;
    }
    try {
      JsonNode node = json.readTree(candidate);
      return node.isObject() ? node : null;
    } catch (JacksonException e) {
      return null;
    }
  }

  /** Text before the JSON block: the human-readable part of the answer. */
  public static String prose(String text) {
    if (text == null) {
      return "";
    }
    int fence = text.lastIndexOf("```json");
    return (fence >= 0 ? text.substring(0, fence) : text).strip();
  }

  private static String lastBalancedObject(String text) {
    int end = text.lastIndexOf('}');
    if (end < 0) {
      return null;
    }
    int depth = 0;
    for (int i = end; i >= 0; i--) {
      char c = text.charAt(i);
      if (c == '}') {
        depth++;
      } else if (c == '{') {
        depth--;
        if (depth == 0) {
          return text.substring(i, end + 1);
        }
      }
    }
    return null;
  }
}

package com.maverickignite.holmes.investigation;

import com.maverickignite.holmes.agent.JsonBlocks;
import com.maverickignite.holmes.common.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class AnalysisParser {

  private final ObjectMapper json;

  Analysis parse(String answer) {
    JsonNode node = JsonBlocks.last(answer, json);
    String report = JsonBlocks.prose(answer);
    if (node == null) {
      // The model ignored the format; keep its prose so the work is not lost.
      return new Analysis(
          Texts.truncate(firstParagraph(report), 500), report, List.of(), null, "LOW", null,
          "UNKNOWN", false, List.of(), report);
    }
    return new Analysis(
        text(node, "summary"),
        text(node, "rootCause"),
        list(node, "evidence"),
        text(node, "suggestedFix"),
        upper(node, "confidence", "LOW"),
        upper(node, "severity", null),
        upper(node, "category", "UNKNOWN"),
        node.path("codeFixRecommended").asBoolean(false),
        list(node, "suspectedFiles"),
        report);
  }

  private static String text(JsonNode n, String key) {
    JsonNode v = n.get(key);
    return v == null || v.isNull() ? null : v.asString();
  }

  private static String upper(JsonNode n, String key, String fallback) {
    String v = text(n, key);
    return Texts.isBlank(v) ? fallback : v.trim().toUpperCase(Locale.ROOT);
  }

  private static List<String> list(JsonNode n, String key) {
    List<String> out = new ArrayList<>();
    JsonNode v = n.get(key);
    if (v != null && v.isArray()) {
      v.forEach(item -> out.add(item.asString()));
    }
    return out;
  }

  private static String firstParagraph(String s) {
    int i = s.indexOf("\n\n");
    return i < 0 ? s : s.substring(0, i);
  }
}

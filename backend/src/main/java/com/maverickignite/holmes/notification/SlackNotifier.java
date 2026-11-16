package com.maverickignite.holmes.notification;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.notification.NotificationSettingsService.Config;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Slack via an incoming webhook (one fixed channel) or a bot token with chat.postMessage (lets each
 * service post to its own channel).
 */
@Component
class SlackNotifier implements Notifier {

  private final RestClient http;

  SlackNotifier(RestClient.Builder builder) {
    this.http = builder.clone().requestFactory(HttpClients.standard()).build();
  }

  @Override
  public String channel() {
    return "slack";
  }

  @Override
  public boolean switchedOn(Config c) {
    return c.slack().enabled();
  }

  @Override
  public String missing(Config c) {
    return Texts.isBlank(c.slack().webhookUrl()) && Texts.isBlank(c.slack().botToken())
        ? "Add an incoming-webhook URL or a bot token"
        : null;
  }

  @Override
  public void send(NotificationMessage m, Config c) {
    NotificationSettingsService.Slack config = c.slack();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "[" + m.severity() + "] " + m.service() + ": " + m.title());
    payload.put("blocks", blocks(m));
    payload.put("unfurl_links", false);

    if (!Texts.isBlank(config.botToken())) {
      String channel = Texts.firstNonBlank(m.slackChannel(), config.channel());
      if (Texts.isBlank(channel)) {
        throw new IllegalStateException("No Slack channel configured for " + m.service());
      }
      payload.put("channel", channel);
      JsonNode response =
          http.post()
              .uri("https://slack.com/api/chat.postMessage")
              .header("Authorization", "Bearer " + config.botToken())
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .body(JsonNode.class);
      if (response == null || !response.path("ok").asBoolean(false)) {
        throw new IllegalStateException(
            "Slack API error: " + (response == null ? "empty response" : response.path("error").asString()));
      }
    } else {
      http.post()
          .uri(config.webhookUrl())
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .toBodilessEntity();
    }
  }

  private static List<Map<String, Object>> blocks(NotificationMessage m) {
    List<Map<String, Object>> blocks = new ArrayList<>();
    blocks.add(
        Map.of(
            "type", "header",
            "text", Map.of("type", "plain_text", "text", Texts.truncate(emoji(m.severity()) + " " + m.title(), 150))));
    blocks.add(
        Map.of(
            "type", "section",
            "fields",
            List.of(
                mrkdwn("*Service*\n" + esc(m.service()) + (m.environment() == null ? "" : " (" + esc(m.environment()) + ")")),
                mrkdwn("*Severity*\n" + esc(m.severity())),
                mrkdwn("*Occurrences*\n" + m.occurrences()),
                mrkdwn("*Confidence*\n" + esc(Texts.firstNonBlank(m.confidence(), "n/a"))))));
    section(blocks, "*Summary*\n" + esc(m.summary()));
    section(blocks, "*Root cause*\n" + esc(m.rootCause()));
    if (m.prUrl() != null) {
      section(blocks, "*Fix PR* <" + m.prUrl() + "|" + esc(m.prUrl()) + ">\n" + esc(m.fixDescription()));
    } else {
      section(blocks, "*Suggested fix*\n" + esc(m.suggestedFix()));
      if (m.fixStatus() != null) {
        section(blocks, "_Auto-fix: " + esc(m.fixStatus()) + "_");
      }
    }
    blocks.add(
        Map.of(
            "type", "actions",
            "elements",
            List.of(
                Map.of(
                    "type", "button",
                    "text", Map.of("type", "plain_text", "text", "Open incident"),
                    "url", m.dashboardLink()))));
    return blocks;
  }

  private static void section(List<Map<String, Object>> blocks, String text) {
    if (!Texts.isBlank(text)) {
      blocks.add(Map.of("type", "section", "text", mrkdwn(Texts.truncate(text, 2_900))));
    }
  }

  private static Map<String, Object> mrkdwn(String text) {
    return Map.of("type", "mrkdwn", "text", text);
  }

  private static String esc(String s) {
    return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static String emoji(String severity) {
    return switch (severity == null ? "" : severity) {
      case "CRITICAL" -> "🔴";
      case "HIGH" -> "🟠";
      case "MEDIUM" -> "🟡";
      default -> "🔵";
    };
  }
}

package com.maverickignite.holmes.notification;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.notification.NotificationSettingsService.Config;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

/** Telegram Bot API sendMessage with HTML formatting. */
@Component
class TelegramNotifier implements Notifier {

  private static final int MAX_LENGTH = 4_000;

  private final RestClient http;

  TelegramNotifier(RestClient.Builder builder) {
    this.http = builder.clone().requestFactory(HttpClients.standard()).build();
  }

  @Override
  public String channel() {
    return "telegram";
  }

  @Override
  public boolean switchedOn(Config c) {
    return c.telegram().enabled();
  }

  @Override
  public String missing(Config c) {
    return Texts.isBlank(c.telegram().botToken()) ? "Add the bot token from @BotFather" : null;
  }

  @Override
  public void send(NotificationMessage m, Config c) {
    NotificationSettingsService.Telegram config = c.telegram();
    Target target = resolveTarget(m.telegramChatId(), config.chatId());
    if (target == null || Texts.isBlank(target.chatId())) {
      throw new IllegalStateException("No Telegram chat id configured for " + m.service());
    }
    StringBuilder sb = new StringBuilder();
    sb.append(SlackNotifier.emoji(m.severity())).append(" <b>").append(h(m.title())).append("</b>\n");
    sb.append("<i>").append(h(m.service()))
        .append(m.environment() == null ? "" : " · " + h(m.environment()))
        .append(" · ").append(h(m.severity())).append(" · ").append(m.occurrences()).append("×</i>\n\n");
    part(sb, "Summary", m.summary());
    part(sb, "Root cause", m.rootCause());
    if (m.prUrl() != null) {
      sb.append("<b>Fix PR:</b> ").append(h(m.prUrl())).append('\n');
    } else {
      part(sb, "Suggested fix", m.suggestedFix());
    }
    sb.append('\n').append(h(m.dashboardLink()));

    Map<String, Object> body = new HashMap<>();
    body.put("chat_id", target.chatId());
    if (target.messageThreadId() != null && target.messageThreadId() > 1) {
      body.put("message_thread_id", target.messageThreadId());
    }
    body.put("text", Texts.truncate(sb.toString(), MAX_LENGTH));
    body.put("parse_mode", "HTML");
    body.put("disable_web_page_preview", true);

    http.post()
        .uri(config.apiUrl() + "/bot{token}/sendMessage", config.botToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  record Target(String chatId, Long messageThreadId) {}

  static Target resolveTarget(String serviceChat, String defaultChat) {
    if (Texts.isBlank(serviceChat) && Texts.isBlank(defaultChat)) {
      return null;
    }
    // If serviceChat is purely numeric (a topic ID, e.g. "10") and defaultChat is configured:
    if (!Texts.isBlank(serviceChat) && !Texts.isBlank(defaultChat)) {
      String s = serviceChat.trim();
      if (s.matches("^\\d+$")) {
        Target def = parseTarget(defaultChat);
        return new Target(def.chatId(), Long.parseLong(s));
      }
    }
    String chat = Texts.firstNonBlank(serviceChat, defaultChat);
    return parseTarget(chat);
  }

  static Target parseTarget(String raw) {
    if (Texts.isBlank(raw)) {
      return null;
    }
    String s = raw.trim();

    // Support t.me/c/<chat_id>/<topic_id>[/<msg_id>]
    if (s.contains("t.me/c/")) {
      String path = s.substring(s.indexOf("t.me/c/") + 7);
      String[] parts = path.split("/");
      if (parts.length >= 2) {
        String cid = parts[0].trim();
        if (!cid.startsWith("-100")) {
          cid = "-100" + cid;
        }
        try {
          return new Target(cid, Long.parseLong(parts[1].trim()));
        } catch (NumberFormatException ignored) {
        }
      }
    }

    // Support web.telegram.org URLs containing #-1004432917613_10
    if (s.contains("#")) {
      s = s.substring(s.indexOf("#") + 1).trim();
    }

    // Support -1004432917613:10, -1004432917613_10, or -1004432917613/10
    for (char sep : new char[] {':', '_', '/'}) {
      int idx = s.indexOf(sep);
      if (idx > 0) {
        String cid = s.substring(0, idx).trim();
        String tid = s.substring(idx + 1).trim();
        if (tid.contains("/")) {
          tid = tid.split("/")[0].trim();
        }
        try {
          return new Target(cid, Long.parseLong(tid));
        } catch (NumberFormatException ignored) {
        }
      }
    }

    return new Target(s, null);
  }

  private static void part(StringBuilder sb, String label, String value) {
    if (!Texts.isBlank(value)) {
      sb.append("<b>").append(label).append(":</b> ").append(h(Texts.truncate(value, 1_200))).append("\n\n");
    }
  }

  private static String h(String s) {
    return s == null ? "" : HtmlUtils.htmlEscape(s);
  }
}

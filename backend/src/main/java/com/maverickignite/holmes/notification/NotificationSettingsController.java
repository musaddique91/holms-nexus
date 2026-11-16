package com.maverickignite.holmes.notification;

import com.maverickignite.holmes.common.BadRequestException;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.notification.NotificationService.ChannelStatus;
import com.maverickignite.holmes.notification.NotificationSettingsService.Config;
import com.maverickignite.holmes.notification.NotificationSettingsService.Update;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Notifications. Webhook URLs, tokens and the SMTP password are write-only: responses
 * only say whether one is set, plus a masked hint.
 */
@RestController
@RequestMapping("/api/settings/notifications")
class NotificationSettingsController {

  private static final Set<String> CHANNELS = Set.of("slack", "email", "telegram");

  private final NotificationSettingsService settings;
  private final NotificationService notifications;
  private final String dashboardUrl;

  NotificationSettingsController(
      NotificationSettingsService settings, NotificationService notifications, HolmesProperties properties) {
    this.settings = settings;
    this.notifications = notifications;
    this.dashboardUrl = properties.getDashboardUrl().replaceFirst("/$", "");
  }

  record SlackView(
      boolean enabled, boolean webhookSet, String webhookHint, boolean botTokenSet, String botTokenHint, String channel) {}

  record EmailView(
      boolean enabled,
      String smtpHost,
      int smtpPort,
      String smtpUsername,
      boolean smtpPasswordSet,
      boolean startTls,
      String from,
      List<String> to) {}

  record TelegramView(boolean enabled, boolean botTokenSet, String botTokenHint, String chatId) {}

  record View(
      String minSeverity,
      SlackView slack,
      EmailView email,
      TelegramView telegram,
      List<ChannelStatus> channels,
      String source,
      Instant updatedAt,
      String updatedBy) {}

  record TestResult(boolean ok, String channel, long latencyMs, String detail) {}

  @GetMapping
  View get() {
    return view(settings.current());
  }

  @PutMapping
  View save(@RequestBody Update update, @AuthenticationPrincipal Jwt jwt) {
    return view(settings.update(update, jwt == null ? "unknown" : jwt.getSubject()));
  }

  /** Sends a sample incident through one channel with the given (unsaved) settings. */
  @PostMapping("/test/{channel}")
  TestResult test(@PathVariable String channel, @RequestBody(required = false) Update update) {
    if (!CHANNELS.contains(channel)) {
      throw new BadRequestException("Unknown channel '" + channel + "'");
    }
    Config config = settings.preview(update);
    long start = System.nanoTime();
    try {
      notifications.test(channel, sample(), config);
      return new TestResult(true, channel, elapsed(start), "Sent. Check " + where(channel, config) + ".");
    } catch (Exception e) {
      String detail = Texts.firstNonBlank(e.getMessage(), e.getClass().getSimpleName());
      return new TestResult(false, channel, elapsed(start), Texts.truncate(detail, 400));
    }
  }

  private NotificationMessage sample() {
    return new NotificationMessage(
        0,
        "Test notification from holms-nexus",
        "holms-nexus",
        null,
        "LOW",
        1,
        "This is a test message sent from the Settings page. Real alerts look like this.",
        "No incident — the channel is configured correctly.",
        null,
        "HIGH",
        null,
        null,
        null,
        dashboardUrl + "/settings",
        null,
        List.of(),
        null);
  }

  private static String where(String channel, Config c) {
    return switch (channel) {
      case "slack" -> Texts.firstNonBlank(c.slack().channel(), "the webhook's Slack channel");
      case "email" -> String.join(", ", c.email().to());
      default -> "Telegram chat " + Texts.firstNonBlank(c.telegram().chatId(), "");
    };
  }

  private View view(Config c) {
    var slack = c.slack();
    var email = c.email();
    var telegram = c.telegram();
    return new View(
        c.minSeverity().name(),
        new SlackView(
            slack.enabled(), slack.webhookUrl() != null, slack.webhookHint(),
            slack.botToken() != null, slack.botTokenHint(), slack.channel()),
        new EmailView(
            email.enabled(), email.smtpHost(), email.smtpPort(), email.smtpUsername(),
            email.smtpPassword() != null, email.startTls(), email.from(), email.to()),
        new TelegramView(telegram.enabled(), telegram.botToken() != null, telegram.botTokenHint(), telegram.chatId()),
        notifications.status(c),
        c.source(),
        c.updatedAt(),
        c.updatedBy());
  }

  private static long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }
}

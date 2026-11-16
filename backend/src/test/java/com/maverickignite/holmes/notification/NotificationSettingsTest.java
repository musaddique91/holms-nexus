package com.maverickignite.holmes.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maverickignite.holmes.common.BadRequestException;
import com.maverickignite.holmes.incident.Severity;
import com.maverickignite.holmes.notification.NotificationSettingsService.Config;
import com.maverickignite.holmes.notification.NotificationSettingsService.Email;
import com.maverickignite.holmes.notification.NotificationSettingsService.Slack;
import com.maverickignite.holmes.notification.NotificationSettingsService.Telegram;
import com.maverickignite.holmes.scm.GitTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class NotificationSettingsTest {

  private static final Slack SLACK_OFF = new Slack(false, null, null, null, null, null);
  private static final Email EMAIL_OFF = new Email(false, null, 587, null, null, true, null, List.of());
  private static final Telegram TELEGRAM_OFF = new Telegram(false, null, null, null, "https://api.telegram.org");

  private static Config config(Severity min, Slack slack, Email email, Telegram telegram) {
    return new Config(1, min, slack, email, telegram, "database", null, null);
  }

  @Test
  void rejectsChannelsSwitchedOnWithoutWhatTheyNeed() {
    assertThatThrownBy(
            () -> NotificationSettingsService.validate(
                config(Severity.LOW, new Slack(true, null, null, null, null, null), EMAIL_OFF, TELEGRAM_OFF)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("webhook");
    assertThatThrownBy(
            () -> NotificationSettingsService.validate(
                config(Severity.LOW, new Slack(false, "https://evil.example/x", null, null, null, null), EMAIL_OFF, TELEGRAM_OFF)))
        .hasMessageContaining("hooks.slack.com");
    assertThatThrownBy(
            () -> NotificationSettingsService.validate(
                config(Severity.LOW, SLACK_OFF,
                    new Email(true, "smtp.gmail.com", 587, null, null, true, "holmes@x.com", List.of("not-an-address")),
                    TELEGRAM_OFF)))
        .hasMessageContaining("not valid");
    assertThatThrownBy(
            () -> NotificationSettingsService.validate(
                config(Severity.LOW, SLACK_OFF, EMAIL_OFF, new Telegram(true, "123:abc", null, null, null))))
        .hasMessageContaining("chat id");
  }

  @Test
  void acceptsCompleteChannels() {
    NotificationSettingsService.validate(
        config(
            Severity.HIGH,
            new Slack(true, "https://hooks.slack.com/services/T/B/x", null, null, null, null),
            new Email(true, "smtp.gmail.com", 587, "u", "p", true, "holms-nexus <holmes@x.com>", List.of("ops@x.com")),
            new Telegram(true, "123:abc", null, "-100123", null)));
    assertThat(NotificationSettingsService.parseSeverity("medium")).isEqualTo(Severity.MEDIUM);
    assertThatThrownBy(() -> NotificationSettingsService.parseSeverity("urgent")).isInstanceOf(BadRequestException.class);
  }

  @Test
  void sendsOnlyThroughSwitchedOnChannelsAndAtOrAboveMinimumSeverity() {
    List<String> sent = new ArrayList<>();
    Notifier slack = fake("slack", c -> c.slack().enabled(), sent);
    Notifier email = fake("email", c -> c.email().enabled(), sent);
    NotificationSettingsService settings = mock(NotificationSettingsService.class);
    when(settings.current())
        .thenReturn(
            config(Severity.HIGH, new Slack(true, "https://hooks.slack.com/x", null, null, null, null), EMAIL_OFF, TELEGRAM_OFF));
    NotificationService service = new NotificationService(List.of(slack, email), settings);

    assertThat(service.send(message("MEDIUM"))).isEmpty();
    assertThat(service.send(message("CRITICAL"))).containsExactly("slack");
    assertThat(sent).containsExactly("slack");
    assertThat(service.enabledChannels()).containsExactly("slack");
  }

  @Test
  void gitTokenHintHidesTheSecret() {
    assertThat(GitTokens.hint("tok_abcdefghijklmnop1234")).isEqualTo("tok_…1234");
    assertThat(GitTokens.hint("pat-abcdefghij5678")).isEqualTo("…5678");
  }

  private static Notifier fake(String name, Predicate<Config> on, List<String> sent) {
    return new Notifier() {
      @Override
      public String channel() {
        return name;
      }

      @Override
      public String missing(Config config) {
        return null;
      }

      @Override
      public boolean switchedOn(Config config) {
        return on.test(config);
      }

      @Override
      public void send(NotificationMessage message, Config config) {
        sent.add(name);
      }
    };
  }

  private static NotificationMessage message(String severity) {
    return new NotificationMessage(
        7, "boom", "payments-api", "staging", severity, 3, "s", "r", "f", "HIGH", null, null, null,
        "https://holmes/incidents/7", null, List.of(), null);
  }
}

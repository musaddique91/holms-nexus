package com.maverickignite.holmes.notification;

import com.maverickignite.holmes.common.BadRequestException;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.incident.Severity;
import com.maverickignite.holmes.settings.LlmSettingsService;
import com.maverickignite.holmes.settings.SecretCipher;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime notification configuration. The database row (Settings page) wins; until it is first
 * saved, the SLACK_* / EMAIL_* / TELEGRAM_* / MAIL_* environment variables are used, and the first
 * save copies them in.
 */
@Service
public class NotificationSettingsService {

  static final String SLACK_WEBHOOK_PREFIX = "https://hooks.slack.com/";

  private final NotificationSettingsRepository repository;
  private final SecretCipher cipher;
  private final HolmesProperties.Notifications env;
  private final Environment environment;
  private final AtomicReference<Config> cache = new AtomicReference<>();

  /** Fully resolved settings with secrets in clear text. Never returned by the API. */
  public record Config(
      long version,
      Severity minSeverity,
      Slack slack,
      Email email,
      Telegram telegram,
      String source,
      Instant updatedAt,
      String updatedBy) {}

  public record Slack(
      boolean enabled, String webhookUrl, String webhookHint, String botToken, String botTokenHint, String channel) {}

  public record Email(
      boolean enabled,
      String smtpHost,
      int smtpPort,
      String smtpUsername,
      String smtpPassword,
      boolean startTls,
      String from,
      List<String> to) {}

  public record Telegram(boolean enabled, String botToken, String botTokenHint, String chatId, String apiUrl) {}

  /**
   * Partial update. A null section or field keeps the saved value; for text fields "" clears it.
   * Secrets (webhook URL, tokens, SMTP password) are write-only: null keeps, "" removes.
   */
  public record Update(String minSeverity, SlackUpdate slack, EmailUpdate email, TelegramUpdate telegram) {}

  public record SlackUpdate(Boolean enabled, String webhookUrl, String botToken, String channel) {}

  public record EmailUpdate(
      Boolean enabled,
      String smtpHost,
      Integer smtpPort,
      String smtpUsername,
      String smtpPassword,
      Boolean startTls,
      String from,
      List<String> to) {}

  public record TelegramUpdate(Boolean enabled, String botToken, String chatId) {}

  public NotificationSettingsService(
      NotificationSettingsRepository repository,
      SecretCipher cipher,
      HolmesProperties properties,
      Environment environment) {
    this.repository = repository;
    this.cipher = cipher;
    this.env = properties.getNotifications();
    this.environment = environment;
  }

  public Config current() {
    Config c = cache.get();
    if (c == null) {
      c = toConfig(repository.findById(NotificationSettings.ID).orElseGet(this::fromEnvironment), 1);
      cache.compareAndSet(null, c);
    }
    return c;
  }

  @Transactional
  public Config update(Update u, String user) {
    NotificationSettings row = repository.findById(NotificationSettings.ID).orElseGet(this::fromEnvironment);
    apply(row, u);
    validate(toConfig(row, 0));
    row.setUpdatedAt(Instant.now());
    row.setUpdatedBy(user);
    repository.save(row);
    Config fresh = toConfig(row, cache.get() == null ? 1 : cache.get().version() + 1);
    cache.set(fresh);
    return fresh;
  }

  /** Settings as they would be after {@code u}, unsaved and unvalidated (for "Send test"). */
  public Config preview(Update u) {
    NotificationSettings row = repository.findById(NotificationSettings.ID).orElseGet(this::fromEnvironment);
    apply(row, u);
    return toConfig(row, 0);
  }

  // Mutates a detached copy only in preview(); update() saves it inside the transaction.
  private void apply(NotificationSettings s, Update u) {
    if (u == null) {
      return;
    }
    if (u.minSeverity() != null) {
      s.setMinSeverity(parseSeverity(u.minSeverity()).name());
    }
    SlackUpdate slack = u.slack();
    if (slack != null) {
      if (slack.enabled() != null) s.setSlackEnabled(slack.enabled());
      if (slack.channel() != null) s.setSlackChannel(blankToNull(slack.channel()));
      if (slack.webhookUrl() != null) {
        String url = slack.webhookUrl().trim();
        s.setSlackWebhookEncrypted(url.isEmpty() ? null : cipher.encrypt(url));
        s.setSlackWebhookHint(url.isEmpty() ? null : webhookHint(url));
      }
      if (slack.botToken() != null) {
        String token = slack.botToken().trim();
        s.setSlackBotTokenEncrypted(token.isEmpty() ? null : cipher.encrypt(token));
        s.setSlackBotTokenHint(token.isEmpty() ? null : LlmSettingsService.hint(token));
      }
    }
    EmailUpdate email = u.email();
    if (email != null) {
      if (email.enabled() != null) s.setEmailEnabled(email.enabled());
      if (email.smtpHost() != null) s.setSmtpHost(blankToNull(email.smtpHost()));
      if (email.smtpPort() != null) s.setSmtpPort(email.smtpPort());
      if (email.smtpUsername() != null) s.setSmtpUsername(blankToNull(email.smtpUsername()));
      if (email.smtpPassword() != null) {
        s.setSmtpPasswordEncrypted(email.smtpPassword().isEmpty() ? null : cipher.encrypt(email.smtpPassword()));
      }
      if (email.startTls() != null) s.setSmtpStartTls(email.startTls());
      if (email.from() != null) s.setEmailFrom(blankToNull(email.from()));
      if (email.to() != null) {
        s.setEmailTo(
            blankToNull(String.join(",", email.to().stream().map(String::trim).filter(t -> !t.isEmpty()).toList())));
      }
    }
    TelegramUpdate telegram = u.telegram();
    if (telegram != null) {
      if (telegram.enabled() != null) s.setTelegramEnabled(telegram.enabled());
      if (telegram.chatId() != null) s.setTelegramChatId(blankToNull(telegram.chatId()));
      if (telegram.botToken() != null) {
        String token = telegram.botToken().trim();
        s.setTelegramTokenEncrypted(token.isEmpty() ? null : cipher.encrypt(token));
        s.setTelegramTokenHint(token.isEmpty() ? null : LlmSettingsService.hint(token));
      }
    }
  }

  /** Rejects settings that would turn a channel on without what it needs to deliver. */
  static void validate(Config c) {
    Slack slack = c.slack();
    if (slack.webhookUrl() != null && !slack.webhookUrl().startsWith(SLACK_WEBHOOK_PREFIX)) {
      throw new BadRequestException("Slack webhook URL must start with " + SLACK_WEBHOOK_PREFIX);
    }
    if (slack.enabled() && slack.webhookUrl() == null && slack.botToken() == null) {
      throw new BadRequestException("Slack needs an incoming-webhook URL or a bot token");
    }
    if (slack.enabled() && slack.webhookUrl() == null && slack.channel() == null) {
      throw new BadRequestException("Slack bot token needs a default channel (e.g. #alerts)");
    }
    Email email = c.email();
    if (email.smtpPort() < 1 || email.smtpPort() > 65_535) {
      throw new BadRequestException("SMTP port must be between 1 and 65535");
    }
    if (email.enabled()) {
      if (Texts.isBlank(email.smtpHost())) {
        throw new BadRequestException("E-mail needs an SMTP host");
      }
      if (Texts.isBlank(email.from())) {
        throw new BadRequestException("E-mail needs a From address");
      }
      address(email.from(), "From");
      if (email.to().isEmpty()) {
        throw new BadRequestException("E-mail needs at least one recipient");
      }
    }
    email.to().forEach(to -> address(to, "Recipient"));
    if (c.telegram().enabled()) {
      if (c.telegram().botToken() == null) {
        throw new BadRequestException("Telegram needs a bot token");
      }
      if (c.telegram().chatId() == null) {
        throw new BadRequestException("Telegram needs a chat id");
      }
    }
  }

  private static void address(String value, String label) {
    try {
      InternetAddress a = new InternetAddress(value, true);
      if (a.getAddress() == null || !a.getAddress().contains("@")) {
        throw new AddressException("no @");
      }
    } catch (AddressException e) {
      throw new BadRequestException(label + " address '" + Texts.truncate(value, 80) + "' is not valid");
    }
  }

  private Config toConfig(NotificationSettings s, long version) {
    return new Config(
        version,
        parseSeverity(s.getMinSeverity()),
        new Slack(
            s.isSlackEnabled(),
            cipher.decrypt(s.getSlackWebhookEncrypted()),
            s.getSlackWebhookHint(),
            cipher.decrypt(s.getSlackBotTokenEncrypted()),
            s.getSlackBotTokenHint(),
            s.getSlackChannel()),
        new Email(
            s.isEmailEnabled(),
            s.getSmtpHost(),
            s.getSmtpPort() == null ? 587 : s.getSmtpPort(),
            s.getSmtpUsername(),
            cipher.decrypt(s.getSmtpPasswordEncrypted()),
            s.isSmtpStartTls(),
            s.getEmailFrom(),
            Texts.splitCsv(s.getEmailTo())),
        new Telegram(
            s.isTelegramEnabled(),
            cipher.decrypt(s.getTelegramTokenEncrypted()),
            s.getTelegramTokenHint(),
            s.getTelegramChatId(),
            env.getTelegram().getApiUrl()),
        s.getUpdatedAt() == null ? "environment" : "database",
        s.getUpdatedAt(),
        s.getUpdatedBy());
  }

  /** The row as the environment variables describe it; used until the first save. */
  private NotificationSettings fromEnvironment() {
    NotificationSettings s = new NotificationSettings();
    s.setId(NotificationSettings.ID);
    s.setMinSeverity(Severity.LOW.name());
    HolmesProperties.Slack slack = env.getSlack();
    s.setSlackEnabled(slack.isEnabled());
    if (!Texts.isBlank(slack.getWebhookUrl())) {
      s.setSlackWebhookEncrypted(cipher.encrypt(slack.getWebhookUrl().trim()));
      s.setSlackWebhookHint(webhookHint(slack.getWebhookUrl().trim()));
    }
    if (!Texts.isBlank(slack.getBotToken())) {
      s.setSlackBotTokenEncrypted(cipher.encrypt(slack.getBotToken().trim()));
      s.setSlackBotTokenHint(LlmSettingsService.hint(slack.getBotToken().trim()));
    }
    s.setSlackChannel(blankToNull(slack.getDefaultChannel()));

    HolmesProperties.Email email = env.getEmail();
    s.setEmailEnabled(email.isEnabled());
    s.setSmtpHost(blankToNull(environment.getProperty("spring.mail.host")));
    s.setSmtpPort(environment.getProperty("spring.mail.port", Integer.class, 587));
    s.setSmtpUsername(blankToNull(environment.getProperty("spring.mail.username")));
    String password = environment.getProperty("spring.mail.password");
    if (!Texts.isBlank(password)) {
      s.setSmtpPasswordEncrypted(cipher.encrypt(password));
    }
    s.setSmtpStartTls(
        environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, true));
    s.setEmailFrom(blankToNull(email.getFrom()));
    s.setEmailTo(blankToNull(String.join(",", email.getTo())));

    HolmesProperties.Telegram telegram = env.getTelegram();
    s.setTelegramEnabled(telegram.isEnabled());
    if (!Texts.isBlank(telegram.getBotToken())) {
      s.setTelegramTokenEncrypted(cipher.encrypt(telegram.getBotToken().trim()));
      s.setTelegramTokenHint(LlmSettingsService.hint(telegram.getBotToken().trim()));
    }
    s.setTelegramChatId(blankToNull(telegram.getChatId()));
    return s;
  }

  static Severity parseSeverity(String value) {
    if (Texts.isBlank(value)) {
      return Severity.LOW;
    }
    try {
      return Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Minimum severity must be one of CRITICAL, HIGH, MEDIUM, LOW");
    }
  }

  /** "hooks.slack.com/…/AbCd": enough to recognise the webhook, useless to post with. */
  static String webhookHint(String url) {
    String tail = url.length() > 4 ? url.substring(url.length() - 4) : "";
    return "hooks.slack.com/…" + tail;
  }

  private static String blankToNull(String s) {
    return Texts.isBlank(s) ? null : s.trim();
  }
}

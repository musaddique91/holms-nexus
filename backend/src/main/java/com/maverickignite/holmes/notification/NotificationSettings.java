package com.maverickignite.holmes.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** The single row of notification-channel configuration (Settings page). */
@Getter
@Setter
@Entity
@Table(name = "notification_settings")
class NotificationSettings {

  static final short ID = 1;

  @Id private Short id;
  private String minSeverity;

  private boolean slackEnabled;
  private String slackWebhookEncrypted;
  private String slackWebhookHint;
  private String slackBotTokenEncrypted;
  private String slackBotTokenHint;
  private String slackChannel;

  private boolean emailEnabled;
  private String smtpHost;
  private Integer smtpPort;
  private String smtpUsername;
  private String smtpPasswordEncrypted;

  @Column(name = "smtp_starttls")
  private boolean smtpStartTls;

  private String emailFrom;
  private String emailTo;

  private boolean telegramEnabled;
  private String telegramTokenEncrypted;
  private String telegramTokenHint;
  private String telegramChatId;

  private Instant updatedAt;
  private String updatedBy;
}

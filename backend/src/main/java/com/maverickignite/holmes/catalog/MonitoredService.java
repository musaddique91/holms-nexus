package com.maverickignite.holmes.catalog;

import com.maverickignite.holmes.common.Texts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** An application whose errors Holmes watches, and where its code lives. */
@Getter
@Setter
@Entity
@Table(name = "monitored_service")
public class MonitoredService {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Canonical name; incidents are grouped under it. */
  @Column(nullable = false, unique = true)
  private String name;

  private String displayName;

  /** Comma-separated alternative names seen in logs (container names per environment). */
  private String aliases;

  private String environment;
  private String repoUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private GitProviderType gitProvider = GitProviderType.NONE;

  private String defaultBranch = "main";

  /** Sub-directory holding the service's code inside a mono-repo, e.g. {@code backend}. */
  private String codePath;

  /** Free-text runbook injected into the investigation prompt. */
  private String runbook;

  private boolean autoFix;
  private String slackChannel;
  private String emailRecipients;
  private String telegramChatId;

  /** Own git personal access token (encrypted); null = use the global token. */
  private String gitTokenEncrypted;

  private String gitTokenHint;

  private Instant createdAt;
  private Instant updatedAt;

  public List<String> aliasList() {
    return Texts.splitCsv(aliases);
  }

  public boolean hasRepository() {
    return gitProvider != GitProviderType.NONE && !Texts.isBlank(repoUrl);
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}

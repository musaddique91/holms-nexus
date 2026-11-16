package com.maverickignite.holmes.settings;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** The single row of runtime LLM configuration (Settings page). */
@Getter
@Setter
@Entity
@Table(name = "llm_settings")
public class LlmSettings {

  public static final short ID = 1;

  @Id private Short id;
  private String provider;
  private String model;
  private String fixModel;
  private String baseUrl;
  private String azureDeployment;
  private String apiKeyEncrypted;
  private String apiKeyHint;
  private int maxTokens;
  private String maxTokensParam;
  private BigDecimal temperature;
  private Instant updatedAt;
  private String updatedBy;
  /** Provider from the shared list (llm_provider); null = the standalone fields above apply. */
  private UUID providerId;
}

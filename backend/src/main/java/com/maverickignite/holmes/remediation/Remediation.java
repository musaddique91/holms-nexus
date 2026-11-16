package com.maverickignite.holmes.remediation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** An attempt to fix an incident in code and open a pull request. */
@Getter
@Setter
@Entity
@Table(name = "remediation")
public class Remediation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long incidentId;

  private Long investigationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RemediationStatus status;

  private String model;
  private String branch;
  private String prUrl;
  private String prNumber;
  private String title;
  private String description;
  /** Comma-separated repo-relative paths. */
  private String filesChanged;
  private String diff;
  private String reason;
  private String error;
  private int steps;
  private long inputTokens;
  private long outputTokens;
  private Instant startedAt;
  private Instant finishedAt;
}

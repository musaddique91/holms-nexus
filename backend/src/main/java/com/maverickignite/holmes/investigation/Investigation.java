package com.maverickignite.holmes.investigation;

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

/** One agent run: a root-cause analysis of an incident, or an operator's free-form question. */
@Getter
@Setter
@Entity
@Table(name = "investigation")
public class Investigation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long incidentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvestigationKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvestigationStatus status;

  private String provider;
  private String model;
  private String question;
  private String summary;
  private String rootCause;
  /** JSON array of strings. */
  private String evidence;
  private String suggestedFix;
  private String confidence;
  private String severity;
  private String category;
  private boolean codeFixRecommended;
  /** JSON array of strings. */
  private String suspectedFiles;
  private String report;
  private String rawAnswer;
  private int steps;
  private int toolCallCount;
  private long inputTokens;
  private long outputTokens;
  private String error;
  private Instant startedAt;
  private Instant finishedAt;
}

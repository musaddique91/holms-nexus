package com.maverickignite.holmes.incident;

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

/**
 * One distinct error, identified by its fingerprint. Repeat occurrences only bump counters, so a
 * hot loop throwing the same exception costs a single investigation. Rows are inserted and
 * counted by {@link IncidentStore} with atomic SQL; JPA is used for reads and status changes.
 */
@Getter
@Setter
@Entity
@Table(name = "incident")
public class Incident {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String fingerprint;

  @Column(nullable = false)
  private String serviceName;

  private String environment;

  @Column(nullable = false)
  private String title;

  private String errorType;
  private String level;
  private String sampleMessage;
  private String sampleStackTrace;
  private String logger;
  private String host;
  private String source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IncidentStatus status;

  @Enumerated(EnumType.STRING)
  private Severity severity;

  private long occurrenceCount;
  private Instant firstSeenAt;
  private Instant lastSeenAt;
  private Instant statusChangedAt;
  private Instant createdAt;
  private Instant updatedAt;
}

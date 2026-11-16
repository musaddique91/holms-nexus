package com.maverickignite.holmes.investigation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Audit trail of every tool the agent ran, shown step by step in the UI. */
@Getter
@Setter
@Entity
@Table(name = "tool_call")
public class ToolCallRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long investigationId;
  private Long remediationId;
  private int sequence;
  private String toolName;
  private String arguments;
  private String output;
  private boolean success;
  private long durationMs;
  private Instant createdAt;
}

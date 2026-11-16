package com.maverickignite.holmes.incident;

import java.util.Set;

public enum IncidentStatus {
  /** Waiting for the scheduler to start an investigation. */
  NEW,
  INVESTIGATING,
  /** Root cause analysed; no automatic fix attempted (or none possible). */
  INVESTIGATED,
  FIXING,
  PR_OPENED,
  FAILED,
  RESOLVED,
  IGNORED;

  /** Statuses a worker owns; rows stuck here after a crash are re-queued. */
  public static final Set<IncidentStatus> IN_FLIGHT = Set.of(INVESTIGATING, FIXING);
}

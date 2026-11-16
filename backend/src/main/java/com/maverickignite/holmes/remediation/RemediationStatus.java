package com.maverickignite.holmes.remediation;

public enum RemediationStatus {
  RUNNING,
  PR_OPENED,
  /** The agent concluded a code change is not the right fix, or made none. */
  NO_CHANGE,
  /** A change was proposed but rejected by a safety rule. */
  REJECTED,
  FAILED
}

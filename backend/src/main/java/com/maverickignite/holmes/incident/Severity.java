package com.maverickignite.holmes.incident;

import java.util.Locale;

public enum Severity {
  CRITICAL,
  HIGH,
  MEDIUM,
  LOW;

  public static Severity parse(String value, Severity fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}

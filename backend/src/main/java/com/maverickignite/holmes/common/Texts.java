package com.maverickignite.holmes.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Small string helpers shared across modules. */
public final class Texts {

  private Texts() {}

  public static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  public static String firstNonBlank(String... values) {
    for (String v : values) {
      if (!isBlank(v)) {
        return v;
      }
    }
    return null;
  }

  /** Truncate to {@code max} characters, keeping head and tail so stack-trace roots survive. */
  public static String truncateMiddle(String s, int max) {
    if (s == null || s.length() <= max) {
      return s;
    }
    int head = (int) (max * 0.7);
    int tail = max - head;
    return s.substring(0, head)
        + "\n... ["
        + (s.length() - max)
        + " chars truncated] ...\n"
        + s.substring(s.length() - tail);
  }

  public static String truncate(String s, int max) {
    if (s == null || s.length() <= max) {
      return s;
    }
    return s.substring(0, Math.max(0, max - 1)) + "…";
  }

  public static List<String> splitCsv(String csv) {
    if (isBlank(csv)) {
      return List.of();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public static String joinCsv(Collection<String> values) {
    if (values == null) {
      return null;
    }
    return values.stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining(","));
  }
}

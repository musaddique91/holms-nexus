package com.maverickignite.holmes.investigation;

import java.util.List;

/** The structured verdict parsed from the agent's final answer. */
public record Analysis(
    String summary,
    String rootCause,
    List<String> evidence,
    String suggestedFix,
    String confidence,
    String severity,
    String category,
    boolean codeFixRecommended,
    List<String> suspectedFiles,
    String report) {}

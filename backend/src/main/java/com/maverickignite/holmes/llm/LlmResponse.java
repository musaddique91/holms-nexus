package com.maverickignite.holmes.llm;

import java.util.List;

public record LlmResponse(
    String text, List<ToolCall> toolCalls, String stopReason, long inputTokens, long outputTokens) {

  public boolean wantsTools() {
    return toolCalls != null && !toolCalls.isEmpty();
  }
}

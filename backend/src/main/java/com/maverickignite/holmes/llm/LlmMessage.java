package com.maverickignite.holmes.llm;

import java.util.List;

/** Provider-neutral conversation turn. */
public sealed interface LlmMessage {

  record User(String text) implements LlmMessage {}

  record Assistant(String text, List<ToolCall> toolCalls) implements LlmMessage {
    public Assistant {
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
  }

  record ToolResult(String toolCallId, String toolName, String content, boolean error)
      implements LlmMessage {}
}

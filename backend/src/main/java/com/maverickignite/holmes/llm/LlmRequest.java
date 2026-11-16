package com.maverickignite.holmes.llm;

import java.util.List;

public record LlmRequest(
    String model,
    String system,
    List<LlmMessage> messages,
    List<ToolDefinition> tools,
    int maxTokens) {}

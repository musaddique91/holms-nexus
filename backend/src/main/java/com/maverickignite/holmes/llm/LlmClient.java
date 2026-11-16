package com.maverickignite.holmes.llm;

/** One chat-completion round trip with tool calling. Implementations must be thread-safe. */
public interface LlmClient {

  String provider();

  String defaultModel();

  /** Model for the code-fix agent; defaults to the investigation model. */
  default String fixModel() {
    return defaultModel();
  }

  default int maxTokens() {
    return 4096;
  }

  LlmResponse complete(LlmRequest request);
}

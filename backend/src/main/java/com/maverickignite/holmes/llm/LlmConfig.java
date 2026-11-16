package com.maverickignite.holmes.llm;

import com.maverickignite.holmes.config.HolmesProperties;
import java.util.Locale;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Builds a concrete client for a provider; used by {@link RoutingLlmClient} and the settings test. */
public final class LlmConfig {

  private LlmConfig() {}

  public static LlmClient create(HolmesProperties.Llm llm, ObjectMapper json, RestClient.Builder builder) {
    return switch (llm.getProvider().toLowerCase(Locale.ROOT)) {
      case "anthropic" -> new AnthropicClient(llm, json, builder);
      case "openai", "azure", "ollama", "openrouter", "gemini", "nvidia", "openai-compatible" ->
          new OpenAiCompatibleClient(llm, json, builder);
      default ->
          throw new IllegalStateException(
              "Unknown LLM provider '"
                  + llm.getProvider()
                  + "'. Use anthropic, openai, azure, gemini, ollama, nvidia, openrouter or"
                  + " openai-compatible.");
    };
  }
}

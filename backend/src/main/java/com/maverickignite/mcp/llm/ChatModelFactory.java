package com.maverickignite.mcp.llm;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Component;

/** Builds Spring AI chat models on demand from provider settings stored in the DB. */
@Component
public class ChatModelFactory {

    public ChatModel create(LlmProviderEntity provider, String model, Double temperature, Integer maxTokens) {
        return create(provider, model, temperature, maxTokens, null);
    }

    /**
     * @param fallbackResolver consulted when the model calls a tool name that isn't registered verbatim (e.g. an
     *     invented namespace); {@code null} keeps Spring AI's strict behaviour
     */
    public ChatModel create(LlmProviderEntity provider, String model, Double temperature, Integer maxTokens,
            ToolCallbackResolver fallbackResolver) {
        ToolCallingManager toolCallingManager = fallbackResolver == null ? DefaultToolCallingManager.builder().build()
                : DefaultToolCallingManager.builder()
                        .toolCallbackResolver(fallbackResolver)
                        .resolutionFallbackEnabled(true)
                        .build();
        String resolvedModel = model == null || model.isBlank() ? provider.getDefaultModel() : model;
        return switch (provider.getType()) {
            case ANTHROPIC -> AnthropicChatModel.builder()
                    .options(AnthropicChatOptions.builder()
                            .apiKey(provider.getApiKey())
                            .baseUrl(blankToNull(provider.getBaseUrl()))
                            .model(resolvedModel)
                            .maxTokens(maxTokens == null ? 8192 : maxTokens)
                            .temperature(temperature)
                            .build())
                    .toolCallingManager(toolCallingManager)
                    .build();
            case OPENAI -> OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .apiKey(provider.getApiKey())
                            .baseUrl(blankToNull(provider.getBaseUrl()))
                            .model(resolvedModel)
                            .maxTokens(maxTokens)
                            .temperature(temperature)
                            .build())
                    .toolCallingManager(toolCallingManager)
                    .build();
            case OLLAMA -> OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()
                                    ? "http://localhost:11434" : provider.getBaseUrl())
                            .build())
                    .options(OllamaChatOptions.builder().model(resolvedModel).temperature(temperature).build())
                    .toolCallingManager(toolCallingManager)
                    .build();
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

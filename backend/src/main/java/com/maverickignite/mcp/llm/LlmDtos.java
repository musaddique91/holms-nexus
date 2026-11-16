package com.maverickignite.mcp.llm;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class LlmDtos {

    private LlmDtos() {
    }

    public record ProviderRequest(@NotBlank String name, @NotNull ProviderType type, String apiKey, String baseUrl,
            @NotBlank String defaultModel, List<String> models, Boolean enabled) {
    }

    /** API key is never returned; only whether one is set and a short hint. */
    public record ProviderView(UUID id, String name, ProviderType type, boolean hasApiKey, String apiKeyHint,
            String baseUrl, String defaultModel, List<String> models, boolean enabled, boolean ready) {
    }

    public record TestResult(boolean ok, String message, long latencyMs) {
    }
}

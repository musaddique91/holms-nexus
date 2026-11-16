package com.maverickignite.holmes.settings;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.llm.LlmClient;
import com.maverickignite.holmes.llm.LlmConfig;
import com.maverickignite.holmes.llm.LlmMessage;
import com.maverickignite.holmes.llm.LlmRequest;
import com.maverickignite.holmes.llm.LlmResponse;
import com.maverickignite.holmes.llm.ToolDefinition;
import com.maverickignite.holmes.settings.LlmSettingsService.Effective;
import com.maverickignite.holmes.settings.LlmSettingsService.Update;
import com.maverickignite.holmes.toolset.Schemas;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.maverickignite.mcp.llm.LlmProviderRepository;
import com.maverickignite.mcp.llm.ProviderType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Settings → LLM. The API key is write-only: responses only say whether one is set. */
@RestController
@RequestMapping("/api/settings/llm")
class LlmSettingsController {

  private static final Map<String, String> DEFAULT_URLS = LlmSettingsService.DEFAULT_URLS;

  private final LlmSettingsService settings;
  private final LlmProviderRepository providers;
  private final ObjectMapper json;
  private final RestClient.Builder builder;

  LlmSettingsController(
      LlmSettingsService settings, LlmProviderRepository providers, ObjectMapper json, RestClient.Builder builder) {
    this.settings = settings;
    this.providers = providers;
    this.json = json;
    this.builder = builder;
  }

  record View(
      String provider,
      String model,
      String fixModel,
      String baseUrl,
      String defaultBaseUrl,
      String azureDeployment,
      int maxTokens,
      String maxTokensParam,
      double temperature,
      boolean apiKeySet,
      String apiKeySource,
      String apiKeyHint,
      Instant updatedAt,
      String updatedBy,
      List<String> providers,
      /* holms-nexus: the shared provider in use (null = standalone settings) and the shared list. */
      UUID providerId,
      String providerName,
      List<SharedProvider> sharedProviders) {}

  /** A provider from the shared Models list, as the AI model picker needs it (no key). */
  record SharedProvider(
      UUID id, String name, ProviderType type, String baseUrl, boolean hasApiKey, boolean enabled,
      String defaultModel, List<String> models) {}

  private View view(Effective e) {
    List<SharedProvider> shared =
        providers.findAllByOrderByCreatedAtAsc().stream()
            .map(
                p ->
                    new SharedProvider(
                        p.getId(), p.getName(), p.getType(), p.getBaseUrl(), !Texts.isBlank(p.getApiKey()),
                        p.isEnabled(), p.getDefaultModel(), p.getModels()))
            .toList();
    return new View(
        e.provider(), e.model(), e.fixModel(), e.baseUrl(), DEFAULT_URLS.get(e.provider()),
        e.azureDeployment(), e.maxTokens(), e.maxTokensParam(), e.temperature(),
        !Texts.isBlank(e.apiKey()), e.apiKeySource(), e.apiKeyHint(), e.updatedAt(), e.updatedBy(),
        LlmSettingsService.PROVIDERS.stream().sorted().toList(), e.providerId(), e.providerName(), shared);
  }

  record TestResult(boolean ok, boolean toolCalling, long latencyMs, String model, String detail) {}

  @GetMapping
  View get() {
    return view(settings.current());
  }

  @PutMapping
  View save(@RequestBody Update update, @AuthenticationPrincipal Jwt jwt) {
    return view(settings.update(update, jwt == null ? "unknown" : jwt.getSubject()));
  }

  /** Lists models the provider offers for the given (or saved) credentials. */
  @PostMapping("/models")
  List<String> models(@RequestBody Update update) {
    Effective e = settings.preview(update);
    String base = Texts.firstNonBlank(e.baseUrl(), DEFAULT_URLS.get(e.provider()));
    if (base == null || "azure".equals(e.provider())) {
      return List.of();
    }
    RestClient http =
        builder.clone().requestFactory(HttpClients.factory(Duration.ofSeconds(5), Duration.ofSeconds(15))).build();
    try {
      JsonNode body;
      if ("anthropic".equals(e.provider())) {
        body =
            http.get().uri(base + "/v1/models?limit=100")
                .header("x-api-key", Texts.firstNonBlank(e.apiKey(), ""))
                .header("anthropic-version", "2023-06-01")
                .retrieve().body(JsonNode.class);
      } else {
        RestClient.RequestHeadersSpec<?> req = http.get().uri(base + "/models");
        if (!Texts.isBlank(e.apiKey())) {
          req = req.header("Authorization", "Bearer " + e.apiKey());
        }
        body = req.retrieve().body(JsonNode.class);
      }
      List<String> ids = new ArrayList<>();
      body.path("data").forEach(m -> ids.add(m.path("id").asString()));
      return ids.stream().filter(id -> !id.isBlank()).sorted().toList();
    } catch (RuntimeException ex) {
      return List.of();
    }
  }

  /**
   * Sends one real request with a dummy tool using the given (unsaved) settings, so a wrong key,
   * URL or a model without tool calling is caught before Holmes depends on it.
   */
  @PostMapping("/test")
  TestResult test(@RequestBody Update update) {
    Effective e = settings.preview(update);
    long start = System.nanoTime();
    try {
      LlmClient client = LlmConfig.create(settings.toClientConfig(e), json, builder);
      ToolDefinition ping =
          new ToolDefinition(
              "check_service",
              "Check whether a service is healthy",
              Schemas.object().string("service", "Service name", true).build());
      LlmResponse r =
          client.complete(
              new LlmRequest(
                  e.model(),
                  "You are a connectivity test. Always use the provided tool.",
                  List.of(new LlmMessage.User("Is the orders-api service healthy? Use the tool.")),
                  List.of(ping),
                  Math.min(1024, e.maxTokens())));
      long ms = (System.nanoTime() - start) / 1_000_000;
      boolean tools = r.wantsTools();
      return new TestResult(
          true, tools, ms, e.model(),
          tools ? "Connected; tool calling works." : "Connected, but the model did not call the tool. Investigations need tool calling — try another model.");
    } catch (RuntimeException ex) {
      long ms = (System.nanoTime() - start) / 1_000_000;
      return new TestResult(false, false, ms, e.model(), Texts.truncate(ex.getMessage(), 400));
    }
  }
}

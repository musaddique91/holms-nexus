package com.maverickignite.holmes.llm;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI Chat Completions protocol. The same wire format is spoken by OpenAI, Azure OpenAI,
 * Gemini's OpenAI endpoint, Ollama (incl. gpt-oss), OpenRouter, NVIDIA NIM, vLLM, and LiteLLM proxies (the usual
 * way to reach Bedrock or Vertex), so one client covers them all.
 */
class OpenAiCompatibleClient implements LlmClient {

  private final HolmesProperties.Llm config;
  private final ObjectMapper json;
  private final RestClient http;
  private final String path;
  private final String provider;

  OpenAiCompatibleClient(
      HolmesProperties.Llm config, ObjectMapper json, RestClient.Builder builder) {
    this.config = config;
    this.json = json;
    this.provider = config.getProvider().toLowerCase(Locale.ROOT);
    RestClient.Builder b =
        builder
            .clone()
            .requestFactory(HttpClients.factory(Duration.ofSeconds(15), config.getTimeout()));

    if ("azure".equals(provider)) {
      if (Texts.isBlank(config.getBaseUrl()) || Texts.isBlank(config.getAzureDeployment())) {
        throw new IllegalStateException(
            "Azure OpenAI needs holmes.llm.base-url and holmes.llm.azure-deployment");
      }
      b.baseUrl(config.getBaseUrl()).defaultHeader("api-key", config.getApiKey());
      this.path =
          "/openai/deployments/"
              + config.getAzureDeployment()
              + "/chat/completions?api-version="
              + config.getAzureApiVersion();
    } else {
      b.baseUrl(Texts.isBlank(config.getBaseUrl()) ? defaultBaseUrl(provider) : config.getBaseUrl());
      if (!Texts.isBlank(config.getApiKey())) {
        b.defaultHeader("Authorization", "Bearer " + config.getApiKey());
      }
      this.path = "/chat/completions";
    }
    this.http = b.build();
  }

  private static String defaultBaseUrl(String provider) {
    return switch (provider) {
      case "openai" -> "https://api.openai.com/v1";
      case "openrouter" -> "https://openrouter.ai/api/v1";
      case "gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai";
      case "ollama" -> "http://host.docker.internal:11434/v1";
      // NVIDIA NIM hosted endpoints (build.nvidia.com); free developer keys start with "nvapi-".
      case "nvidia" -> "https://integrate.api.nvidia.com/v1";
      default ->
          throw new IllegalStateException(
              "holmes.llm.base-url is required for provider '" + provider + "'");
    };
  }

  @Override
  public String provider() {
    return provider;
  }

  @Override
  public String defaultModel() {
    return config.getModel();
  }

  @Override
  public int maxTokens() {
    return config.getMaxTokens();
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    if (("nvidia".equals(provider) || "openai".equals(provider) || "openrouter".equals(provider)
            || "gemini".equals(provider) || "azure".equals(provider))
        && Texts.isBlank(config.getApiKey())) {
      throw new LlmException("No API key configured for " + provider + " (Settings → LLM)", null);
    }
    ObjectNode body = json.createObjectNode();
    body.put("model", Texts.firstNonBlank(request.model(), config.getModel()));
    body.put(config.getMaxTokensParam(), request.maxTokens());
    body.put("temperature", config.getTemperature());

    ArrayNode messages = body.putArray("messages");
    if (!Texts.isBlank(request.system())) {
      messages.addObject().put("role", "system").put("content", request.system());
    }
    for (LlmMessage m : request.messages()) {
      switch (m) {
        case LlmMessage.User u -> messages.addObject().put("role", "user").put("content", u.text());
        case LlmMessage.Assistant a -> {
          ObjectNode msg = messages.addObject();
          msg.put("role", "assistant");
          if (Texts.isBlank(a.text())) {
            msg.putNull("content");
          } else {
            msg.put("content", a.text());
          }
          if (!a.toolCalls().isEmpty()) {
            ArrayNode calls = msg.putArray("tool_calls");
            for (ToolCall call : a.toolCalls()) {
              ObjectNode c = calls.addObject();
              c.put("id", call.id());
              c.put("type", "function");
              ObjectNode fn = c.putObject("function");
              fn.put("name", call.name());
              fn.put("arguments", call.arguments() == null ? "{}" : call.arguments().toString());
            }
          }
        }
        case LlmMessage.ToolResult r ->
            messages
                .addObject()
                .put("role", "tool")
                .put("tool_call_id", r.toolCallId())
                .put("content", r.content());
      }
    }

    if (request.tools() != null && !request.tools().isEmpty()) {
      ArrayNode tools = body.putArray("tools");
      for (ToolDefinition t : request.tools()) {
        ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
        fn.put("name", t.name());
        fn.put("description", t.description());
        fn.set("parameters", t.inputSchema());
      }
    }

    JsonNode response =
        RetryingHttp.call(
            provider,
            () ->
                http.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(JsonNode.class));
    return parse(response);
  }

  private LlmResponse parse(JsonNode response) {
    JsonNode choice = response.path("choices").path(0);
    JsonNode message = choice.path("message");
    List<ToolCall> calls = new ArrayList<>();
    for (JsonNode c : message.path("tool_calls")) {
      JsonNode fn = c.path("function");
      calls.add(
          new ToolCall(
              c.path("id").asString(), fn.path("name").asString(), parseArguments(fn.path("arguments"))));
    }
    JsonNode usage = response.path("usage");
    return new LlmResponse(
        message.path("content").isString() ? message.path("content").asString() : "",
        calls,
        choice.path("finish_reason").asString(),
        usage.path("prompt_tokens").asLong(0),
        usage.path("completion_tokens").asLong(0));
  }

  private JsonNode parseArguments(JsonNode raw) {
    if (raw.isObject()) {
      return raw;
    }
    String s = raw.asString();
    if (Texts.isBlank(s)) {
      return json.createObjectNode();
    }
    try {
      return json.readTree(s);
    } catch (JacksonException e) {
      return json.createObjectNode().put("_unparseable_arguments", s);
    }
  }
}

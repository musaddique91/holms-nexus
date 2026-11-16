package com.maverickignite.holmes.llm;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Anthropic Messages API with native tool use. The system prompt and tool list are marked for
 * prompt caching: both are identical across every step of an investigation, so later steps reuse
 * them instead of paying for them again.
 */
class AnthropicClient implements LlmClient {

  private static final String DEFAULT_URL = "https://api.anthropic.com";

  private final HolmesProperties.Llm config;
  private final ObjectMapper json;
  private final RestClient http;

  AnthropicClient(HolmesProperties.Llm config, ObjectMapper json, RestClient.Builder builder) {
    this.config = config;
    this.json = json;
    this.http =
        builder
            .clone()
            .baseUrl(Texts.firstNonBlank(config.getBaseUrl(), DEFAULT_URL))
            .requestFactory(HttpClients.factory(Duration.ofSeconds(15), config.getTimeout()))
            .defaultHeader("x-api-key", Texts.firstNonBlank(config.getApiKey(), ""))
            .defaultHeader("anthropic-version", "2023-06-01")
            .build();
  }

  @Override
  public String provider() {
    return "anthropic";
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
    if (Texts.isBlank(config.getApiKey())) {
      // Checked per call, not at startup: Holmes keeps ingesting and serving the UI without a key.
      throw new LlmException(
          "No LLM API key configured (HOLMES_LLM_API_KEY / Jenkins ANTHROPIC_API_KEY)", null);
    }
    ObjectNode body = json.createObjectNode();
    body.put("model", Texts.firstNonBlank(request.model(), config.getModel()));
    body.put("max_tokens", request.maxTokens());
    body.put("temperature", config.getTemperature());
    if (!Texts.isBlank(request.system())) {
      ObjectNode block = body.putArray("system").addObject();
      block.put("type", "text");
      block.put("text", request.system());
      block.putObject("cache_control").put("type", "ephemeral");
    }
    if (request.tools() != null && !request.tools().isEmpty()) {
      ArrayNode tools = body.putArray("tools");
      for (ToolDefinition t : request.tools()) {
        ObjectNode tool = tools.addObject();
        tool.put("name", t.name());
        tool.put("description", t.description());
        tool.set("input_schema", t.inputSchema());
      }
      ((ObjectNode) tools.get(tools.size() - 1)).putObject("cache_control").put("type", "ephemeral");
    }
    body.set("messages", messages(request.messages()));

    JsonNode response =
        RetryingHttp.call(
            "anthropic",
            () ->
                http.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(JsonNode.class));
    return parse(response);
  }

  private ArrayNode messages(List<LlmMessage> messages) {
    ArrayNode out = json.createArrayNode();
    ArrayNode pendingResults = null;
    for (LlmMessage m : messages) {
      if (m instanceof LlmMessage.ToolResult r) {
        // Anthropic wants all results for one assistant turn inside a single user message.
        if (pendingResults == null) {
          ObjectNode user = out.addObject();
          user.put("role", "user");
          pendingResults = user.putArray("content");
        }
        ObjectNode block = pendingResults.addObject();
        block.put("type", "tool_result");
        block.put("tool_use_id", r.toolCallId());
        block.put("content", r.content());
        if (r.error()) {
          block.put("is_error", true);
        }
        continue;
      }
      if (m instanceof LlmMessage.User u && pendingResults != null) {
        // A nudge right after tool results must share their user turn: roles have to alternate.
        pendingResults.addObject().put("type", "text").put("text", u.text());
        pendingResults = null;
        continue;
      }
      pendingResults = null;
      switch (m) {
        case LlmMessage.User u -> {
          ObjectNode user = out.addObject();
          user.put("role", "user");
          user.put("content", u.text());
        }
        case LlmMessage.Assistant a -> {
          ObjectNode assistant = out.addObject();
          assistant.put("role", "assistant");
          ArrayNode content = assistant.putArray("content");
          if (!Texts.isBlank(a.text())) {
            content.addObject().put("type", "text").put("text", a.text());
          }
          for (ToolCall call : a.toolCalls()) {
            ObjectNode use = content.addObject();
            use.put("type", "tool_use");
            use.put("id", call.id());
            use.put("name", call.name());
            use.set("input", call.arguments() == null ? json.createObjectNode() : call.arguments());
          }
        }
        case LlmMessage.ToolResult ignored -> throw new IllegalStateException("unreachable");
      }
    }
    return out;
  }

  private LlmResponse parse(JsonNode response) {
    StringBuilder text = new StringBuilder();
    List<ToolCall> calls = new ArrayList<>();
    for (JsonNode block : response.path("content")) {
      switch (block.path("type").asString()) {
        case "text" -> text.append(block.path("text").asString());
        case "tool_use" ->
            calls.add(
                new ToolCall(
                    block.path("id").asString(),
                    block.path("name").asString(),
                    block.path("input")));
        default -> {
          // thinking and other block types are not needed by the agent loop
        }
      }
    }
    JsonNode usage = response.path("usage");
    long input =
        usage.path("input_tokens").asLong(0)
            + usage.path("cache_read_input_tokens").asLong(0)
            + usage.path("cache_creation_input_tokens").asLong(0);
    return new LlmResponse(
        text.toString(),
        calls,
        response.path("stop_reason").asString(),
        input,
        usage.path("output_tokens").asLong(0));
  }
}

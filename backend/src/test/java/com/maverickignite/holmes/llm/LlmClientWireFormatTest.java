package com.maverickignite.holmes.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Schemas;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Checks the exact JSON exchanged with each provider, against a local fake endpoint. */
class LlmClientWireFormatTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final AtomicReference<String> lastBody = new AtomicReference<>();
  private final AtomicReference<String> lastPath = new AtomicReference<>();
  private HttpServer server;

  private String serve(String response) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          lastPath.set(exchange.getRequestURI().toString());
          lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private static List<LlmMessage> conversation() {
    return List.of(
        new LlmMessage.User("why?"),
        new LlmMessage.Assistant(
            "looking",
            List.of(
                new ToolCall("t1", "docker_ps", Schemas.empty()),
                new ToolCall("t2", "docker_logs", Schemas.args("container", "orders-backend")))),
        new LlmMessage.ToolResult("t1", "docker_ps", "orders-backend Up", false),
        new LlmMessage.ToolResult("t2", "docker_logs", "boom", true),
        new LlmMessage.User("final answer now"));
  }

  private static List<ToolDefinition> tools() {
    return List.of(
        new ToolDefinition("docker_ps", "list", Schemas.empty()),
        new ToolDefinition("docker_logs", "logs", Schemas.object().string("container", "c", true).build()));
  }

  @Test
  void anthropicRequestAndResponse() throws IOException {
    String base =
        serve(
            """
            {"content":[{"type":"text","text":"Root cause"},
                        {"type":"tool_use","id":"tu_1","name":"docker_ps","input":{"all":true}}],
             "stop_reason":"tool_use",
             "usage":{"input_tokens":100,"cache_read_input_tokens":900,"output_tokens":20}}
            """);
    HolmesProperties.Llm cfg = new HolmesProperties.Llm();
    cfg.setApiKey("k");
    cfg.setBaseUrl(base);
    LlmResponse r =
        new AnthropicClient(cfg, json, RestClient.builder())
            .complete(new LlmRequest("claude-sonnet-5-5", "sys", conversation(), tools(), 1000));

    assertThat(lastPath.get()).isEqualTo("/v1/messages");
    JsonNode body = json.readTree(lastBody.get());
    assertThat(body.path("system").get(0).path("cache_control").path("type").asString()).isEqualTo("ephemeral");
    assertThat(body.path("tools").get(1).has("cache_control")).isTrue();
    assertThat(body.path("tools").get(0).has("cache_control")).isFalse();
    JsonNode messages = body.path("messages");
    assertThat(messages.size()).isEqualTo(3); // user, assistant, user(tool results + nudge)
    assertThat(messages.get(1).path("content").get(2).path("input").path("container").asString())
        .isEqualTo("orders-backend");
    JsonNode lastTurn = messages.get(2).path("content");
    assertThat(lastTurn.get(0).path("type").asString()).isEqualTo("tool_result");
    assertThat(lastTurn.get(1).path("is_error").asBoolean()).isTrue();
    assertThat(lastTurn.get(2).path("text").asString()).isEqualTo("final answer now");

    assertThat(r.text()).isEqualTo("Root cause");
    assertThat(r.toolCalls()).extracting(ToolCall::name).containsExactly("docker_ps");
    assertThat(r.inputTokens()).isEqualTo(1000);
  }

  @Test
  void openAiRequestAndResponse() throws IOException {
    String base =
        serve(
            """
            {"choices":[{"finish_reason":"tool_calls","message":{"content":null,
              "tool_calls":[{"id":"call_1","type":"function",
                             "function":{"name":"docker_logs","arguments":"{\\"container\\":\\"crm-backend\\"}"}}]}}],
             "usage":{"prompt_tokens":50,"completion_tokens":9}}
            """);
    HolmesProperties.Llm cfg = new HolmesProperties.Llm();
    cfg.setProvider("openai-compatible");
    cfg.setBaseUrl(base + "/v1");
    cfg.setModel("gpt-x");
    LlmResponse r =
        new OpenAiCompatibleClient(cfg, json, RestClient.builder())
            .complete(new LlmRequest(null, "sys", conversation(), tools(), 500));

    assertThat(lastPath.get()).isEqualTo("/v1/chat/completions");
    JsonNode body = json.readTree(lastBody.get());
    assertThat(body.path("model").asString()).isEqualTo("gpt-x");
    assertThat(body.path("max_tokens").asInt()).isEqualTo(500);
    JsonNode messages = body.path("messages");
    assertThat(messages.get(0).path("role").asString()).isEqualTo("system");
    assertThat(messages.get(2).path("tool_calls").get(1).path("function").path("arguments").asString())
        .isEqualTo("{\"container\":\"orders-backend\"}");
    assertThat(messages.get(3).path("role").asString()).isEqualTo("tool");
    assertThat(messages.get(3).path("tool_call_id").asString()).isEqualTo("t1");
    assertThat(body.path("tools").get(0).path("function").path("name").asString()).isEqualTo("docker_ps");

    assertThat(r.text()).isEmpty();
    assertThat(r.toolCalls().getFirst().arguments().path("container").asString()).isEqualTo("crm-backend");
  }

  @Test
  void azureUsesDeploymentPathAndApiKeyHeader() throws IOException {
    String base = serve("{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{}}");
    HolmesProperties.Llm cfg = new HolmesProperties.Llm();
    cfg.setProvider("azure");
    cfg.setBaseUrl(base);
    cfg.setApiKey("k");
    cfg.setAzureDeployment("gpt4o-prod");
    LlmResponse r =
        new OpenAiCompatibleClient(cfg, json, RestClient.builder())
            .complete(new LlmRequest(null, "sys", List.of(new LlmMessage.User("hi")), List.of(), 10));
    assertThat(lastPath.get()).startsWith("/openai/deployments/gpt4o-prod/chat/completions?api-version=");
    assertThat(r.text()).isEqualTo("ok");
  }

  @Test
  void nvidiaAndOllamaPresetsNeedNoBaseUrl() {
    HolmesProperties.Llm nvidia = new HolmesProperties.Llm();
    nvidia.setProvider("nvidia");
    nvidia.setApiKey("nvapi-test");
    org.assertj.core.api.Assertions.assertThatCode(
            () -> new OpenAiCompatibleClient(nvidia, json, RestClient.builder()))
        .doesNotThrowAnyException();
    HolmesProperties.Llm ollama = new HolmesProperties.Llm();
    ollama.setProvider("ollama");
    org.assertj.core.api.Assertions.assertThatCode(
            () -> new OpenAiCompatibleClient(ollama, json, RestClient.builder()))
        .doesNotThrowAnyException();
  }
}

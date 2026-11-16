package com.maverickignite.holmes.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.llm.LlmClient;
import com.maverickignite.holmes.llm.LlmMessage;
import com.maverickignite.holmes.llm.LlmRequest;
import com.maverickignite.holmes.llm.LlmResponse;
import com.maverickignite.holmes.llm.ToolCall;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolExecutor;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunnerTest {

  /** Replays scripted responses and records every request it receives. */
  static final class ScriptedLlm implements LlmClient {
    final Deque<LlmResponse> script = new ArrayDeque<>();
    final List<LlmRequest> requests = new ArrayList<>();

    @Override
    public String provider() {
      return "fake";
    }

    @Override
    public String defaultModel() {
      return "fake-model";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
      requests.add(new LlmRequest(request.model(), request.system(), List.copyOf(request.messages()), request.tools(), request.maxTokens()));
      return script.removeFirst();
    }
  }

  private final ToolSpec echo =
      new ToolSpec(
          "echo",
          "echo",
          Schemas.object().string("text", "text", true).build(),
          args -> "echo:" + args.path("text").asString());

  @Test
  void runsToolsThenReturnsFinalAnswer() {
    ScriptedLlm llm = new ScriptedLlm();
    llm.script.add(
        new LlmResponse("checking", List.of(new ToolCall("c1", "echo", Schemas.args("text", "hi"))), "tool_use", 10, 5));
    llm.script.add(new LlmResponse("done", List.of(), "end_turn", 20, 7));
    List<String> seen = new ArrayList<>();

    try (ToolExecutor executor = new ToolExecutor()) {
      AgentRunner runner = new AgentRunner(llm, executor, new HolmesProperties());
      AgentRunner.AgentResult result =
          runner.run(
              new AgentRunner.AgentTask(
                  null, "sys", "investigate", List.of(echo), 5,
                  (seq, tool, args, outcome) -> seen.add(seq + ":" + tool + ":" + outcome.output())));

      assertThat(result.finalText()).isEqualTo("done");
      assertThat(result.steps()).isEqualTo(2);
      assertThat(result.inputTokens()).isEqualTo(30);
      assertThat(seen).containsExactly("1:echo:echo:hi");
      LlmMessage last = llm.requests.get(1).messages().getLast();
      assertThat(last).isEqualTo(new LlmMessage.ToolResult("c1", "echo", "echo:hi", false));
    }
  }

  @Test
  void unknownToolsAndBudgetExhaustionAreHandled() {
    ScriptedLlm llm = new ScriptedLlm();
    llm.script.add(new LlmResponse("", List.of(new ToolCall("c1", "nope", Schemas.empty())), "tool_use", 1, 1));
    llm.script.add(new LlmResponse("final after budget", List.of(), "end_turn", 1, 1));

    try (ToolExecutor executor = new ToolExecutor()) {
      AgentRunner runner = new AgentRunner(llm, executor, new HolmesProperties());
      AgentRunner.AgentResult result =
          runner.run(new AgentRunner.AgentTask(null, "sys", "go", List.of(echo), 1, null));

      assertThat(result.finalText()).isEqualTo("final after budget");
      List<LlmMessage> sent = llm.requests.get(1).messages();
      assertThat(sent.get(sent.size() - 2))
          .isInstanceOfSatisfying(LlmMessage.ToolResult.class, r -> assertThat(r.error()).isTrue());
      assertThat(sent.getLast()).isInstanceOf(LlmMessage.User.class);
    }
  }
}

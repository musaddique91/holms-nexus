package com.maverickignite.holmes.agent;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.llm.LlmClient;
import com.maverickignite.holmes.llm.LlmMessage;
import com.maverickignite.holmes.llm.LlmRequest;
import com.maverickignite.holmes.llm.LlmResponse;
import com.maverickignite.holmes.llm.ToolCall;
import com.maverickignite.holmes.llm.ToolDefinition;
import com.maverickignite.holmes.toolset.ToolExecutor;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The HolmesGPT-style loop: the model reasons, calls tools, reads their output, and repeats until
 * it answers without asking for tools or the step budget runs out. Each tool call is reported to
 * a listener as it happens so the UI can show the investigation live.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunner {

  private static final String BUDGET_EXHAUSTED =
      "You have used your tool budget. Do not call any more tools. Give your final answer now,"
          + " in the required format, based on the evidence gathered so far, and say what you"
          + " could not verify.";

  private final LlmClient llm;
  private final ToolExecutor executor;
  private final HolmesProperties properties;

  @FunctionalInterface
  public interface ToolCallListener {
    void onToolCall(int sequence, String tool, String arguments, ToolExecutor.Outcome outcome);
  }

  public record AgentTask(
      String model,
      String system,
      String prompt,
      List<ToolSpec> tools,
      int maxSteps,
      ToolCallListener listener) {}

  public record AgentResult(
      String finalText, int steps, int toolCalls, long inputTokens, long outputTokens) {}

  public String provider() {
    return llm.provider();
  }

  public String defaultModel() {
    return llm.defaultModel();
  }

  public String fixModel() {
    return llm.fixModel();
  }

  public AgentResult run(AgentTask task) {
    Map<String, ToolSpec> byName = new LinkedHashMap<>();
    task.tools().forEach(t -> byName.put(t.name(), t));
    List<ToolDefinition> definitions = task.tools().stream().map(ToolSpec::definition).toList();
    int maxTokens = llm.maxTokens();
    int maxChars = properties.getInvestigation().getMaxToolOutputChars();

    List<LlmMessage> messages = new ArrayList<>();
    messages.add(new LlmMessage.User(task.prompt()));
    long in = 0;
    long out = 0;
    int calls = 0;

    for (int step = 1; step <= task.maxSteps(); step++) {
      LlmResponse response =
          llm.complete(new LlmRequest(task.model(), task.system(), messages, definitions, maxTokens));
      in += response.inputTokens();
      out += response.outputTokens();
      messages.add(new LlmMessage.Assistant(response.text(), response.toolCalls()));

      if (!response.wantsTools()) {
        return new AgentResult(response.text(), step, calls, in, out);
      }
      for (ToolCall call : response.toolCalls()) {
        calls++;
        ToolSpec spec = byName.get(call.name());
        ToolExecutor.Outcome outcome =
            spec == null
                ? new ToolExecutor.Outcome("Error: unknown tool '" + call.name() + "'", false, 0)
                : executor.execute(spec, call.arguments(), maxChars);
        String args = call.arguments() == null ? "{}" : call.arguments().toString();
        if (task.listener() != null) {
          task.listener().onToolCall(calls, call.name(), args, outcome);
        }
        messages.add(
            new LlmMessage.ToolResult(call.id(), call.name(), outcome.output(), !outcome.success()));
      }
    }

    log.info("Agent step budget ({}) exhausted; requesting final answer", task.maxSteps());
    messages.add(new LlmMessage.User(BUDGET_EXHAUSTED));
    LlmResponse last =
        llm.complete(new LlmRequest(task.model(), task.system(), messages, definitions, maxTokens));
    return new AgentResult(
        last.text(),
        task.maxSteps() + 1,
        calls,
        in + last.inputTokens(),
        out + last.outputTokens());
  }
}

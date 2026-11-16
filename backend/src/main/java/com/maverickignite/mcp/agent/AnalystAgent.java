package com.maverickignite.mcp.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import reactor.core.Disposable;

/** Writes (streams) the user-facing answer from the retrievers' findings. */
@Component
public class AnalystAgent {

    private final AgentSupport support;

    public AnalystAgent(AgentSupport support) {
        this.support = support;
    }

    public Disposable stream(AgentRequest req, String agentId, Plan plan, String findings, boolean visualizerActive,
            Runnable onDone) {
        TurnRecorder turn = req.turn();
        turn.agentStart(agentId, AgentRole.ANALYST, "Analyst",
                plan.steps().isEmpty() ? "Answer the user" : "Synthesize findings into the answer", null, null);
        String chartRule = visualizerActive
                ? "- The chart for this answer is being drawn automatically and appears right after your text. Do NOT "
                        + "output chart/highcharts blocks; briefly refer to \"the chart below\" when useful."
                : "- Add a chart only if the user explicitly asked for one, using the Highcharts format below.\n\n"
                        + support.prompts().get("highcharts");
        String system = req.settings().systemPrompt() + "\n\n"
                + support.prompts().render("analyst", Map.of(
                        "chartRule", chartRule,
                        "presentation", support.prompts().get("presentation")))
                + support.context(req)
                + "\n\n## Retrieved findings\n" + findings;

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.addAll(req.history());
        messages.add(new UserMessage(req.question()));

        long[] chars = {0};
        return support.client(req, null, null, List.of()).prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .subscribe(
                        response -> {
                            String text = AgentSupport.textOf(response);
                            if (text != null && !text.isEmpty()) {
                                chars[0] += text.length();
                                turn.token(text);
                            }
                        },
                        error -> {
                            String msg = AgentSupport.describe(error);
                            turn.agentEnd(agentId, "error", msg, null);
                            turn.fail(msg);
                            onDone.run();
                        },
                        () -> {
                            turn.agentEnd(agentId, "ok", "Wrote " + chars[0] + " characters", null);
                            onDone.run();
                        });
    }
}

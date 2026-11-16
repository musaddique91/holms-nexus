package com.maverickignite.mcp.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/** Fetches data for one plan step using only one server's tools. */
@Component
public class RetrieverAgent {

    /** Result of one retriever run. */
    public record Finding(String agentId, String server, String goal, boolean ok, String report) {
    }

    private final AgentSupport support;

    public RetrieverAgent(AgentSupport support) {
        this.support = support;
    }

    public Finding run(AgentRequest req, String agentId, Plan.Step step, AgentRequest.ServerHandle server) {
        TurnRecorder turn = req.turn();
        turn.agentStart(agentId, AgentRole.RETRIEVER, "Retriever · " + server.server().getName(), step.goal(),
                server.server().getId(), server.server().getName());
        try {
            String system = support.prompts().render("retriever", Map.of(
                    "server", server.server().getName(),
                    "goal", step.goal(),
                    "question", req.question()))
                    + support.context(req);
            List<ToolCallback> tools = support.tools(List.of(server), turn.toolListener(agentId));
            String report = support.client(req, 0.1, null, tools).prompt()
                    .messages(List.of(new SystemMessage(system), new UserMessage(step.goal())))
                    .toolCallbacks(tools)
                    .call()
                    .content();
            report = AgentSupport.stripThinking(report);
            turn.agentEnd(agentId, "ok", firstLine(report), report);
            return new Finding(agentId, server.server().getName(), step.goal(), true, report);
        }
        catch (Exception ex) {
            String msg = AgentSupport.describe(ex);
            turn.agentEnd(agentId, "error", msg, null);
            return new Finding(agentId, server.server().getName(), step.goal(), false, "Retrieval failed: " + msg);
        }
    }

    static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String line = text.strip().split("\\R", 2)[0].replaceAll("^[#>*\\-\\d.\\s]+", "");
        return TurnRecorder.truncate(line, 200);
    }
}

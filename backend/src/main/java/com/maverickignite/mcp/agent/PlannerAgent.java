package com.maverickignite.mcp.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.maverickignite.mcp.common.Json;

import io.modelcontextprotocol.spec.McpSchema;

/** Decides which servers to query, for what, and whether a chart is needed. Works for any MCP server. */
@Component
public class PlannerAgent {

    private static final Pattern CHART_WORDS = Pattern.compile(
            "\\b(chart|graph|plot|visuali[sz]e|trend|pie|donut|bar|histogram|heatmap|3d)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final int CATALOG_CHARS = 14_000;

    private final AgentSupport support;

    public PlannerAgent(AgentSupport support) {
        this.support = support;
    }

    public Plan plan(AgentRequest req) {
        String system = support.prompts().render("planner", Map.of(
                "catalog", catalog(req),
                "workspaceInstructions", req.workspace().getInstructions() == null ? "(none)"
                        : req.workspace().getInstructions(),
                "maxSteps", String.valueOf(Math.max(1, req.settings().maxParallelAgents()))));
        String user = "Conversation so far (most recent last):\n" + transcript(req.history())
                + "\n\nCurrent user message:\n" + req.question();
        try {
            String reply = support.model(req, 0.1, 2000)
                    .call(new Prompt(List.of(new SystemMessage(system), new UserMessage(user))))
                    .getResult().getOutput().getText();
            return parse(reply, req);
        }
        catch (Exception ex) {
            return fallback(req);
        }
    }

    @SuppressWarnings("unchecked")
    private Plan parse(String reply, AgentRequest req) {
        Map<String, Object> raw = Json.readMap(AgentSupport.extractJson(reply));
        List<Plan.Step> steps = new ArrayList<>();
        if (raw.get("steps") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String server = matchServer(String.valueOf(((Map<String, Object>) m).get("server")), req);
                    Object goal = ((Map<String, Object>) m).get("goal");
                    if (server != null && goal != null && !String.valueOf(goal).isBlank()) {
                        steps.add(new Plan.Step(server, String.valueOf(goal)));
                    }
                }
            }
        }
        int max = Math.max(1, req.settings().maxParallelAgents());
        return new Plan(String.valueOf(raw.getOrDefault("intent", "")),
                steps.size() > max ? steps.subList(0, max) : steps,
                Boolean.TRUE.equals(raw.get("visualize")) || CHART_WORDS.matcher(req.question()).find(),
                raw.get("chartHint") == null ? "" : String.valueOf(raw.get("chartHint")),
                raw.get("presentation") == null ? "text" : String.valueOf(raw.get("presentation")), false);
    }

    /** When the planner can't produce valid JSON: ask every attached server about the question. */
    private Plan fallback(AgentRequest req) {
        List<Plan.Step> steps = req.servers().stream()
                .limit(Math.max(1, req.settings().maxParallelAgents()))
                .map(h -> new Plan.Step(h.server().getName(), "Fetch the data needed to answer: " + req.question()))
                .toList();
        return new Plan("Answer the user's question", steps, CHART_WORDS.matcher(req.question()).find(), "", "text",
                true);
    }

    private static String matchServer(String name, AgentRequest req) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (AgentRequest.ServerHandle h : req.servers()) {
            if (h.server().getName().equalsIgnoreCase(n)) {
                return h.server().getName();
            }
        }
        for (AgentRequest.ServerHandle h : req.servers()) {
            String s = h.server().getName().toLowerCase(Locale.ROOT);
            if (s.contains(n) || n.contains(s)) {
                return h.server().getName();
            }
        }
        return null;
    }

    private static String catalog(AgentRequest req) {
        if (req.servers().isEmpty()) {
            return "(no MCP servers are attached — plan zero steps)";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentRequest.ServerHandle h : req.servers()) {
            sb.append("### ").append(h.server().getName()).append('\n');
            if (h.server().getDescription() != null) {
                sb.append(h.server().getDescription()).append('\n');
            }
            for (McpSchema.Tool t : h.connection().tools()) {
                String d = t.description() == null ? "" : t.description().replaceAll("\\s+", " ");
                sb.append("- ").append(t.name()).append(": ").append(TurnRecorder.truncate(d, 180)).append('\n');
            }
            sb.append('\n');
            if (sb.length() > CATALOG_CHARS) {
                sb.append("…(catalog truncated)\n");
                break;
            }
        }
        return sb.toString();
    }

    static String transcript(List<Message> history) {
        if (history.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (Message m : history.subList(Math.max(0, history.size() - 6), history.size())) {
            sb.append(m.getMessageType().getValue()).append(": ")
                    .append(TurnRecorder.truncate(m.getText(), 1200)).append('\n');
        }
        return sb.toString();
    }
}

package com.maverickignite.mcp.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.maverickignite.mcp.common.Json;

/** Produces one Highcharts options object from the retrieved findings. */
@Component
public class VisualizerAgent {

    /** Keys that could carry callbacks / event handlers — never accepted from a model. */
    private static final Set<String> UNSAFE_KEYS = Set.of("events", "formatter", "pointFormatter",
            "labelFormatter", "positioner", "keyboardNavigation", "exporting", "credits");

    private final AgentSupport support;

    public VisualizerAgent(AgentSupport support) {
        this.support = support;
    }

    /** Returns a chart artifact part, or {@code null} when no chart makes sense. */
    public Map<String, Object> run(AgentRequest req, String agentId, Plan plan, String findings) {
        TurnRecorder turn = req.turn();
        turn.agentStart(agentId, AgentRole.VISUALIZER, "Visualizer",
                plan.chartHint() == null || plan.chartHint().isBlank() ? "Pick the best chart for the data"
                        : plan.chartHint(), null, null);
        try {
            String system = support.prompts().render("visualizer", Map.of(
                    "question", req.question(),
                    "chartHint", plan.chartHint() == null ? "" : plan.chartHint(),
                    "findings", findings,
                    "highcharts", support.prompts().get("highcharts")));
            String reply = support.model(req, 0.2, 6000)
                    .call(new Prompt(List.of(new SystemMessage(system), new UserMessage(
                            "Produce the Highcharts options JSON now. Conversation context:\n"
                                    + PlannerAgent.transcript(req.history())))))
                    .getResult().getOutput().getText();
            Map<String, Object> options = sanitize(Json.readMap(AgentSupport.extractJson(reply)));
            if (options.containsKey("skip") || !(options.get("series") instanceof List<?> s) || s.isEmpty()) {
                turn.agentEnd(agentId, "skipped", options.containsKey("skip") ? String.valueOf(options.get("skip"))
                        : "No plottable series", null);
                return null;
            }
            String title = options.get("title") instanceof Map<?, ?> t && t.get("text") != null
                    ? String.valueOf(t.get("text")) : "Chart";
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("type", "chart");
            artifact.put("id", UUID.randomUUID().toString());
            artifact.put("title", title);
            artifact.put("options", options);
            turn.agentEnd(agentId, "ok", chartType(options) + " chart · " + title, null);
            return artifact;
        }
        catch (Exception ex) {
            turn.agentEnd(agentId, "error", AgentSupport.describe(ex), null);
            return null;
        }
    }

    private static String chartType(Map<String, Object> options) {
        Object type = options.get("chart") instanceof Map<?, ?> c ? c.get("type") : null;
        return type == null ? "Line" : capitalize(String.valueOf(type));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> sanitize(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (!UNSAFE_KEYS.contains(k)) {
                out.put(k, sanitizeValue(v));
            }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object v) {
        if (v instanceof Map<?, ?> m) {
            return sanitize((Map<String, Object>) m);
        }
        if (v instanceof List<?> l) {
            return l.stream().map(VisualizerAgent::sanitizeValue).toList();
        }
        return v;
    }
}

package com.maverickignite.mcp.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.maverickignite.mcp.common.Json;
import com.maverickignite.mcp.mcp.McpToolCallback;

/**
 * Records one assistant turn — the agent workflow, tool calls, streamed text and artifacts — as ordered
 * "parts", and mirrors every change to the browser as Server-Sent Events.
 *
 * <p>Parts persisted on the message:
 * <ul>
 * <li>{@code workflow}: plan + agent runs (each with its own tool calls)</li>
 * <li>{@code text}: markdown segments</li>
 * <li>{@code tool}: tool calls made outside any agent (single-agent mode)</li>
 * <li>{@code chart}: Highcharts artifacts produced by the visualizer</li>
 * </ul>
 */
public final class TurnRecorder {

    static final int STORED_RESULT_CHARS = 12_000;

    private final SseEmitter emitter;

    private final List<Map<String, Object>> parts = new ArrayList<>();

    private final Map<String, Map<String, Object>> agents = new LinkedHashMap<>();

    private final Map<String, Map<String, Object>> toolParts = new LinkedHashMap<>();

    private Map<String, Object> workflow;

    private final StringBuilder currentText = new StringBuilder();

    private final StringBuilder fullText = new StringBuilder();

    public final AtomicBoolean finished = new AtomicBoolean();

    private volatile boolean clientGone;

    private volatile String error;

    private volatile boolean cancelled;

    public TurnRecorder(SseEmitter emitter) {
        this.emitter = emitter;
    }

    // ------------------------------------------------------------------ workflow

    private Map<String, Object> workflow() {
        if (workflow == null) {
            workflow = new LinkedHashMap<>();
            workflow.put("type", "workflow");
            workflow.put("agents", new ArrayList<Map<String, Object>>());
            parts.addFirst(workflow);
        }
        return workflow;
    }

    public synchronized void plan(Map<String, Object> plan) {
        workflow().put("plan", plan);
        send("plan", plan);
    }

    @SuppressWarnings("unchecked")
    public void agentStart(String id, AgentRole role, String name, String goal, UUID serverId, String server) {
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("id", id);
        agent.put("role", role.key());
        agent.put("name", name);
        if (goal != null) {
            agent.put("goal", goal);
        }
        if (serverId != null) {
            agent.put("serverId", serverId.toString());
            agent.put("server", server);
        }
        agent.put("status", "running");
        agent.put("startedAt", System.currentTimeMillis());
        agent.put("tools", new ArrayList<Map<String, Object>>());
        synchronized (this) {
            ((List<Map<String, Object>>) workflow().get("agents")).add(agent);
            agents.put(id, agent);
        }
        send("agent_start", agent);
    }

    public void agentEnd(String id, String status, String summary, String output) {
        Map<String, Object> agent = agents.get(id);
        if (agent == null) {
            return;
        }
        long duration;
        synchronized (this) {
            duration = System.currentTimeMillis() - (long) agent.get("startedAt");
            agent.put("status", status);
            agent.put("durationMs", duration);
            if (summary != null) {
                agent.put("summary", summary);
            }
            if (output != null) {
                agent.put("output", truncate(output, STORED_RESULT_CHARS));
            }
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("status", status);
        event.put("durationMs", duration);
        event.put("summary", summary == null ? "" : summary);
        event.put("output", output == null ? "" : truncate(output, STORED_RESULT_CHARS));
        send("agent_end", event);
    }

    /** Tool listener that files calls under an agent (or at top level when {@code agentId} is null). */
    public McpToolCallback.Listener toolListener(String agentId) {
        return new McpToolCallback.Listener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onStart(String callId, UUID serverId, String serverName, String tool,
                    Map<String, Object> args) {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("type", "tool");
                part.put("id", callId);
                part.put("serverId", serverId.toString());
                part.put("server", serverName);
                part.put("tool", tool);
                part.put("args", args);
                part.put("status", "running");
                synchronized (TurnRecorder.this) {
                    Map<String, Object> agent = agentId == null ? null : agents.get(agentId);
                    if (agent != null) {
                        ((List<Map<String, Object>>) agent.get("tools")).add(part);
                    }
                    else {
                        flushText();
                        parts.add(part);
                    }
                    toolParts.put(callId, part);
                }
                Map<String, Object> event = new LinkedHashMap<>(part);
                if (agentId != null) {
                    event.put("agentId", agentId);
                }
                send("tool_start", event);
            }

            @Override
            public void onEnd(String callId, boolean ok, String result, long durationMs) {
                String stored = truncate(result, STORED_RESULT_CHARS);
                synchronized (TurnRecorder.this) {
                    Map<String, Object> part = toolParts.get(callId);
                    if (part != null) {
                        part.put("status", ok ? "ok" : "error");
                        part.put("result", stored);
                        part.put("durationMs", durationMs);
                    }
                }
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("id", callId);
                event.put("status", ok ? "ok" : "error");
                event.put("result", stored == null ? "" : stored);
                event.put("durationMs", durationMs);
                if (agentId != null) {
                    event.put("agentId", agentId);
                }
                send("tool_end", event);
            }
        };
    }

    // ------------------------------------------------------------------ content

    public void token(String text) {
        synchronized (this) {
            currentText.append(text);
            fullText.append(text);
        }
        send("token", Map.of("text", text));
    }

    public void artifact(Map<String, Object> artifact) {
        synchronized (this) {
            flushText();
            parts.add(artifact);
        }
        send("artifact", artifact);
    }

    private void flushText() {
        if (!currentText.isEmpty()) {
            parts.add(new LinkedHashMap<>(Map.of("type", "text", "text", currentText.toString())));
            currentText.setLength(0);
        }
    }

    public synchronized String fullText() {
        return fullText.toString();
    }

    public synchronized List<Map<String, Object>> parts() {
        flushText();
        // Mark anything still running as interrupted so reloaded history never shows spinners.
        for (Map<String, Object> agent : agents.values()) {
            if ("running".equals(agent.get("status"))) {
                agent.put("status", cancelled ? "cancelled" : "error");
            }
        }
        for (Map<String, Object> tool : toolParts.values()) {
            if ("running".equals(tool.get("status"))) {
                tool.put("status", "error");
                tool.put("result", cancelled ? "Cancelled" : "Interrupted");
            }
        }
        return new ArrayList<>(parts);
    }

    // ------------------------------------------------------------------ state

    public void fail(String message) {
        this.error = message;
    }

    public String error() {
        return error;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void send(String event, Object data) {
        if (clientGone) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(Json.write(data)));
            }
        }
        catch (IOException | IllegalStateException ex) {
            clientGone = true;
        }
    }

    /**
     * SSE comment line (ignored by the browser). Keeps the stream from looking idle to the nginx
     * proxies in front of the app while an agent is still thinking.
     */
    public void heartbeat() {
        if (clientGone || finished.get()) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().comment("ping"));
            }
        }
        catch (IOException | IllegalStateException ex) {
            clientGone = true;
        }
    }

    public void complete() {
        try {
            emitter.complete();
        }
        catch (Exception ignored) {
            // client already gone
        }
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

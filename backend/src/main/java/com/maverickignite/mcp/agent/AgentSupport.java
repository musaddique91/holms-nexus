package com.maverickignite.mcp.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.maverickignite.mcp.llm.ChatModelFactory;

import io.micrometer.observation.ObservationRegistry;
import com.maverickignite.mcp.mcp.McpToolCallback;

import io.modelcontextprotocol.spec.McpSchema;

/** Shared helpers for agents: models, MCP tool wiring and common prompt fragments. */
@Component
public class AgentSupport {

    private final ChatModelFactory modelFactory;

    private final McpPrompts prompts;

    public AgentSupport(ChatModelFactory modelFactory, McpPrompts prompts) {
        this.modelFactory = modelFactory;
        this.prompts = prompts;
    }

    public McpPrompts prompts() {
        return prompts;
    }

    public ChatModel model(AgentRequest req, Double temperature, Integer maxTokens) {
        return model(req, temperature, maxTokens, null);
    }

    /** A model that maps near-miss tool names (invented namespaces etc.) back onto {@code tools}. */
    public ChatModel model(AgentRequest req, Double temperature, Integer maxTokens, List<ToolCallback> tools) {
        return modelFactory.create(req.provider(), req.model(),
                temperature != null ? temperature : req.settings().temperature(),
                maxTokens != null ? maxTokens : req.settings().maxTokens(),
                new ForgivingToolResolver(tools == null ? List.of() : tools));
    }

    /**
     * A ChatClient whose tool loop forgives near-miss tool names. Spring AI 2 runs tools through the client's own
     * {@link ToolCallingAdvisor}, so the forgiving resolver must be wired there too - not only into the model.
     */
    public ChatClient client(AgentRequest req, Double temperature, Integer maxTokens, List<ToolCallback> tools) {
        List<ToolCallback> list = tools == null ? List.of() : tools;
        ForgivingToolResolver resolver = new ForgivingToolResolver(list);
        DefaultToolCallingManager manager = DefaultToolCallingManager.builder()
                .toolCallbackResolver(resolver)
                .resolutionFallbackEnabled(true)
                .build();
        return ChatClient.builder(model(req, temperature, maxTokens, list), ObservationRegistry.NOOP, null, null,
                        ToolCallingAdvisor.builder().toolCallingManager(manager))
                .build();
    }

    /** Tool callbacks for the given servers, each reporting into {@code listener}. */
    public List<ToolCallback> tools(List<AgentRequest.ServerHandle> servers, McpToolCallback.Listener listener) {
        List<ToolCallback> tools = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (AgentRequest.ServerHandle h : servers) {
            String prefix = slug(h.server().getName());
            for (McpSchema.Tool tool : h.connection().tools()) {
                tools.add(new McpToolCallback(h.server().getId(), h.server().getName(), tool,
                        uniqueToolName(prefix, tool.name(), used), h.connection().client(), listener));
            }
        }
        return tools;
    }

    /** System-prompt context common to all user-facing agents. */
    public String context(AgentRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nCurrent date/time (UTC): ").append(Instant.now());
        sb.append("\nWorkspace: ").append(req.workspace().getName());
        if (req.workspace().getInstructions() != null) {
            sb.append("\n\n## Workspace instructions\n").append(req.workspace().getInstructions());
        }
        for (AgentRequest.ServerHandle h : req.servers()) {
            String instructions = h.connection().instructions();
            if (instructions != null && !instructions.isBlank()) {
                sb.append("\n\n## Notes from the '").append(h.server().getName()).append("' server\n")
                        .append(TurnRecorder.truncate(instructions, 1500));
            }
        }
        return sb.toString();
    }

    public static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /** Removes reasoning blocks some local models emit inline. */
    public static String stripThinking(String text) {
        return text == null ? "" : text.replaceAll("(?s)<think>.*?</think>", "").strip();
    }

    /** Extracts the outermost JSON object from a model reply (tolerates prose and code fences). */
    public static String extractJson(String text) {
        String t = stripThinking(text);
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No JSON object in model reply");
        }
        return t.substring(start, end + 1);
    }

    public static String describe(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return msg.length() > 600 ? msg.substring(0, 600) + "…" : msg;
    }

    static String uniqueToolName(String prefix, String tool, Set<String> used) {
        String base = (prefix + "__" + tool).replaceAll("[^a-zA-Z0-9_-]", "_");
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        String name = base;
        int i = 2;
        while (!used.add(name)) {
            name = base + "_" + i++;
        }
        return name;
    }

    static String slug(String s) {
        String out = s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return out.length() > 20 ? out.substring(0, 20) : out.isEmpty() ? "mcp" : out;
    }
}

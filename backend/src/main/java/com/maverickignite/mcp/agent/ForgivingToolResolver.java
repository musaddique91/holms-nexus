package com.maverickignite.mcp.agent;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import com.maverickignite.mcp.mcp.McpToolCallback;

/**
 * Consulted only when a model calls a tool name that isn't registered verbatim. Some models invent namespaces
 * (gpt-oss: {@code repo_browser.list_branches}, others {@code functions.x}) or drop our {@code server__} prefix.
 * This maps such names back to the real tool when the match is unambiguous; otherwise it returns a tool that
 * tells the model which names exist (or that the step has no tools), so the turn continues instead of failing.
 */
final class ForgivingToolResolver implements ToolCallbackResolver {

    private static final Logger log = LoggerFactory.getLogger(ForgivingToolResolver.class);

    private final List<ToolCallback> tools;

    ForgivingToolResolver(List<ToolCallback> tools) {
        this.tools = List.copyOf(tools);
    }

    @Override
    public ToolCallback resolve(String requested) {
        String bare = bareName(requested);
        List<ToolCallback> matches = tools.stream()
                .filter(t -> {
                    String exposed = t.getToolDefinition().name().toLowerCase(Locale.ROOT);
                    String original = t instanceof McpToolCallback m ? m.originalToolName().toLowerCase(Locale.ROOT) : "";
                    return exposed.equals(bare) || exposed.endsWith("__" + bare) || original.equals(bare)
                            || original.replaceAll("[^a-z0-9_-]", "_").equals(bare);
                })
                .toList();
        if (tools.isEmpty()) {
            log.warn("Model called tool '{}' in a step that has no tools", requested);
            return unknownTool(requested, List.of());
        }
        if (matches.size() == 1) {
            log.info("Model called unknown tool '{}', resolved to '{}'", requested,
                    matches.getFirst().getToolDefinition().name());
            return matches.getFirst();
        }
        log.warn("Model called unknown tool '{}' ({} candidates)", requested, matches.size());
        return unknownTool(requested, matches.isEmpty() ? tools : matches);
    }

    /** Strips invented namespaces ("repo_browser.", "functions.", "mcp:", "a/b") and normalises case. */
    static String bareName(String name) {
        String n = name == null ? "" : name.trim();
        int cut = Math.max(n.lastIndexOf('.'), Math.max(n.lastIndexOf('/'), n.lastIndexOf(':')));
        return (cut >= 0 ? n.substring(cut + 1) : n).toLowerCase(Locale.ROOT);
    }

    private static ToolCallback unknownTool(String requested, List<ToolCallback> candidates) {
        String names = candidates.stream().map(t -> t.getToolDefinition().name()).collect(Collectors.joining(", "));
        String reply = candidates.isEmpty()
                ? "Error: no tools are available in this step (there is no '" + requested + "'). Do not call tools - "
                        + "write your answer from the data already provided in the conversation."
                : "Error: there is no tool named '" + requested + "'. Call one of these exact tool names instead: "
                        + names;
        ToolDefinition definition = ToolDefinition.builder()
                .name(requested)
                .description("Unknown tool")
                .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return reply;
            }
        };
    }
}

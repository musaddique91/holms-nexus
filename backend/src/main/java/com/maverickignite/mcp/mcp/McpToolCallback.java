package com.maverickignite.mcp.mcp;

import java.util.Map;
import java.util.UUID;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.maverickignite.mcp.common.Json;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Exposes one MCP tool to Spring AI. Unlike the stock callback it reports each invocation to a
 * {@link Listener} (so the UI can render live tool activity) and returns errors to the model as text so
 * it can recover instead of aborting the whole turn.
 */
public class McpToolCallback implements ToolCallback {

    private static final int MAX_RESULT_CHARS = 60_000;

    public interface Listener {

        void onStart(String callId, UUID serverId, String serverName, String tool, Map<String, Object> args);

        void onEnd(String callId, boolean ok, String result, long durationMs);
    }

    private final UUID serverId;

    private final String serverName;

    private final McpSchema.Tool tool;

    private final McpSyncClient client;

    private final Listener listener;

    private final ToolDefinition definition;

    public McpToolCallback(UUID serverId, String serverName, McpSchema.Tool tool, String exposedName,
            McpSyncClient client, Listener listener) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.tool = tool;
        this.client = client;
        this.listener = listener;
        Map<String, Object> schema = tool.inputSchema() == null || tool.inputSchema().isEmpty()
                ? Map.of("type", "object", "properties", Map.of()) : tool.inputSchema();
        String description = tool.description() == null || tool.description().isBlank() ? tool.name()
                : tool.description();
        this.definition = ToolDefinition.builder()
                .name(exposedName)
                .description("[" + serverName + "] " + description)
                .inputSchema(Json.write(schema))
                .build();
    }

    /** The tool's name on its MCP server (before the {@code server__} prefix). */
    public String originalToolName() {
        return tool.name();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        String callId = UUID.randomUUID().toString();
        Map<String, Object> args;
        try {
            args = toolInput == null || toolInput.isBlank() ? Map.of() : Json.readMap(toolInput);
        }
        catch (Exception ex) {
            args = Map.of("_raw", toolInput);
        }
        listener.onStart(callId, serverId, serverName, tool.name(), args);
        long start = System.currentTimeMillis();
        try {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool.name(), args));
            String text = McpResults.toText(result);
            boolean ok = !Boolean.TRUE.equals(result.isError());
            listener.onEnd(callId, ok, text, System.currentTimeMillis() - start);
            String forModel = text.length() > MAX_RESULT_CHARS
                    ? text.substring(0, MAX_RESULT_CHARS) + "\n…[truncated " + (text.length() - MAX_RESULT_CHARS) + " chars]"
                    : text;
            return ok ? forModel : "Tool returned an error: " + forModel;
        }
        catch (Exception ex) {
            String msg = McpConnectionManager.rootMessage(ex);
            listener.onEnd(callId, false, msg, System.currentTimeMillis() - start);
            return "Tool call failed: " + msg;
        }
    }
}

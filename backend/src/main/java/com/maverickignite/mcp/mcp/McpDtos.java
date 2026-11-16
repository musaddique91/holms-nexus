package com.maverickignite.mcp.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API contracts for the MCP module. */
public final class McpDtos {

    private McpDtos() {
    }

    public record ServerRequest(
            @NotBlank @Size(max = 120) String name,
            String description,
            @NotNull TransportType transport,
            String command,
            List<String> args,
            Map<String, String> env,
            String url,
            Map<String, String> headers,
            Boolean enabled,
            String icon,
            String color,
            String catalogId) {
    }

    public record ServerView(
            UUID id,
            String name,
            String description,
            TransportType transport,
            String command,
            List<String> args,
            Map<String, String> env,
            String url,
            Map<String, String> headers,
            boolean enabled,
            String icon,
            String color,
            String catalogId,
            ConnectionStatus status,
            String error,
            int toolCount,
            int resourceCount,
            int promptCount,
            String serverName,
            String serverVersion,
            String instructions,
            Instant connectedAt,
            Instant createdAt) {
    }

    public record ToolView(String name, String title, String description, Map<String, Object> inputSchema,
            boolean readOnly, boolean destructive) {
    }

    public record ResourceView(String uri, String name, String title, String description, String mimeType) {
    }

    public record PromptArgView(String name, String description, boolean required) {
    }

    public record PromptView(String name, String title, String description, List<PromptArgView> arguments) {
    }

    public record CallToolRequest(@NotBlank String tool, Map<String, Object> arguments) {
    }

    public record CallToolResponse(boolean error, String text, Object structured, long durationMs) {
    }

    public record ReadResourceRequest(@NotBlank String uri) {
    }

    public record ResourceContentView(String uri, String mimeType, String text, boolean binary) {
    }

    public record GetPromptRequest(@NotBlank String name, Map<String, Object> arguments) {
    }

    public record PromptMessageView(String role, String text) {
    }

    public record LogLine(Instant at, String level, String message) {
    }

    public record InstallRequest(@NotBlank String catalogId, String name, Map<String, String> values) {
    }

    public record ImportRequest(@NotBlank String json) {
    }

    public record ImportResult(List<ServerView> created, List<String> skipped) {
    }

    /** One server that the chat can use, with its live tools (for the composer & suggestions). */
    public record ToolsetView(UUID serverId, String serverName, String icon, String color, ConnectionStatus status,
            List<ToolView> tools) {
    }
}

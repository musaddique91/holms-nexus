package com.maverickignite.mcp.mcp;

import java.util.stream.Collectors;

import com.maverickignite.mcp.common.Json;

import io.modelcontextprotocol.spec.McpSchema;

/** Flattens MCP content blocks into text the LLM and UI can consume. */
public final class McpResults {

    private McpResults() {
    }

    public static String toText(McpSchema.CallToolResult result) {
        String text = result.content() == null ? ""
                : result.content().stream().map(McpResults::contentToText).collect(Collectors.joining("\n"));
        if (text.isBlank() && result.structuredContent() != null) {
            text = Json.write(result.structuredContent());
        }
        return text;
    }

    public static String contentToText(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent t) {
            return t.text();
        }
        if (content instanceof McpSchema.ImageContent i) {
            return "[image " + i.mimeType() + ", " + (i.data() == null ? 0 : i.data().length()) + " bytes base64]";
        }
        if (content instanceof McpSchema.AudioContent a) {
            return "[audio " + a.mimeType() + "]";
        }
        if (content instanceof McpSchema.EmbeddedResource r) {
            if (r.resource() instanceof McpSchema.TextResourceContents t) {
                return t.text();
            }
            return "[resource " + r.resource().uri() + "]";
        }
        if (content instanceof McpSchema.ResourceLink l) {
            return "[resource link: " + l.uri() + (l.description() == null ? "" : " — " + l.description()) + "]";
        }
        return String.valueOf(content);
    }
}

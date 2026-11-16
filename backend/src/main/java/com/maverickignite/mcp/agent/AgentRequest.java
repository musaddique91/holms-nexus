package com.maverickignite.mcp.agent;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

import com.maverickignite.mcp.llm.LlmProviderEntity;
import com.maverickignite.mcp.mcp.McpConnectionManager;
import com.maverickignite.mcp.mcp.McpServerEntity;
import com.maverickignite.mcp.settings.SettingsService.AppSettings;
import com.maverickignite.mcp.workspace.WorkspaceEntity;

/**
 * Everything an agent run needs for one user turn.
 *
 * @param question the user's message for this turn
 * @param history prior conversation turns (oldest first), excluding {@code question}
 * @param servers connected MCP servers the user enabled for this chat
 */
public record AgentRequest(
        String question,
        List<Message> history,
        List<ServerHandle> servers,
        WorkspaceEntity workspace,
        AppSettings settings,
        LlmProviderEntity provider,
        String model,
        TurnRecorder turn) {

    /** A configured server together with its live connection. */
    public record ServerHandle(McpServerEntity server, McpConnectionManager.Connection connection) {
    }
}

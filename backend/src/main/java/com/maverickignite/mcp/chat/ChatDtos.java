package com.maverickignite.mcp.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record CreateConversation(String title, UUID workspaceId, UUID providerId, String model,
            List<UUID> serverIds) {
    }

    public record UpdateConversation(String title, Boolean pinned, UUID workspaceId, UUID providerId, String model,
            List<UUID> serverIds) {
    }

    public record ConversationSummary(UUID id, String title, boolean pinned, UUID workspaceId, UUID providerId,
            String model,
            List<UUID> serverIds, Instant createdAt, Instant updatedAt) {
    }

    public record MessageView(UUID id, String role, String content, List<Map<String, Object>> parts, String model,
            Long durationMs, boolean error, Instant createdAt) {
    }

    public record ConversationDetail(ConversationSummary conversation, List<MessageView> messages) {
    }

    public record SendMessage(@NotBlank String content, UUID workspaceId, UUID providerId, String model,
            List<UUID> serverIds,
            Boolean regenerate) {
    }
}

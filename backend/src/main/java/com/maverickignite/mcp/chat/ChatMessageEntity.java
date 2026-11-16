package com.maverickignite.mcp.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.maverickignite.mcp.common.JsonConverters;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    /** user | assistant */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String content = "";

    /** Ordered text / tool-call segments of an assistant turn. */
    @Convert(converter = JsonConverters.ObjectList.class)
    private List<Map<String, Object>> parts = new ArrayList<>();

    private String model;

    private Long durationMs;

    private boolean error;

    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content == null ? "" : content; }
    public List<Map<String, Object>> getParts() { return parts; }
    public void setParts(List<Map<String, Object>> parts) { this.parts = parts == null ? new ArrayList<>() : new ArrayList<>(parts); }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public boolean isError() { return error; }
    public void setError(boolean error) { this.error = error; }
    public Instant getCreatedAt() { return createdAt; }
}

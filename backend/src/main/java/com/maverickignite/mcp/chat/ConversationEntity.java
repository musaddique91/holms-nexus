package com.maverickignite.mcp.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.maverickignite.mcp.common.JsonConverters;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation")
public class ConversationEntity {

    public static final String DEFAULT_TITLE = "New chat";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title = DEFAULT_TITLE;

    private UUID providerId;

    private UUID workspaceId;

    private String model;

    @Convert(converter = JsonConverters.UuidList.class)
    private List<UUID> serverIds = new ArrayList<>();

    private boolean pinned;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getProviderId() { return providerId; }
    public void setProviderId(UUID providerId) { this.providerId = providerId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<UUID> getServerIds() { return serverIds; }
    public void setServerIds(List<UUID> serverIds) { this.serverIds = serverIds == null ? new ArrayList<>() : new ArrayList<>(serverIds); }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

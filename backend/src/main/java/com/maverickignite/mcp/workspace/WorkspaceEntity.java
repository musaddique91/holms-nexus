package com.maverickignite.mcp.workspace;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A named set of MCP servers (plus instructions) that chats run against. */
@Entity
@Table(name = "workspace")
public class WorkspaceEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    private String icon;

    private String color;

    /** When true the workspace always includes every enabled server, including ones added later. */
    private boolean allServers;

    @Convert(converter = JsonConverters.UuidList.class)
    private List<UUID> serverIds = new ArrayList<>();

    private String instructions;

    @Column(name = "is_default")
    private boolean defaultWorkspace;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isAllServers() { return allServers; }
    public void setAllServers(boolean allServers) { this.allServers = allServers; }
    public List<UUID> getServerIds() { return serverIds; }
    public void setServerIds(List<UUID> serverIds) { this.serverIds = serverIds == null ? new ArrayList<>() : new ArrayList<>(serverIds); }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public boolean isDefaultWorkspace() { return defaultWorkspace; }
    public void setDefaultWorkspace(boolean defaultWorkspace) { this.defaultWorkspace = defaultWorkspace; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

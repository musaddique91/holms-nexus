package com.maverickignite.mcp.workspace;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.maverickignite.mcp.common.ApiException;
import com.maverickignite.mcp.mcp.McpServerEntity;
import com.maverickignite.mcp.mcp.McpServerRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Service
public class WorkspaceService {

    public record WorkspaceRequest(@NotBlank String name, String description, String icon, String color,
            Boolean allServers, List<UUID> serverIds, String instructions) {
    }

    public record WorkspaceView(UUID id, String name, String description, String icon, String color,
            boolean allServers, List<UUID> serverIds, String instructions, boolean isDefault) {
    }

    private final WorkspaceRepository repository;

    private final McpServerRepository servers;

    public WorkspaceService(WorkspaceRepository repository, McpServerRepository servers) {
        this.repository = repository;
        this.servers = servers;
    }

    /** Guarantees a default "All servers" workspace exists. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    @Transactional
    public void ensureDefault() {
        if (repository.findFirstByDefaultWorkspaceTrue().isEmpty()) {
            WorkspaceEntity w = new WorkspaceEntity();
            w.setName("All servers");
            w.setDescription("Every connected MCP server");
            w.setIcon("layers");
            w.setColor("#e3150e");
            w.setAllServers(true);
            w.setDefaultWorkspace(true);
            repository.save(w);
        }
    }

    @Transactional(readOnly = true)
    public List<WorkspaceView> list() {
        return repository.findAllByOrderByDefaultWorkspaceDescCreatedAtAsc().stream().map(WorkspaceService::view)
                .toList();
    }

    public WorkspaceEntity require(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Workspace"));
    }

    @Transactional(readOnly = true)
    public WorkspaceEntity resolve(UUID id) {
        if (id != null) {
            return repository.findById(id).orElseGet(this::defaultWorkspace);
        }
        return defaultWorkspace();
    }

    private WorkspaceEntity defaultWorkspace() {
        return repository.findFirstByDefaultWorkspaceTrue()
                .orElseThrow(() -> ApiException.notFound("Default workspace"));
    }

    /** Enabled servers that belong to the workspace. */
    @Transactional(readOnly = true)
    public List<McpServerEntity> serversOf(WorkspaceEntity w) {
        return servers.findAllByEnabledTrue().stream()
                .filter(s -> w.isAllServers() || w.getServerIds().contains(s.getId()))
                .toList();
    }

    @Transactional
    public WorkspaceView create(WorkspaceRequest req) {
        if (repository.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.badRequest("A workspace named '" + req.name() + "' already exists");
        }
        WorkspaceEntity w = new WorkspaceEntity();
        apply(w, req);
        return view(repository.save(w));
    }

    @Transactional
    public WorkspaceView update(UUID id, WorkspaceRequest req) {
        WorkspaceEntity w = require(id);
        apply(w, req);
        return view(repository.save(w));
    }

    @Transactional
    public void delete(UUID id) {
        WorkspaceEntity w = require(id);
        if (w.isDefaultWorkspace()) {
            throw ApiException.badRequest("The default workspace cannot be deleted");
        }
        repository.delete(w);
    }

    private static void apply(WorkspaceEntity w, WorkspaceRequest req) {
        w.setName(req.name().trim());
        w.setDescription(req.description());
        w.setIcon(req.icon());
        w.setColor(req.color());
        w.setAllServers(Boolean.TRUE.equals(req.allServers()));
        w.setServerIds(req.serverIds());
        w.setInstructions(req.instructions() == null || req.instructions().isBlank() ? null : req.instructions());
    }

    static WorkspaceView view(WorkspaceEntity w) {
        return new WorkspaceView(w.getId(), w.getName(), w.getDescription(), w.getIcon(), w.getColor(),
                w.isAllServers(), w.getServerIds(), w.getInstructions(), w.isDefaultWorkspace());
    }

    @RestController
    @RequestMapping("/api/workspaces")
    static class WorkspaceController {

        private final WorkspaceService service;

        WorkspaceController(WorkspaceService service) {
            this.service = service;
        }

        @GetMapping
        List<WorkspaceView> list() {
            return service.list();
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        WorkspaceView create(@Valid @RequestBody WorkspaceRequest req) {
            return service.create(req);
        }

        @PutMapping("/{id}")
        WorkspaceView update(@PathVariable UUID id, @Valid @RequestBody WorkspaceRequest req) {
            return service.update(id, req);
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void delete(@PathVariable UUID id) {
            service.delete(id);
        }
    }
}

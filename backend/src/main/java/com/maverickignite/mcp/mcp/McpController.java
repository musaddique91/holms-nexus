package com.maverickignite.mcp.mcp;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.maverickignite.mcp.mcp.McpDtos.CallToolRequest;
import com.maverickignite.mcp.mcp.McpDtos.CallToolResponse;
import com.maverickignite.mcp.mcp.McpDtos.GetPromptRequest;
import com.maverickignite.mcp.mcp.McpDtos.ImportRequest;
import com.maverickignite.mcp.mcp.McpDtos.ImportResult;
import com.maverickignite.mcp.mcp.McpDtos.InstallRequest;
import com.maverickignite.mcp.mcp.McpDtos.LogLine;
import com.maverickignite.mcp.mcp.McpDtos.PromptMessageView;
import com.maverickignite.mcp.mcp.McpDtos.PromptView;
import com.maverickignite.mcp.mcp.McpDtos.ReadResourceRequest;
import com.maverickignite.mcp.mcp.McpDtos.ResourceContentView;
import com.maverickignite.mcp.mcp.McpDtos.ResourceView;
import com.maverickignite.mcp.mcp.McpDtos.ServerRequest;
import com.maverickignite.mcp.mcp.McpDtos.ServerView;
import com.maverickignite.mcp.mcp.McpDtos.ToolView;
import com.maverickignite.mcp.mcp.McpDtos.ToolsetView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpServerService service;

    private final McpCatalog catalog;

    private final McpRegistryService registry;

    public McpController(McpServerService service, McpCatalog catalog, McpRegistryService registry) {
        this.service = service;
        this.catalog = catalog;
        this.registry = registry;
    }

    @GetMapping("/registry")
    McpRegistryService.SearchResult registrySearch(@org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String cursor) {
        return registry.search(q, cursor);
    }

    @PostMapping("/registry/install")
    @ResponseStatus(HttpStatus.CREATED)
    ServerView registryInstall(@RequestBody McpRegistryService.InstallRequest req) {
        return service.installFromRegistry(registry.toServerRequest(req));
    }

    @GetMapping("/servers")
    List<ServerView> list() {
        return service.list();
    }

    @GetMapping("/servers/{id}")
    ServerView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/servers")
    @ResponseStatus(HttpStatus.CREATED)
    ServerView create(@Valid @RequestBody ServerRequest req) {
        return service.create(req);
    }

    @PutMapping("/servers/{id}")
    ServerView update(@PathVariable UUID id, @Valid @RequestBody ServerRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/servers/{id}/enabled")
    ServerView setEnabled(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return service.setEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
    }

    @DeleteMapping("/servers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/servers/{id}/reconnect")
    ServerView reconnect(@PathVariable UUID id) {
        return service.reconnect(id);
    }

    @PostMapping("/servers/test")
    ServerView test(@Valid @RequestBody ServerRequest req) {
        return service.test(req);
    }

    @GetMapping("/servers/{id}/tools")
    List<ToolView> tools(@PathVariable UUID id) {
        return service.tools(id);
    }

    @PostMapping("/servers/{id}/tools/call")
    CallToolResponse callTool(@PathVariable UUID id, @Valid @RequestBody CallToolRequest req) {
        return service.callTool(id, req);
    }

    @GetMapping("/servers/{id}/resources")
    List<ResourceView> resources(@PathVariable UUID id) {
        return service.resources(id);
    }

    @PostMapping("/servers/{id}/resources/read")
    List<ResourceContentView> readResource(@PathVariable UUID id, @Valid @RequestBody ReadResourceRequest req) {
        return service.readResource(id, req.uri());
    }

    @GetMapping("/servers/{id}/prompts")
    List<PromptView> prompts(@PathVariable UUID id) {
        return service.prompts(id);
    }

    @PostMapping("/servers/{id}/prompts/get")
    List<PromptMessageView> getPrompt(@PathVariable UUID id, @Valid @RequestBody GetPromptRequest req) {
        return service.getPrompt(id, req);
    }

    @GetMapping("/servers/{id}/logs")
    List<LogLine> logs(@PathVariable UUID id) {
        return service.logs(id);
    }

    @GetMapping("/catalog")
    List<McpCatalog.Entry> catalog() {
        return catalog.all();
    }

    @PostMapping("/catalog/install")
    @ResponseStatus(HttpStatus.CREATED)
    ServerView install(@Valid @RequestBody InstallRequest req) {
        return service.install(req);
    }

    @PostMapping("/import")
    ImportResult importJson(@Valid @RequestBody ImportRequest req) {
        return service.importJson(req.json());
    }

    @GetMapping("/export")
    Map<String, Object> exportJson() {
        return service.exportJson();
    }

    @GetMapping("/toolsets")
    List<ToolsetView> toolsets() {
        return service.toolsets();
    }
}

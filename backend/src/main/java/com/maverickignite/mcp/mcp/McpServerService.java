package com.maverickignite.mcp.mcp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.maverickignite.mcp.common.ApiException;
import com.maverickignite.mcp.common.Json;
import com.maverickignite.mcp.mcp.McpConnectionManager.Connection;
import com.maverickignite.mcp.mcp.McpDtos.CallToolResponse;
import com.maverickignite.mcp.mcp.McpDtos.ImportResult;
import com.maverickignite.mcp.mcp.McpDtos.PromptArgView;
import com.maverickignite.mcp.mcp.McpDtos.PromptMessageView;
import com.maverickignite.mcp.mcp.McpDtos.PromptView;
import com.maverickignite.mcp.mcp.McpDtos.ResourceContentView;
import com.maverickignite.mcp.mcp.McpDtos.ResourceView;
import com.maverickignite.mcp.mcp.McpDtos.ServerRequest;
import com.maverickignite.mcp.mcp.McpDtos.ServerView;
import com.maverickignite.mcp.mcp.McpDtos.ToolView;
import com.maverickignite.mcp.mcp.McpDtos.ToolsetView;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

@Service
public class McpServerService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final McpServerRepository repository;

    private final McpConnectionManager connections;

    private final McpCatalog catalog;

    private final ToolCatalogCache toolCache;

    public McpServerService(McpServerRepository repository, McpConnectionManager connections, McpCatalog catalog,
            ToolCatalogCache toolCache) {
        this.repository = repository;
        this.connections = connections;
        this.catalog = catalog;
        this.toolCache = toolCache;
    }

    // ---------------------------------------------------------------- CRUD

    @Transactional(readOnly = true)
    public List<ServerView> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ServerView get(UUID id) {
        return toView(require(id));
    }

    public McpServerEntity require(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("MCP server"));
    }

    @Transactional
    public ServerView create(ServerRequest req) {
        if (repository.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.badRequest("A server named '" + req.name() + "' already exists");
        }
        McpServerEntity entity = new McpServerEntity();
        apply(entity, req);
        repository.saveAndFlush(entity);
        if (entity.isEnabled()) {
            connections.connectAsync(entity);
        }
        return toView(entity);
    }

    @Transactional
    public ServerView update(UUID id, ServerRequest req) {
        McpServerEntity entity = require(id);
        apply(entity, req);
        repository.saveAndFlush(entity);
        if (entity.isEnabled()) {
            connections.connectAsync(entity);
        }
        else {
            connections.disconnect(id);
        }
        return toView(entity);
    }

    @Transactional
    public ServerView setEnabled(UUID id, boolean enabled) {
        McpServerEntity entity = require(id);
        entity.setEnabled(enabled);
        repository.saveAndFlush(entity);
        if (enabled) {
            connections.connectAsync(entity);
        }
        else {
            connections.disconnect(id);
        }
        return toView(entity);
    }

    @Transactional
    public void delete(UUID id) {
        McpServerEntity entity = require(id);
        connections.remove(id);
        repository.delete(entity);
    }

    /** Synchronous reconnect, so the caller gets the outcome immediately. */
    @Transactional(readOnly = true)
    public ServerView reconnect(UUID id) {
        McpServerEntity entity = require(id);
        connections.connect(entity);
        return toView(entity);
    }

    /** Connects to a not-yet-saved configuration to validate it, then tears it down. */
    public ServerView test(ServerRequest req) {
        McpServerEntity probe = new McpServerEntity();
        apply(probe, req);
        probe.setId(UUID.randomUUID());
        try {
            Connection conn = connections.connect(probe);
            return toView(probe, conn);
        }
        finally {
            connections.remove(probe.getId());
        }
    }

    private void apply(McpServerEntity e, ServerRequest req) {
        e.setName(req.name().trim());
        e.setDescription(req.description());
        e.setTransport(req.transport());
        e.setCommand(req.command() == null ? null : req.command().trim());
        e.setArgs(req.args() == null ? List.of() : req.args().stream().filter(a -> a != null && !a.isBlank()).toList());
        e.setEnv(cleanMap(req.env()));
        e.setUrl(req.url() == null ? null : req.url().trim());
        e.setHeaders(cleanMap(req.headers()));
        e.setEnabled(req.enabled() == null || req.enabled());
        e.setIcon(req.icon());
        e.setColor(req.color());
        e.setCatalogId(req.catalogId());
    }

    private static Map<String, String> cleanMap(Map<String, String> map) {
        Map<String, String> out = new LinkedHashMap<>();
        if (map != null) {
            map.forEach((k, v) -> {
                if (k != null && !k.isBlank()) {
                    out.put(k.trim(), v == null ? "" : v);
                }
            });
        }
        return out;
    }

    // ---------------------------------------------------------------- catalog / import / export

    @Transactional
    public ServerView install(McpDtos.InstallRequest req) {
        McpCatalog.Entry entry = catalog.find(req.catalogId())
                .orElseThrow(() -> ApiException.notFound("Catalog entry"));
        Map<String, String> values = req.values() == null ? Map.of() : req.values();
        for (McpCatalog.Param param : entry.params()) {
            if (param.required() && (values.get(param.key()) == null || values.get(param.key()).isBlank())) {
                throw ApiException.badRequest(param.label() + " is required");
            }
        }
        String name = req.name() == null || req.name().isBlank() ? entry.name() : req.name().trim();
        name = uniqueName(name);
        Map<String, String> env = new LinkedHashMap<>();
        entry.env().forEach((k, v) -> putIfFilled(env, k, fill(v, values)));
        Map<String, String> headers = new LinkedHashMap<>();
        entry.headers().forEach((k, v) -> putIfFilled(headers, k, fill(v, values)));
        ServerRequest request = new ServerRequest(name, entry.description(), entry.transport(), entry.command(),
                entry.args().stream().map(a -> fill(a, values)).toList(), env, fill(entry.url(), values), headers,
                true, entry.icon(), entry.color(), entry.id());
        return create(request);
    }

    /** Optional params left blank drop the env var / header they feed instead of sending an empty value. */
    private static void putIfFilled(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    @Transactional
    public ServerView installFromRegistry(ServerRequest req) {
        String name = uniqueName(req.name());
        return create(new ServerRequest(name, req.description(), req.transport(), req.command(), req.args(), req.env(),
                req.url(), req.headers(), true, req.icon(), req.color(), req.catalogId()));
    }

    private String uniqueName(String base) {
        String name = base;
        int i = 2;
        while (repository.existsByNameIgnoreCase(name)) {
            name = base + " " + i++;
        }
        return name;
    }

    private static String fill(String template, Map<String, String> values) {
        if (template == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(values.getOrDefault(m.group(1), "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Imports the {@code mcpServers} block used by Claude Desktop, Cursor, VS Code and friends. */
    @Transactional
    @SuppressWarnings("unchecked")
    public ImportResult importJson(String json) {
        Map<String, Object> root;
        try {
            root = Json.readMap(json);
        }
        catch (Exception ex) {
            throw ApiException.badRequest("Invalid JSON: " + McpConnectionManager.rootMessage(ex));
        }
        Object block = root.containsKey("mcpServers") ? root.get("mcpServers")
                : root.containsKey("servers") ? root.get("servers") : root;
        if (!(block instanceof Map<?, ?> servers) || servers.isEmpty()) {
            throw ApiException.badRequest("Expected an object like { \"mcpServers\": { \"name\": { … } } }");
        }
        List<ServerView> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<?, ?> e : servers.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> raw)) {
                skipped.add(name + " (not an object)");
                continue;
            }
            Map<String, Object> cfg = (Map<String, Object>) raw;
            if (repository.existsByNameIgnoreCase(name)) {
                skipped.add(name + " (already exists)");
                continue;
            }
            String url = (String) cfg.get("url");
            String type = String.valueOf(cfg.getOrDefault("type", cfg.getOrDefault("transport", ""))).toLowerCase();
            TransportType transport = url == null ? TransportType.STDIO
                    : type.contains("sse") ? TransportType.SSE : TransportType.STREAMABLE_HTTP;
            List<String> args = cfg.get("args") instanceof List<?> l ? l.stream().map(String::valueOf).toList()
                    : List.of();
            Map<String, String> env = stringMap(cfg.get("env"));
            Map<String, String> headers = stringMap(cfg.get("headers"));
            boolean enabled = !Boolean.TRUE.equals(cfg.get("disabled"));
            try {
                created.add(create(new ServerRequest(name, null, transport, (String) cfg.get("command"), args, env, url,
                        headers, enabled, null, null, null)));
            }
            catch (ApiException ex) {
                skipped.add(name + " (" + ex.getMessage() + ")");
            }
        }
        return new ImportResult(created, skipped);
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportJson() {
        Map<String, Object> servers = new LinkedHashMap<>();
        for (McpServerEntity s : repository.findAllByOrderByCreatedAtAsc()) {
            Map<String, Object> cfg = new LinkedHashMap<>();
            if (s.getTransport() == TransportType.STDIO) {
                cfg.put("command", s.getCommand());
                cfg.put("args", s.getArgs());
                if (!s.getEnv().isEmpty()) {
                    cfg.put("env", s.getEnv());
                }
            }
            else {
                cfg.put("type", s.getTransport() == TransportType.SSE ? "sse" : "http");
                cfg.put("url", s.getUrl());
                if (!s.getHeaders().isEmpty()) {
                    cfg.put("headers", s.getHeaders());
                }
            }
            if (!s.isEnabled()) {
                cfg.put("disabled", true);
            }
            servers.put(s.getName(), cfg);
        }
        return Map.of("mcpServers", servers);
    }

    // ---------------------------------------------------------------- capabilities

    public List<ToolView> tools(UUID id) {
        require(id);
        Connection conn = connections.state(id);
        List<McpSchema.Tool> tools = conn.status() == ConnectionStatus.CONNECTED ? conn.tools() : toolCache.get(id);
        return tools.stream().map(McpServerService::toToolView).toList();
    }

    public List<ResourceView> resources(UUID id) {
        require(id);
        return connections.state(id).resources().stream()
                .map(r -> new ResourceView(r.uri(), r.name(), r.title(), r.description(), r.mimeType()))
                .toList();
    }

    public List<PromptView> prompts(UUID id) {
        require(id);
        return connections.state(id).prompts().stream()
                .map(p -> new PromptView(p.name(), p.title(), p.description(),
                        p.arguments() == null ? List.of()
                                : p.arguments().stream()
                                        .map(a -> new PromptArgView(a.name(), a.description(),
                                                Boolean.TRUE.equals(a.required())))
                                        .toList()))
                .toList();
    }

    public List<McpDtos.LogLine> logs(UUID id) {
        require(id);
        return connections.state(id).logs();
    }

    public CallToolResponse callTool(UUID id, McpDtos.CallToolRequest req) {
        require(id);
        McpSyncClient client = requireConnected(id);
        long start = System.currentTimeMillis();
        try {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(req.tool(),
                    req.arguments() == null ? Map.of() : req.arguments()));
            return new CallToolResponse(Boolean.TRUE.equals(result.isError()), McpResults.toText(result),
                    result.structuredContent(), System.currentTimeMillis() - start);
        }
        catch (Exception ex) {
            return new CallToolResponse(true, McpConnectionManager.rootMessage(ex), null,
                    System.currentTimeMillis() - start);
        }
    }

    public List<ResourceContentView> readResource(UUID id, String uri) {
        require(id);
        McpSchema.ReadResourceResult result = requireConnected(id)
                .readResource(new McpSchema.ReadResourceRequest(uri));
        return result.contents().stream().map(c -> {
            if (c instanceof McpSchema.TextResourceContents t) {
                return new ResourceContentView(t.uri(), t.mimeType(), t.text(), false);
            }
            if (c instanceof McpSchema.BlobResourceContents b) {
                return new ResourceContentView(b.uri(), b.mimeType(), b.blob(), true);
            }
            return new ResourceContentView(c.uri(), c.mimeType(), String.valueOf(c), false);
        }).toList();
    }

    public List<PromptMessageView> getPrompt(UUID id, McpDtos.GetPromptRequest req) {
        require(id);
        McpSchema.GetPromptResult result = requireConnected(id).getPrompt(new McpSchema.GetPromptRequest(req.name(),
                req.arguments() == null ? Map.of() : req.arguments()));
        return result.messages().stream()
                .map(m -> new PromptMessageView(m.role().name().toLowerCase(), McpResults.contentToText(m.content())))
                .toList();
    }

    private McpSyncClient requireConnected(UUID id) {
        try {
            return connections.requireClient(id);
        }
        catch (IllegalStateException ex) {
            throw ApiException.badRequest("Server is not connected");
        }
    }

    /** Servers usable in chat, with live tools. */
    @Transactional(readOnly = true)
    public List<ToolsetView> toolsets() {
        return repository.findAllByEnabledTrue().stream()
                .sorted(Comparator.comparing(McpServerEntity::getCreatedAt))
                .map(s -> {
                    Connection conn = connections.state(s.getId());
                    return new ToolsetView(s.getId(), s.getName(), s.getIcon(), s.getColor(), conn.status(),
                            conn.tools().stream().map(McpServerService::toToolView).toList());
                })
                .toList();
    }

    // ---------------------------------------------------------------- mapping

    private ServerView toView(McpServerEntity e) {
        return toView(e, connections.state(e.getId()));
    }

    private ServerView toView(McpServerEntity e, Connection c) {
        boolean live = c.status() == ConnectionStatus.CONNECTED;
        int toolCount = live ? c.tools().size() : toolCache.get(e.getId()).size();
        ConnectionStatus status = !e.isEnabled() && c.status() != ConnectionStatus.CONNECTING
                ? ConnectionStatus.DISCONNECTED : c.status();
        return new ServerView(e.getId(), e.getName(), e.getDescription(), e.getTransport(), e.getCommand(),
                e.getArgs(), e.getEnv(), e.getUrl(), e.getHeaders(), e.isEnabled(), e.getIcon(), e.getColor(),
                e.getCatalogId(), status, c.error(), toolCount, c.resources().size(), c.prompts().size(),
                c.serverInfo() == null ? null : c.serverInfo().name(),
                c.serverInfo() == null ? null : c.serverInfo().version(), c.instructions(), c.connectedAt(),
                e.getCreatedAt());
    }

    static ToolView toToolView(McpSchema.Tool t) {
        McpSchema.ToolAnnotations a = t.annotations();
        return new ToolView(t.name(), t.title() != null ? t.title() : a != null ? a.title() : null, t.description(),
                t.inputSchema(), a != null && Boolean.TRUE.equals(a.readOnlyHint()),
                a != null && Boolean.TRUE.equals(a.destructiveHint()));
    }
}

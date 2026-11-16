package com.maverickignite.mcp.mcp;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;

/**
 * Runtime registry of live MCP client connections, keyed by server id. Servers are configured in the DB;
 * this class owns the processes / HTTP sessions and their state.
 */
@Component
public class McpConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionManager.class);

    private static final int MAX_LOG_LINES = 300;

    private static final McpSchema.Implementation CLIENT_INFO = new McpSchema.Implementation("nexus-mcp",
            "holms-nexus MCP", "0.1.0");

    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();

    private final McpServerRepository repository;

    private final ToolCatalogCache toolCache;

    private final ExecutorService executor;

    public McpConnectionManager(McpServerRepository repository, ToolCatalogCache toolCache,
            ExecutorService nexusExecutor) {
        this.repository = repository;
        this.toolCache = toolCache;
        this.executor = nexusExecutor;
    }

    /** Live state of one server connection. */
    public static final class Connection {

        private final UUID serverId;

        private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;

        private volatile String error;

        private volatile McpSyncClient client;

        private volatile McpSchema.Implementation serverInfo;

        private volatile String instructions;

        private volatile List<McpSchema.Tool> tools = List.of();

        private volatile List<McpSchema.Resource> resources = List.of();

        private volatile List<McpSchema.Prompt> prompts = List.of();

        private volatile Instant connectedAt;

        private final Deque<McpDtos.LogLine> logs = new ArrayDeque<>();

        Connection(UUID serverId) {
            this.serverId = serverId;
        }

        void log(String level, String message) {
            synchronized (logs) {
                logs.addLast(new McpDtos.LogLine(Instant.now(), level, message));
                while (logs.size() > MAX_LOG_LINES) {
                    logs.removeFirst();
                }
            }
        }

        public List<McpDtos.LogLine> logs() {
            synchronized (logs) {
                return new ArrayList<>(logs);
            }
        }

        public UUID serverId() { return serverId; }
        public ConnectionStatus status() { return status; }
        public String error() { return error; }
        public McpSyncClient client() { return client; }
        public McpSchema.Implementation serverInfo() { return serverInfo; }
        public String instructions() { return instructions; }
        public List<McpSchema.Tool> tools() { return tools; }
        public List<McpSchema.Resource> resources() { return resources; }
        public List<McpSchema.Prompt> prompts() { return prompts; }
        public Instant connectedAt() { return connectedAt; }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectEnabledOnStartup() {
        repository.findAllByEnabledTrue().forEach(this::connectAsync);
    }

    public Connection state(UUID serverId) {
        return connections.computeIfAbsent(serverId, Connection::new);
    }

    public Optional<Connection> find(UUID serverId) {
        return Optional.ofNullable(connections.get(serverId));
    }

    public void connectAsync(McpServerEntity server) {
        Connection conn = state(server.getId());
        conn.status = ConnectionStatus.CONNECTING;
        conn.error = null;
        executor.submit(() -> connect(server));
    }

    /** Connects (or reconnects) synchronously. Never throws; failures are captured in the state. */
    public Connection connect(McpServerEntity server) {
        Connection conn = state(server.getId());
        synchronized (conn) {
            closeClient(conn);
            conn.status = ConnectionStatus.CONNECTING;
            conn.error = null;
            conn.log("info", "Connecting via " + server.getTransport() + "…");
            try {
                McpClientTransport transport = buildTransport(server, conn);
                McpSyncClient client = McpClient.sync(transport)
                        .clientInfo(CLIENT_INFO)
                        .requestTimeout(Duration.ofSeconds(90))
                        .initializationTimeout(Duration.ofSeconds(120))
                        .loggingConsumer(n -> conn.log(n.level() == null ? "info" : n.level().name().toLowerCase(),
                                n.data() == null ? "" : n.data().toString()))
                        .toolsChangeConsumer(tools -> {
                            conn.tools = List.copyOf(tools);
                            toolCache.put(server.getId(), conn.tools);
                            conn.log("info", "Tools changed: " + tools.size() + " available");
                        })
                        .build();
                McpSchema.InitializeResult init = client.initialize();
                conn.client = client;
                conn.serverInfo = init.serverInfo();
                conn.instructions = init.instructions();
                refreshCatalog(conn);
                conn.connectedAt = Instant.now();
                conn.status = ConnectionStatus.CONNECTED;
                conn.log("info", "Connected to " + (init.serverInfo() == null ? server.getName()
                        : init.serverInfo().name() + " " + init.serverInfo().version()) + " — "
                        + conn.tools.size() + " tools");
            }
            catch (Exception ex) {
                String msg = rootMessage(ex);
                log.warn("MCP server '{}' failed to connect: {}", server.getName(), msg);
                closeClient(conn);
                conn.status = ConnectionStatus.ERROR;
                conn.error = msg;
                conn.log("error", msg);
            }
            return conn;
        }
    }

    public void disconnect(UUID serverId) {
        Connection conn = connections.get(serverId);
        if (conn != null) {
            synchronized (conn) {
                closeClient(conn);
                conn.status = ConnectionStatus.DISCONNECTED;
                conn.error = null;
                conn.log("info", "Disconnected");
            }
        }
    }

    public void remove(UUID serverId) {
        disconnect(serverId);
        connections.remove(serverId);
        toolCache.evict(serverId);
    }

    public McpSyncClient requireClient(UUID serverId) {
        Connection conn = connections.get(serverId);
        if (conn == null || conn.client == null || conn.status != ConnectionStatus.CONNECTED) {
            throw new IllegalStateException("Server is not connected");
        }
        return conn.client;
    }

    public void refreshCatalog(Connection conn) {
        McpSyncClient client = conn.client;
        McpSchema.ServerCapabilities caps = client.getServerCapabilities();
        conn.tools = caps == null || caps.tools() != null ? listAllTools(client) : List.of();
        toolCache.put(conn.serverId, conn.tools);
        conn.resources = caps != null && caps.resources() != null ? safe(() -> listAllResources(client)) : List.of();
        conn.prompts = caps != null && caps.prompts() != null ? safe(() -> listAllPrompts(client)) : List.of();
    }

    private static List<McpSchema.Tool> listAllTools(McpSyncClient client) {
        List<McpSchema.Tool> all = new ArrayList<>();
        String cursor = null;
        do {
            McpSchema.ListToolsResult page = cursor == null ? client.listTools() : client.listTools(cursor);
            all.addAll(page.tools());
            cursor = page.nextCursor();
        }
        while (cursor != null && !cursor.isBlank());
        return List.copyOf(all);
    }

    private static List<McpSchema.Resource> listAllResources(McpSyncClient client) {
        List<McpSchema.Resource> all = new ArrayList<>();
        String cursor = null;
        do {
            McpSchema.ListResourcesResult page = cursor == null ? client.listResources()
                    : client.listResources(cursor);
            all.addAll(page.resources());
            cursor = page.nextCursor();
        }
        while (cursor != null && !cursor.isBlank() && all.size() < 500);
        return List.copyOf(all);
    }

    private static List<McpSchema.Prompt> listAllPrompts(McpSyncClient client) {
        return List.copyOf(client.listPrompts().prompts());
    }

    private static <T> List<T> safe(java.util.function.Supplier<List<T>> supplier) {
        try {
            return supplier.get();
        }
        catch (Exception ex) {
            return List.of();
        }
    }

    private McpClientTransport buildTransport(McpServerEntity server, Connection conn) {
        return switch (server.getTransport()) {
            case STDIO -> {
                if (server.getCommand() == null || server.getCommand().isBlank()) {
                    throw new IllegalArgumentException("Command is required for STDIO servers");
                }
                Map<String, String> env = new LinkedHashMap<>(server.getEnv());
                env.put("PATH", enrichedPath(env.getOrDefault("PATH", System.getenv("PATH"))));
                ServerParameters params = ServerParameters.builder(resolveCommand(server.getCommand(), env.get("PATH")))
                        .args(server.getArgs())
                        .env(env)
                        .build();
                StdioClientTransport transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());
                transport.setStdErrorHandler(line -> conn.log("stderr", line));
                yield transport;
            }
            case STREAMABLE_HTTP -> {
                URI uri = requireUrl(server);
                yield HttpClientStreamableHttpTransport.builder(origin(uri))
                        .endpoint(pathAndQuery(uri, "/mcp"))
                        .connectTimeout(Duration.ofSeconds(20))
                        .httpRequestCustomizer((builder, method, endpoint, body, ctx) -> server.getHeaders()
                                .forEach(builder::header))
                        .build();
            }
            case SSE -> {
                URI uri = requireUrl(server);
                yield HttpClientSseClientTransport.builder(origin(uri))
                        .sseEndpoint(pathAndQuery(uri, "/sse"))
                        .connectTimeout(Duration.ofSeconds(20))
                        .httpRequestCustomizer((builder, method, endpoint, body, ctx) -> server.getHeaders()
                                .forEach(builder::header))
                        .build();
            }
        };
    }

    private static URI requireUrl(McpServerEntity server) {
        if (server.getUrl() == null || server.getUrl().isBlank()) {
            throw new IllegalArgumentException("URL is required for HTTP servers");
        }
        return URI.create(server.getUrl().trim());
    }

    private static String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static String pathAndQuery(URI uri, String fallback) {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? fallback : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    /** GUI-launched JVMs often miss Homebrew / user bin dirs; add the usual suspects so npx/uvx resolve. */
    private static String enrichedPath(String path) {
        Set<String> dirs = new LinkedHashSet<>();
        if (path != null) {
            dirs.addAll(List.of(path.split(File.pathSeparator)));
        }
        String home = System.getProperty("user.home");
        dirs.addAll(List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin", home + "/.local/bin",
                home + "/.cargo/bin", home + "/.bun/bin"));
        return String.join(File.pathSeparator, dirs);
    }

    private static String resolveCommand(String command, String path) {
        if (command.contains(File.separator)) {
            return command;
        }
        for (String dir : path.split(File.pathSeparator)) {
            File candidate = new File(dir, command);
            if (candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return command;
    }

    private static void closeClient(Connection conn) {
        McpSyncClient client = conn.client;
        conn.client = null;
        conn.connectedAt = null;
        if (client != null) {
            try {
                client.closeGracefully();
            }
            catch (Exception ex) {
                try {
                    client.close();
                }
                catch (Exception ignored) {
                    // best effort
                }
            }
        }
    }

    static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = t.getClass().getSimpleName();
        }
        if (t instanceof java.io.IOException && msg.contains("error=2")) {
            msg = "Command not found. Make sure it is installed and on the PATH (" + msg + ")";
        }
        return msg;
    }

    @PreDestroy
    public void shutdown() {
        connections.values().forEach(McpConnectionManager::closeClient);
    }
}

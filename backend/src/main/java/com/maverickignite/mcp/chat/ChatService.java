package com.maverickignite.mcp.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.maverickignite.mcp.agent.AgentRequest;
import com.maverickignite.mcp.agent.AgentRun;
import com.maverickignite.mcp.agent.AgentSupport;
import com.maverickignite.mcp.agent.SingleAgentRunner;
import com.maverickignite.mcp.agent.TurnRecorder;
import com.maverickignite.mcp.agent.WorkflowOrchestrator;
import com.maverickignite.mcp.chat.ChatDtos.ConversationDetail;
import com.maverickignite.mcp.chat.ChatDtos.ConversationSummary;
import com.maverickignite.mcp.chat.ChatDtos.CreateConversation;
import com.maverickignite.mcp.chat.ChatDtos.MessageView;
import com.maverickignite.mcp.chat.ChatDtos.SendMessage;
import com.maverickignite.mcp.chat.ChatDtos.UpdateConversation;
import com.maverickignite.mcp.common.ApiException;
import com.maverickignite.mcp.common.Json;
import com.maverickignite.mcp.common.NexusProperties;
import com.maverickignite.mcp.llm.ChatModelFactory;
import com.maverickignite.mcp.llm.LlmProviderEntity;
import com.maverickignite.mcp.llm.LlmProviderService;
import com.maverickignite.mcp.mcp.ConnectionStatus;
import com.maverickignite.mcp.mcp.McpConnectionManager;
import com.maverickignite.mcp.mcp.McpServerRepository;
import com.maverickignite.mcp.settings.SettingsService;
import com.maverickignite.mcp.workspace.WorkspaceEntity;
import com.maverickignite.mcp.workspace.WorkspaceService;

@Service
public class ChatService {

    private static final int HISTORY_CONTEXT_CHARS = 1_500;

    private final ConversationRepository conversations;

    private final ChatMessageRepository messages;

    private final LlmProviderService providers;

    private final ChatModelFactory modelFactory;

    private final SettingsService settings;

    private final McpServerRepository servers;

    private final McpConnectionManager connections;

    private final WorkspaceService workspaces;

    private final WorkflowOrchestrator orchestrator;

    private final SingleAgentRunner singleAgent;

    private final NexusProperties properties;

    private final ExecutorService executor;

    /** Cancel hooks of in-flight turns, keyed by conversation. */
    private final Map<UUID, Runnable> activeStreams = new ConcurrentHashMap<>();

    public ChatService(ConversationRepository conversations, ChatMessageRepository messages,
            LlmProviderService providers, ChatModelFactory modelFactory, SettingsService settings,
            McpServerRepository servers, McpConnectionManager connections, WorkspaceService workspaces,
            WorkflowOrchestrator orchestrator, SingleAgentRunner singleAgent, NexusProperties properties,
            ExecutorService nexusExecutor) {
        this.conversations = conversations;
        this.messages = messages;
        this.providers = providers;
        this.modelFactory = modelFactory;
        this.settings = settings;
        this.servers = servers;
        this.connections = connections;
        this.workspaces = workspaces;
        this.orchestrator = orchestrator;
        this.singleAgent = singleAgent;
        this.properties = properties;
        this.executor = nexusExecutor;
    }

    // ------------------------------------------------------------------ conversations

    /** Chats created before workspaces existed belong to the default workspace. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.core.annotation.Order(1)
    public void adoptOrphanConversations() {
        conversations.assignOrphansTo(workspaces.resolve(null).getId());
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(String q, UUID workspaceId) {
        List<ConversationEntity> all = workspaceId == null ? conversations.findAllByOrderByPinnedDescUpdatedAtDesc()
                : conversations.findByWorkspaceIdOrderByPinnedDescUpdatedAtDesc(workspaceId);
        if (q == null || q.isBlank()) {
            return all.stream().map(ChatService::summary).toList();
        }
        Set<UUID> ids = new HashSet<>(messages.findConversationIdsByContent(q.trim()));
        String needle = q.trim().toLowerCase();
        return all.stream()
                .filter(c -> ids.contains(c.getId()) || c.getTitle().toLowerCase().contains(needle))
                .map(ChatService::summary)
                .toList();
    }

    @Transactional
    public ConversationSummary create(CreateConversation req) {
        ConversationEntity c = new ConversationEntity();
        if (req.title() != null && !req.title().isBlank()) {
            c.setTitle(req.title().trim());
        }
        c.setWorkspaceId(workspaces.resolve(req.workspaceId()).getId());
        c.setProviderId(req.providerId());
        c.setModel(req.model());
        c.setServerIds(req.serverIds());
        return summary(conversations.save(c));
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(UUID id) {
        ConversationEntity c = require(id);
        List<MessageView> list = messages.findByConversationIdOrderByCreatedAtAsc(id).stream()
                .map(ChatService::view).toList();
        return new ConversationDetail(summary(c), list);
    }

    @Transactional
    public ConversationSummary update(UUID id, UpdateConversation req) {
        ConversationEntity c = require(id);
        if (req.title() != null && !req.title().isBlank()) {
            c.setTitle(req.title().trim());
        }
        if (req.pinned() != null) {
            c.setPinned(req.pinned());
        }
        if (req.workspaceId() != null) {
            c.setWorkspaceId(req.workspaceId());
        }
        if (req.providerId() != null) {
            c.setProviderId(req.providerId());
        }
        if (req.model() != null) {
            c.setModel(req.model());
        }
        if (req.serverIds() != null) {
            c.setServerIds(req.serverIds());
        }
        return summary(conversations.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        stop(id);
        conversations.delete(require(id));
    }

    private ConversationEntity require(UUID id) {
        return conversations.findById(id).orElseThrow(() -> ApiException.notFound("Conversation"));
    }

    // ------------------------------------------------------------------ streaming

    public void stop(UUID conversationId) {
        Runnable cancel = activeStreams.get(conversationId);
        if (cancel != null) {
            cancel.run();
        }
    }

    /** One daemon thread sends the keep-alive pings for all open chat streams. */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEATS =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /**
     * Streams an assistant turn as Server-Sent Events. Multi-agent mode emits {@code agent_start}, {@code plan},
     * {@code tool_start}/{@code tool_end} (tagged with {@code agentId}), {@code agent_end}, {@code token} and
     * {@code artifact}; every turn ends with {@code done} (preceded by {@code error} on failure).
     */
    public SseEmitter stream(UUID conversationId, SendMessage req) {
        ConversationEntity conversation = require(conversationId);
        SettingsService.AppSettings appSettings = settings.get();

        UUID providerId = firstNonNull(req.providerId(), conversation.getProviderId(), appSettings.defaultProviderId());
        LlmProviderEntity provider = providerId != null ? providers.require(providerId) : providers.defaultProvider();
        String model = firstNonBlank(req.model(),
                provider.getId().equals(conversation.getProviderId()) ? conversation.getModel() : null,
                provider.getId().equals(appSettings.defaultProviderId()) ? appSettings.defaultModel() : null,
                provider.getDefaultModel());
        WorkspaceEntity workspace = workspaces.resolve(
                firstNonNull(req.workspaceId(), conversation.getWorkspaceId()));
        List<UUID> serverIds = req.serverIds() != null ? req.serverIds() : conversation.getServerIds();

        conversation.setWorkspaceId(workspace.getId());
        conversation.setProviderId(provider.getId());
        conversation.setModel(model);
        conversation.setServerIds(serverIds);
        conversation.touch();
        conversations.save(conversation);

        if (Boolean.TRUE.equals(req.regenerate())) {
            dropTrailingAssistant(conversationId);
        }
        else {
            ChatMessageEntity userMsg = new ChatMessageEntity();
            userMsg.setConversationId(conversationId);
            userMsg.setRole("user");
            userMsg.setContent(req.content());
            messages.save(userMsg);
        }

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        TurnRecorder turn = new TurnRecorder(emitter);
        long startedAt = System.currentTimeMillis();

        List<AgentRequest.ServerHandle> handles = connectedServers(serverIds);
        AgentRequest agentReq = new AgentRequest(req.content(), history(conversationId), handles, workspace,
                appSettings, provider, model, turn);

        turn.send("meta", Map.of("conversationId", conversationId, "provider", provider.getName(),
                "providerType", provider.getType(), "model", model, "workspace", workspace.getName(),
                "mode", appSettings.multiAgent() ? "multi" : "single",
                "toolCount", handles.stream().mapToInt(h -> h.connection().tools().size()).sum()));

        if (ConversationEntity.DEFAULT_TITLE.equals(conversation.getTitle())) {
            ChatModel titleModel = modelFactory.create(provider, model, 0.3, 60);
            executor.submit(() -> {
                String title = generateTitle(titleModel, req.content());
                if (conversations.replacePlaceholderTitle(conversationId, title, ConversationEntity.DEFAULT_TITLE) > 0) {
                    turn.send("title", Map.of("title", title));
                }
            });
        }

        Runnable finish = () -> finishTurn(conversationId, turn, model, startedAt);
        AgentRun run = appSettings.multiAgent() ? orchestrator.start(agentReq, finish)
                : singleAgent.start(agentReq, finish);
        Runnable cancel = () -> {
            if (!turn.finished.get()) {
                turn.cancel();
                run.cancel();
                finish.run();
            }
        };
        activeStreams.put(conversationId, cancel);
        // Proxies (nginx: 60–120 s read timeouts) drop a stream that stays silent; ping every 15 s.
        java.util.concurrent.ScheduledFuture<?> heartbeat =
                HEARTBEATS.scheduleAtFixedRate(turn::heartbeat, 15, 15, java.util.concurrent.TimeUnit.SECONDS);
        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onTimeout(() -> { heartbeat.cancel(false); cancel.run(); });
        emitter.onError(e -> { heartbeat.cancel(false); cancel.run(); });
        return emitter;
    }

    private List<AgentRequest.ServerHandle> connectedServers(List<UUID> serverIds) {
        List<AgentRequest.ServerHandle> out = new ArrayList<>();
        for (UUID id : serverIds) {
            servers.findById(id).filter(s -> s.isEnabled()).ifPresent(s -> connections.find(id)
                    .filter(c -> c.status() == ConnectionStatus.CONNECTED && c.client() != null)
                    .ifPresent(c -> out.add(new AgentRequest.ServerHandle(s, c))));
        }
        return out;
    }

    private void finishTurn(UUID conversationId, TurnRecorder turn, String model, long startedAt) {
        if (!turn.finished.compareAndSet(false, true)) {
            return;
        }
        activeStreams.remove(conversationId);
        long duration = System.currentTimeMillis() - startedAt;
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setModel(model);
        msg.setDurationMs(duration);
        String content = turn.fullText();
        if (turn.error() != null) {
            msg.setError(true);
            content = content.isBlank() ? turn.error() : content + "\n\n⚠️ " + turn.error();
        }
        else if (turn.cancelled()) {
            content = content + (content.isBlank() ? "_Stopped._" : "\n\n_Stopped._");
        }
        msg.setContent(content);
        msg.setParts(turn.parts());
        messages.save(msg);

        conversations.touch(conversationId, Instant.now());

        if (turn.error() != null) {
            turn.send("error", Map.of("message", turn.error(), "messageId", msg.getId()));
        }
        turn.send("done", Map.of("messageId", msg.getId(), "durationMs", duration));
        turn.complete();
    }

    private String generateTitle(ChatModel chatModel, String userText) {
        String fallback = userText.length() > 60 ? userText.substring(0, 57).trim() + "…" : userText;
        try {
            String prompt = "Write a short, specific title (3-6 words, no quotes, no trailing punctuation) for a chat "
                    + "that starts with this user message:\n\n" + truncate(userText, 1000);
            String title = chatModel.call(new Prompt(new UserMessage(prompt))).getResult().getOutput().getText();
            title = AgentSupport.stripThinking(title).replaceAll("^[\"'#*\\s]+|[\"'.*\\s]+$", "");
            if (title.isBlank()) {
                return fallback;
            }
            return title.length() > 80 ? title.substring(0, 80) : title;
        }
        catch (Exception ex) {
            return fallback;
        }
    }

    private void dropTrailingAssistant(UUID conversationId) {
        List<ChatMessageEntity> all = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        for (int i = all.size() - 1; i >= 0 && "assistant".equals(all.get(i).getRole()); i--) {
            messages.delete(all.get(i));
        }
    }

    /** Prior turns, excluding the user message being answered now. */
    private List<Message> history(UUID conversationId) {
        List<ChatMessageEntity> all = new ArrayList<>(messages.findByConversationIdOrderByCreatedAtAsc(conversationId));
        if (!all.isEmpty() && "user".equals(all.getLast().getRole())) {
            all.removeLast();
        }
        int from = Math.max(0, all.size() - properties.historyLimit());
        List<Message> out = new ArrayList<>();
        for (int i = from; i < all.size(); i++) {
            ChatMessageEntity m = all.get(i);
            if ("user".equals(m.getRole())) {
                out.add(new UserMessage(m.getContent()));
            }
            else {
                out.add(new AssistantMessage(withDataContext(m, i >= all.size() - 6)));
            }
        }
        return out;
    }

    /** Re-inject compact traces of earlier retrieved data so follow-up questions can build on it. */
    @SuppressWarnings("unchecked")
    private static String withDataContext(ChatMessageEntity m, boolean recent) {
        if (!recent) {
            return m.getContent();
        }
        StringBuilder sb = new StringBuilder(m.getContent());
        for (Map<String, Object> part : m.getParts()) {
            if ("tool".equals(part.get("type"))) {
                appendTool(sb, part);
            }
            else if ("workflow".equals(part.get("type")) && part.get("agents") instanceof List<?> agents) {
                for (Object a : agents) {
                    Map<String, Object> agent = (Map<String, Object>) a;
                    if ("retriever".equals(agent.get("role")) && agent.get("output") != null) {
                        sb.append("\n\n[Data retrieved earlier from ").append(agent.get("server")).append(": ")
                                .append(truncate(String.valueOf(agent.get("output")), HISTORY_CONTEXT_CHARS))
                                .append("]");
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void appendTool(StringBuilder sb, Map<String, Object> part) {
        sb.append("\n\n[Earlier tool call ").append(part.get("server")).append(" › ").append(part.get("tool"))
                .append(" with ").append(Json.write(part.getOrDefault("args", Map.of()))).append(" returned: ")
                .append(truncate(String.valueOf(part.getOrDefault("result", "")), HISTORY_CONTEXT_CHARS))
                .append("]");
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ mapping

    static ConversationSummary summary(ConversationEntity c) {
        return new ConversationSummary(c.getId(), c.getTitle(), c.isPinned(), c.getWorkspaceId(), c.getProviderId(),
                c.getModel(), c.getServerIds(), c.getCreatedAt(), c.getUpdatedAt());
    }

    static MessageView view(ChatMessageEntity m) {
        return new MessageView(m.getId(), m.getRole(), m.getContent(), m.getParts(), m.getModel(), m.getDurationMs(),
                m.isError(), m.getCreatedAt());
    }
}

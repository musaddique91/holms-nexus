package com.maverickignite.mcp.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.maverickignite.mcp.common.ApiException;
import com.maverickignite.mcp.common.NexusProperties;
import com.maverickignite.mcp.llm.LlmDtos.ProviderRequest;
import com.maverickignite.mcp.llm.LlmDtos.ProviderView;
import com.maverickignite.mcp.llm.LlmDtos.TestResult;

@Service
public class LlmProviderService {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderService.class);

    static final Map<ProviderType, List<String>> SUGGESTED_MODELS = Map.of(
            ProviderType.ANTHROPIC, List.of("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5",
                    "claude-fable-5-1"),
            ProviderType.OPENAI, List.of("gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4.1-mini"),
            ProviderType.OLLAMA, List.of("qwen3", "llama3.2", "mistral"));

    private final LlmProviderRepository repository;

    private final ChatModelFactory modelFactory;

    private final NexusProperties properties;

    private final RestClient http;

    private final ApplicationEventPublisher events;

    public LlmProviderService(LlmProviderRepository repository, ChatModelFactory modelFactory,
            NexusProperties properties, ApplicationEventPublisher events) {
        this.events = events;
        this.repository = repository;
        this.modelFactory = modelFactory;
        this.properties = properties;
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory();
        rf.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    /** Seeds providers from environment variables on the very first start. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        if (repository.count() > 0) {
            return;
        }
        NexusProperties.Bootstrap b = properties.bootstrap();
        if (b.anthropicApiKey() != null && !b.anthropicApiKey().isBlank()) {
            repository.save(newProvider("Anthropic", ProviderType.ANTHROPIC, b.anthropicApiKey(), null,
                    "claude-sonnet-5-5", true));
        }
        if (b.openaiApiKey() != null && !b.openaiApiKey().isBlank()) {
            repository.save(newProvider("OpenAI", ProviderType.OPENAI, b.openaiApiKey(), null, "gpt-5", true));
        }
        if (b.ollamaBaseUrl() != null && !b.ollamaBaseUrl().isBlank()) {
            repository.save(newProvider("Ollama (local)", ProviderType.OLLAMA, null, b.ollamaBaseUrl(), "qwen3", true));
        }
        log.info("Seeded {} LLM provider(s)", repository.count());
    }

    private static LlmProviderEntity newProvider(String name, ProviderType type, String key, String baseUrl,
            String model, boolean enabled) {
        LlmProviderEntity e = new LlmProviderEntity();
        e.setName(name);
        e.setType(type);
        e.setApiKey(key);
        e.setBaseUrl(baseUrl);
        e.setDefaultModel(model);
        e.setModels(SUGGESTED_MODELS.get(type));
        e.setEnabled(enabled);
        return e;
    }

    @Transactional(readOnly = true)
    public List<ProviderView> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(LlmProviderService::toView).toList();
    }

    public LlmProviderEntity require(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Provider"));
    }

    /** First enabled provider that is ready to use (has a key, or is keyless like Ollama). */
    @Transactional(readOnly = true)
    public LlmProviderEntity defaultProvider() {
        return repository.findAllByOrderByCreatedAtAsc().stream()
                .filter(p -> p.isEnabled() && isReady(p))
                .min(Comparator.comparing(p -> p.getType() == ProviderType.OLLAMA ? 1 : 0))
                .orElseThrow(() -> ApiException.badRequest(
                        "No model provider is configured. Open Settings → Models and add an API key."));
    }

    @Transactional
    public ProviderView create(ProviderRequest req) {
        LlmProviderEntity e = new LlmProviderEntity();
        apply(e, req);
        if (e.getModels().isEmpty()) {
            e.setModels(SUGGESTED_MODELS.get(e.getType()));
        }
        ProviderView view = toView(repository.save(e));
        events.publishEvent(new ProvidersChangedEvent());
        return view;
    }

    @Transactional
    public ProviderView update(UUID id, ProviderRequest req) {
        LlmProviderEntity e = require(id);
        apply(e, req);
        ProviderView view = toView(repository.save(e));
        events.publishEvent(new ProvidersChangedEvent());
        return view;
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(require(id));
        events.publishEvent(new ProvidersChangedEvent());
    }

    private static void apply(LlmProviderEntity e, ProviderRequest req) {
        e.setName(req.name().trim());
        e.setType(req.type());
        // A blank key on update means "keep the existing one".
        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            e.setApiKey(req.apiKey().trim());
        }
        e.setBaseUrl(req.baseUrl() == null || req.baseUrl().isBlank() ? null : req.baseUrl().trim());
        e.setDefaultModel(req.defaultModel().trim());
        if (req.models() != null) {
            List<String> models = new ArrayList<>(req.models().stream().filter(m -> !m.isBlank()).distinct().toList());
            if (!models.contains(e.getDefaultModel())) {
                models.addFirst(e.getDefaultModel());
            }
            e.setModels(models);
        }
        e.setEnabled(req.enabled() == null || req.enabled());
    }

    /** Model ids that are not chat models (embeddings, rerankers, guards, vision/speech-only). */
    private static final java.util.regex.Pattern NON_CHAT = java.util.regex.Pattern.compile(
            "embed|rerank|guard|safety|reward|parse|clip|deplot|vila|kosmos|neva|fuyu|paligemma|tts|whisper|asr|"
                    + "riva|stable-diffusion|sdxl|flux|bge|e5-|nv-embed|retriever|vision-?only",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Asks the provider for its available models and stores them. */
    @Transactional
    @SuppressWarnings("unchecked")
    public ProviderView refreshModels(UUID id) {
        LlmProviderEntity p = require(id);
        List<String> models;
        try {
            models = switch (p.getType()) {
                case ANTHROPIC -> {
                    String base = p.getBaseUrl() == null ? "https://api.anthropic.com" : p.getBaseUrl();
                    Map<String, Object> body = http.get().uri(trimSlash(base) + "/v1/models?limit=100")
                            .header("x-api-key", p.getApiKey())
                            .header("anthropic-version", "2023-06-01")
                            .retrieve().body(Map.class);
                    yield ids(body, "data", "id");
                }
                case OPENAI -> {
                    String base = p.getBaseUrl() == null ? "https://api.openai.com/v1" : p.getBaseUrl();
                    Map<String, Object> body = http.get().uri(trimSlash(base) + "/models")
                            .header("Authorization", "Bearer " + p.getApiKey())
                            .retrieve().body(Map.class);
                    boolean openAi = p.getBaseUrl() == null || p.getBaseUrl().contains("api.openai.com");
                    yield ids(body, "data", "id").stream()
                            // api.openai.com lists everything (audio, images, embeddings): keep the chat
                            // families. Other OpenAI-compatible APIs (NVIDIA, OpenRouter, vLLM…) name
                            // models freely, so only drop the ones that clearly cannot chat.
                            .filter(m -> openAi ? m.startsWith("gpt") || m.startsWith("o") || !m.contains("-")
                                    : !NON_CHAT.matcher(m).find())
                            .sorted(openAi ? Comparator.reverseOrder() : Comparator.naturalOrder()).toList();
                }
                case OLLAMA -> {
                    String base = p.getBaseUrl() == null ? "http://localhost:11434" : p.getBaseUrl();
                    Map<String, Object> body = http.get().uri(trimSlash(base) + "/api/tags").retrieve()
                            .body(Map.class);
                    yield ids(body, "models", "name");
                }
            };
        }
        catch (Exception ex) {
            throw ApiException.badRequest("Could not list models: " + ex.getMessage());
        }
        if (!models.isEmpty()) {
            p.setModels(models);
            if (!models.contains(p.getDefaultModel())) {
                p.setDefaultModel(models.getFirst());
            }
        }
        return toView(repository.save(p));
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Map<String, Object> body, String listKey, String idKey) {
        if (body == null || !(body.get(listKey) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance)
                .map(m -> String.valueOf(((Map<String, Object>) m).get(idKey))).toList();
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public TestResult test(UUID id) {
        LlmProviderEntity p = require(id);
        long start = System.currentTimeMillis();
        try {
            ChatModel model = modelFactory.create(p, null, null, 32);
            String reply = model.call(new Prompt(new UserMessage("Reply with exactly: OK"))).getResult().getOutput()
                    .getText();
            return new TestResult(true, "Model replied: " + (reply == null ? "" : reply.strip()),
                    System.currentTimeMillis() - start);
        }
        catch (Exception ex) {
            Throwable t = ex;
            while (t.getCause() != null && t.getCause() != t) {
                t = t.getCause();
            }
            return new TestResult(false, t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    static boolean isReady(LlmProviderEntity p) {
        return p.getType() == ProviderType.OLLAMA || (p.getApiKey() != null && !p.getApiKey().isBlank());
    }

    static ProviderView toView(LlmProviderEntity p) {
        String key = p.getApiKey();
        String hint = key == null || key.length() < 8 ? null : key.substring(0, 4) + "…" + key.substring(key.length() - 4);
        return new ProviderView(p.getId(), p.getName(), p.getType(), key != null && !key.isBlank(), hint,
                p.getBaseUrl(), p.getDefaultModel(), p.getModels(), p.isEnabled(), isReady(p));
    }
}

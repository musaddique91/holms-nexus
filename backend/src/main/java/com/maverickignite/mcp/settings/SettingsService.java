package com.maverickignite.mcp.settings;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Service
public class SettingsService {

    public static final String DEFAULT_SYSTEM_PROMPT = """
            You are MVX MCP, a sharp, friendly data analyst connected to the user's systems through MCP tools.
            - Use the available tools to fetch real data before answering questions about it; never invent data.
            - Prefer several small, targeted tool calls over one huge one. If a tool fails, explain and try another way.
            - Present results clearly: short summary first, then details. Use GitHub-flavoured markdown tables for tabular data.
            - Be concise and point out notable insights, anomalies and next questions worth asking.""";

    /** User-tunable settings. */
    public record AppSettings(String systemPrompt, double temperature, int maxTokens, UUID defaultProviderId,
            String defaultModel, String agentMode, boolean visualizer, int maxParallelAgents) {

        public boolean multiAgent() {
            return !"single".equals(agentMode);
        }
    }

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AppSettings get() {
        Map<String, String> m = repository.findAll().stream()
                .collect(Collectors.toMap(AppSettingEntity::getKey, AppSettingEntity::getValue));
        return new AppSettings(
                m.getOrDefault("systemPrompt", DEFAULT_SYSTEM_PROMPT),
                parse(m.get("temperature"), Double::parseDouble, 0.4),
                parse(m.get("maxTokens"), Integer::parseInt, 8192),
                parse(m.get("defaultProviderId"), UUID::fromString, null),
                m.get("defaultModel"),
                m.getOrDefault("agentMode", "multi"),
                parse(m.get("visualizer"), Boolean::parseBoolean, true),
                parse(m.get("maxParallelAgents"), Integer::parseInt, 4));
    }

    @Transactional
    public AppSettings update(AppSettings s) {
        put("systemPrompt", s.systemPrompt() == null || s.systemPrompt().isBlank() ? DEFAULT_SYSTEM_PROMPT
                : s.systemPrompt());
        put("temperature", String.valueOf(Math.max(0, Math.min(2, s.temperature()))));
        put("maxTokens", String.valueOf(Math.max(256, Math.min(64000, s.maxTokens()))));
        put("defaultProviderId", s.defaultProviderId() == null ? "" : s.defaultProviderId().toString());
        put("defaultModel", s.defaultModel() == null ? "" : s.defaultModel());
        put("agentMode", "single".equals(s.agentMode()) ? "single" : "multi");
        put("visualizer", String.valueOf(s.visualizer()));
        put("maxParallelAgents", String.valueOf(Math.max(1, Math.min(8, s.maxParallelAgents() == 0 ? 4 : s.maxParallelAgents()))));
        return get();
    }

    private void put(String key, String value) {
        AppSettingEntity e = repository.findById(key).orElseGet(() -> new AppSettingEntity(key, value));
        e.setValue(value);
        repository.save(e);
    }

    private static <T> T parse(String raw, Function<String, T> parser, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return parser.apply(raw);
        }
        catch (RuntimeException ex) {
            return fallback;
        }
    }

    @RestController
    @RequestMapping("/api/settings")
    static class SettingsController {

        private final SettingsService service;

        SettingsController(SettingsService service) {
            this.service = service;
        }

        @GetMapping
        AppSettings get() {
            return service.get();
        }

        @PutMapping
        AppSettings update(@RequestBody AppSettings settings) {
            return service.update(settings);
        }

        @GetMapping("/default-system-prompt")
        Map<String, String> defaultPrompt() {
            return Map.of("systemPrompt", DEFAULT_SYSTEM_PROMPT);
        }
    }
}

package com.maverickignite.mcp.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads {@code prompts/*.md} templates and fills {@code {{placeholders}}} (plain replace, braces-safe). */
@Component
public class McpPrompts {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String name) {
        return cache.computeIfAbsent(name, n -> {
            try {
                return new ClassPathResource("prompts/" + n + ".md").getContentAsString(StandardCharsets.UTF_8);
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    public String render(String name, Map<String, String> values) {
        String out = get(name);
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }
}

package com.maverickignite.mcp.common;

import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Static JSON helper so JPA converters (instantiated by Hibernate) can share one mapper. */
public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private Json() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static String pretty(Object value) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static <T> T read(String json, TypeReference<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(json, new TypeReference<>() {
        });
    }

    public static List<Object> readList(String json) {
        return MAPPER.readValue(json, new TypeReference<>() {
        });
    }
}

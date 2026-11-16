package com.maverickignite.mcp.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

/** JSON-in-TEXT column converters. */
public final class JsonConverters {

    private JsonConverters() {
    }

    @Converter
    public static class StringList implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            return Json.write(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Json.read(dbData, new TypeReference<List<String>>() {
            }));
        }
    }

    @Converter
    public static class StringMap implements AttributeConverter<Map<String, String>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, String> attribute) {
            return Json.write(attribute == null ? Map.of() : attribute);
        }

        @Override
        public Map<String, String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return new LinkedHashMap<>();
            }
            return new LinkedHashMap<>(Json.read(dbData, new TypeReference<Map<String, String>>() {
            }));
        }
    }

    @Converter
    public static class UuidList implements AttributeConverter<List<UUID>, String> {
        @Override
        public String convertToDatabaseColumn(List<UUID> attribute) {
            return Json.write(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<UUID> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Json.read(dbData, new TypeReference<List<UUID>>() {
            }));
        }
    }

    @Converter
    public static class ObjectList implements AttributeConverter<List<Map<String, Object>>, String> {
        @Override
        public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
            return Json.write(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Json.read(dbData, new TypeReference<List<Map<String, Object>>>() {
            }));
        }
    }
}

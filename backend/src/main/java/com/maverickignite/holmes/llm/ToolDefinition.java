package com.maverickignite.holmes.llm;

import tools.jackson.databind.JsonNode;

/** A function the model may call; {@code inputSchema} is a JSON Schema object. */
public record ToolDefinition(String name, String description, JsonNode inputSchema) {}

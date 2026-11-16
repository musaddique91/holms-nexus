package com.maverickignite.holmes.toolset;

import com.maverickignite.holmes.llm.ToolDefinition;
import tools.jackson.databind.JsonNode;

/** A callable tool: its LLM-facing contract plus the code that runs it. */
public record ToolSpec(String name, String description, JsonNode parameters, ToolHandler handler) {

  public ToolDefinition definition() {
    return new ToolDefinition(name, description, parameters);
  }
}

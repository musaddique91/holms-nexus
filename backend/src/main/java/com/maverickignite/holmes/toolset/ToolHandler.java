package com.maverickignite.holmes.toolset;

import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface ToolHandler {

  /** Returns text for the model. Throw {@link ToolException} for errors the model should see. */
  String invoke(JsonNode arguments) throws Exception;
}

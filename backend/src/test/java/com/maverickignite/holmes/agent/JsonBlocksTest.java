package com.maverickignite.holmes.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonBlocksTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void takesLastFencedBlockAndProse() {
    String answer =
        "## Summary\nPool exhausted.\n```json\n{\"a\":1}\n```\nmore\n```json\n{\"confidence\":\"HIGH\"}\n```";
    assertThat(JsonBlocks.last(answer, json).path("confidence").asString()).isEqualTo("HIGH");
    assertThat(JsonBlocks.prose("Report\n```json\n{}\n```")).isEqualTo("Report");
  }

  @Test
  void fallsBackToBareObjectAndToleratesGarbage() {
    assertThat(JsonBlocks.last("result: {\"x\": {\"y\": 2}}", json).path("x").path("y").asInt()).isEqualTo(2);
    assertThat(JsonBlocks.last("no json here", json)).isNull();
    assertThat(JsonBlocks.last("{not json}", json)).isNull();
  }
}

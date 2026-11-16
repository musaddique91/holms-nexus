package com.maverickignite.holmes.toolset.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LokiToolsetTest {

  @Test
  void disabledByDefault() {
    HolmesProperties props = new HolmesProperties();
    LokiToolset toolset = new LokiToolset(props, RestClient.builder());
    assertThat(toolset.name()).isEqualTo("loki");
    assertThat(toolset.enabled()).isFalse();
  }

  @Test
  void registersLokiToolsWhenEnabled() {
    HolmesProperties props = new HolmesProperties();
    props.getToolsets().getLoki().setEnabled(true);
    props.getToolsets().getLoki().setUrl("http://localhost:3100");

    LokiToolset toolset = new LokiToolset(props, RestClient.builder());
    assertThat(toolset.enabled()).isTrue();

    List<String> toolNames = toolset.tools().stream().map(ToolSpec::name).toList();
    assertThat(toolNames)
        .containsExactlyInAnyOrder(
            "loki_query_range",
            "loki_instant_query",
            "loki_labels",
            "loki_label_values");
  }
}

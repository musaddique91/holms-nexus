package com.maverickignite.holmes.toolset.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class PrometheusToolsetTest {

  @Test
  void disabledByDefault() {
    HolmesProperties props = new HolmesProperties();
    PrometheusToolset toolset = new PrometheusToolset(props, RestClient.builder());
    assertThat(toolset.name()).isEqualTo("prometheus");
    assertThat(toolset.enabled()).isFalse();
  }

  @Test
  void registersPrometheusToolsWhenEnabled() {
    HolmesProperties props = new HolmesProperties();
    props.getToolsets().getPrometheus().setEnabled(true);
    props.getToolsets().getPrometheus().setUrl("http://localhost:9090");

    PrometheusToolset toolset = new PrometheusToolset(props, RestClient.builder());
    assertThat(toolset.enabled()).isTrue();

    List<String> toolNames = toolset.tools().stream().map(ToolSpec::name).toList();
    assertThat(toolNames)
        .containsExactlyInAnyOrder(
            "prometheus_instant_query",
            "prometheus_range_query",
            "prometheus_active_alerts",
            "prometheus_targets");
  }
}

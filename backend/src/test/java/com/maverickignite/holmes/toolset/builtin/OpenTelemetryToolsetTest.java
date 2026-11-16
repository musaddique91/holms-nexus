package com.maverickignite.holmes.toolset.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.openobserve.OpenObserveClient;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenTelemetryToolsetTest {

  @Test
  void disabledByDefault() {
    HolmesProperties props = new HolmesProperties();
    OpenObserveClient oo = mock(OpenObserveClient.class);
    when(oo.isConfigured()).thenReturn(false);

    OpenTelemetryToolset toolset = new OpenTelemetryToolset(props, oo, RestClient.builder());
    assertThat(toolset.name()).isEqualTo("opentelemetry");
    assertThat(toolset.enabled()).isFalse();
  }

  @Test
  void registersOtelToolsWhenEnabled() {
    HolmesProperties props = new HolmesProperties();
    props.getToolsets().getOpentelemetry().setEnabled(true);
    props.getToolsets().getOpentelemetry().setUrl("http://localhost:5080");

    OpenObserveClient oo = mock(OpenObserveClient.class);
    when(oo.isConfigured()).thenReturn(true);

    OpenTelemetryToolset toolset = new OpenTelemetryToolset(props, oo, RestClient.builder());
    assertThat(toolset.enabled()).isTrue();

    List<String> toolNames = toolset.tools().stream().map(ToolSpec::name).toList();
    assertThat(toolNames)
        .containsExactlyInAnyOrder("otel_trace_by_id", "otel_search_error_traces");
  }
}

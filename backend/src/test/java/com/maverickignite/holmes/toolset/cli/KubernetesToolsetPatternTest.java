package com.maverickignite.holmes.toolset.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

class KubernetesToolsetPatternTest {

  @Test
  void kindPatternExcludesSecrets() throws Exception {
    JsonNode root =
        YAMLMapper.builder().build().readTree(getClass().getResourceAsStream("/toolsets/kubernetes.yaml"));
    Pattern kind = Pattern.compile(root.path("tools").get(0).path("parameters").path("kind").path("pattern").asString());
    assertThat(kind.matcher("pods").matches()).isTrue();
    assertThat(kind.matcher("deployments").matches()).isTrue();
    assertThat(kind.matcher("secrets").matches()).isFalse();
    assertThat(kind.matcher("Secret").matches()).isFalse();
  }
}

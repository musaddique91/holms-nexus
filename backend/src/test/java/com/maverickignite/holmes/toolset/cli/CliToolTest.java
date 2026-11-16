package com.maverickignite.holmes.toolset.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maverickignite.holmes.toolset.ToolException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

class CliToolTest {

  private final YAMLMapper yaml = YAMLMapper.builder().build();

  private CliTool tool() {
    JsonNode command =
        yaml.readTree(
            """
            - kubectl
            - get
            - "${kind}"
            - {if: namespace, args: ["-n", "${namespace}"]}
            - {unless: namespace, args: ["-A"]}
            - "--tail=${tail}"
            """);
    return new CliTool(
        "kubectl_get",
        "",
        List.of(
            new CliTool.Param("kind", "string", "", true, null, null, List.of()),
            new CliTool.Param("namespace", "string", "", false, null, null, List.of()),
            new CliTool.Param("tail", "integer", "", false, "200", null, List.of())),
        command);
  }

  @Test
  void rendersOptionalBlocksAndDefaults() {
    assertThat(tool().render(yaml.readTree("{kind: pods, namespace: prod}")))
        .containsExactly("kubectl", "get", "pods", "-n", "prod", "--tail=200");
    assertThat(tool().render(yaml.readTree("{kind: pods}")))
        .containsExactly("kubectl", "get", "pods", "-A", "--tail=200");
  }

  @Test
  void rejectsOptionAndShellInjection() {
    assertThatThrownBy(() -> tool().render(yaml.readTree("{kind: '--kubeconfig=/etc/x'}")))
        .isInstanceOf(ToolException.class);
    assertThatThrownBy(() -> tool().render(yaml.readTree("{kind: 'pods; rm -rf /'}")))
        .isInstanceOf(ToolException.class);
    assertThatThrownBy(() -> tool().render(yaml.readTree("{kind: pods, tail: '10 && x'}")))
        .isInstanceOf(ToolException.class);
    // AWS CLI expands file:// / fileb://, Azure CLI expands @file: both would read local files.
    assertThatThrownBy(() -> tool().render(yaml.readTree("{kind: 'file:///proc/self/environ'}")))
        .isInstanceOf(ToolException.class);
    assertThatThrownBy(() -> tool().render(yaml.readTree("{kind: pods, namespace: '@/etc/passwd'}")))
        .isInstanceOf(ToolException.class);
  }

  @Test
  void requiresMandatoryArguments() {
    assertThatThrownBy(() -> tool().render(yaml.readTree("{}")))
        .isInstanceOf(ToolException.class)
        .hasMessageContaining("kind");
  }
}

package com.maverickignite.holmes.toolset.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandRunnerTest {

  @Test
  void childProcessesGetOnlyAllowListedEnvironment() throws Exception {
    CommandRunner.Result r = CommandRunner.run(List.of("env"), Map.of("EXTRA_FOR_TOOL", "1"), Duration.ofSeconds(10));
    List<String> keys = Arrays.stream(r.output().split("\n")).map(l -> l.split("=", 2)[0]).toList();
    assertThat(keys).contains("PATH", "EXTRA_FOR_TOOL");
    assertThat(keys)
        .allMatch(
            k -> List.of("PATH", "HOME", "LANG", "LC_ALL", "TZ", "TMPDIR", "KUBECONFIG", "DOCKER_HOST",
                        "DOCKER_CONFIG", "DOCKER_CONTEXT", "HTTPS_PROXY", "HTTP_PROXY", "NO_PROXY", "EXTRA_FOR_TOOL", "PWD", "SHLVL", "_")
                    .contains(k)
                || k.startsWith("AWS_") || k.startsWith("AZURE_"));
  }

  @Test
  void killsCommandsThatOutliveTheirTimeout() throws Exception {
    CommandRunner.Result r = CommandRunner.run(List.of("sleep", "5"), Map.of(), Duration.ofMillis(300));
    assertThat(r.timedOut()).isTrue();
  }
}

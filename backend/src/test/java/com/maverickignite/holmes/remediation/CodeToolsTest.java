package com.maverickignite.holmes.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CodeToolsTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @TempDir Path repo;

  private ToolSpec tool(String name) {
    return CodeTools.readWrite(repo, null).stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
  }

  @Test
  void editsUniqueSnippet() throws Exception {
    Files.createDirectories(repo.resolve("src"));
    Files.writeString(repo.resolve("src/A.java"), "class A {\n  int x = 1;\n}\n");
    tool("repo_edit").handler().invoke(json.readTree("{\"path\":\"src/A.java\",\"old_text\":\"int x = 1;\",\"new_text\":\"int x = 2;\"}"));
    assertThat(Files.readString(repo.resolve("src/A.java"))).contains("int x = 2;");
  }

  @Test
  void refusesPathsOutsideRepository() {
    var read = tool("repo_read");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> read.handler().invoke(json.readTree("{\"path\":\"../../etc/passwd\"}")))
        .isInstanceOf(ToolException.class)
        .hasMessageContaining("escapes");
  }

  @Test
  void refusesProtectedFiles() {
    var write = tool("repo_write");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> write.handler().invoke(json.readTree("{\"path\":\".github/workflows/x.yml\",\"content\":\"x\"}")))
        .isInstanceOf(ToolException.class);
    assertThat(CodeTools.isProtected("backend/Dockerfile")).isTrue();
    assertThat(CodeTools.isProtected("docker-compose-jenkins.yaml")).isTrue();
    assertThat(CodeTools.isProtected("backend/src/main/java/A.java")).isFalse();
  }

  @Test
  void safetyRulesRejectLargeOrProtectedChanges() {
    assertThat(RemediationService.safetyViolation(Set.of("backend/src/main/java/A.java"), "backend")).isNull();
    assertThat(RemediationService.safetyViolation(Set.of("backend/src/main/java/A.java", "Jenkinsfile"), "backend"))
        .contains("may not modify");
    Set<String> many = new java.util.TreeSet<>();
    for (int i = 0; i < 11; i++) {
      many.add("src/F" + i + ".java");
    }
    assertThat(RemediationService.safetyViolation(many, null)).contains("11 files");
  }

  @Test
  void onlySourceFilesInsideTheCodePathMayChange() {
    assertThat(CodeTools.refusal("backend/src/main/java/a/B.java", "backend")).isNull();
    assertThat(CodeTools.refusal("backend/src/test/java/a/BTest.java", "backend")).isNull();
    assertThat(CodeTools.refusal("backend/src/main/resources/db/changelog/002.sql", "backend")).isNull();
    assertThat(CodeTools.refusal("frontend/src/App.tsx", "backend")).contains("outside");
    assertThat(CodeTools.refusal("backend/pom.xml", "backend")).isNotNull();
    assertThat(CodeTools.refusal("package.json", null)).isNotNull();
    assertThat(CodeTools.refusal("src/main/run.sh", null)).isNotNull();
    assertThat(CodeTools.refusal(".circleci/config.yml", null)).isNotNull();
    assertThat(CodeTools.refusal("src/.hidden/A.java", null)).contains("hidden");
    assertThat(CodeTools.refusal("compose.yaml", null)).isNotNull();
    assertThat(CodeTools.refusal("README.md", null)).contains("src/");
  }

  @Test
  void symlinksCannotEscapeTheClone(@TempDir Path outside) throws Exception {
    Files.createDirectories(repo.resolve("src"));
    Files.createSymbolicLink(repo.resolve("src/link"), outside);
    Files.writeString(outside.resolve("secret.txt"), "TOKEN=abc");
    var write = tool("repo_write");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> write.handler().invoke(json.readTree("{\"path\":\"src/link/new.java\",\"content\":\"x\"}")))
        .isInstanceOf(ToolException.class);
    assertThat(Files.exists(outside.resolve("new.java"))).isFalse();
    var read = tool("repo_read");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> read.handler().invoke(json.readTree("{\"path\":\"src/link/secret.txt\"}")))
        .isInstanceOf(ToolException.class);
    String found = tool("repo_search").handler().invoke(json.readTree("{\"pattern\":\"TOKEN\"}"));
    assertThat(found).isEqualTo("No matches.");
  }

  @Test
  void searchFindsMatchesWithLineNumbers() throws Exception {
    Files.createDirectories(repo.resolve("src"));
    Files.writeString(repo.resolve("src/OrderService.java"), "a\nfindByTenant(id)\n");
    String out = tool("repo_search").handler().invoke(json.readTree("{\"pattern\":\"findByTenant\"}"));
    assertThat(out).contains("src/OrderService.java:2:");
    assertThat(List.of(out.split("\n"))).hasSize(1);
  }
}

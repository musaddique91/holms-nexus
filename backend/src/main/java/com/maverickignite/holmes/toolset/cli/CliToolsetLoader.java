package com.maverickignite.holmes.toolset.cli;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Toolset;
import com.maverickignite.holmes.toolset.ToolsetProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Loads CLI toolsets from {@code classpath:toolsets/*.yaml} and the optional custom directory.
 * Adding an integration that has a CLI (helm, gcloud, oc...) is a YAML file, not Java code.
 */
@Slf4j
@Component
class CliToolsetLoader implements ToolsetProvider {

  private final HolmesProperties.Toolsets config;
  private final YAMLMapper yaml = YAMLMapper.builder().build();

  CliToolsetLoader(HolmesProperties properties) {
    this.config = properties.getToolsets();
  }

  @Override
  public List<Toolset> toolsets() {
    List<Toolset> out = new ArrayList<>();
    try {
      Resource[] resources =
          new PathMatchingResourcePatternResolver().getResources("classpath*:toolsets/*.yaml");
      for (Resource r : resources) {
        try (InputStream in = r.getInputStream()) {
          out.add(parse(yaml.readTree(in), r.getFilename()));
        }
      }
      if (!Texts.isBlank(config.getCustomDir())) {
        Path dir = Path.of(config.getCustomDir());
        if (Files.isDirectory(dir)) {
          try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
              out.add(parse(yaml.readTree(f.toFile()), f.toString()));
            }
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load CLI toolsets", e);
    }
    return out;
  }

  private Toolset parse(JsonNode root, String source) {
    String name = root.path("name").asString();
    if (Texts.isBlank(name)) {
      throw new IllegalStateException("Toolset in " + source + " has no name");
    }
    HolmesProperties.CliToolset settings =
        config.getCli().getOrDefault(name, new HolmesProperties.CliToolset());
    List<CliTool> tools = new ArrayList<>();
    for (JsonNode t : root.path("tools")) {
      List<CliTool.Param> params = new ArrayList<>();
      for (var entry : t.path("parameters").properties()) {
        JsonNode p = entry.getValue();
        List<String> allowed = new ArrayList<>();
        p.path("enum").forEach(v -> allowed.add(v.asString()));
        String pattern = p.path("pattern").asString(null);
        params.add(
            new CliTool.Param(
                entry.getKey(),
                p.path("type").asString("string"),
                p.path("description").asString(""),
                p.path("required").asBoolean(false),
                p.has("default") ? p.path("default").asString() : null,
                pattern == null ? null : Pattern.compile(pattern),
                allowed));
      }
      tools.add(
          new CliTool(
              t.path("name").asString(),
              t.path("description").asString(),
              params,
              t.path("command")));
    }
    log.debug("Loaded CLI toolset {} ({} tools) from {}", name, tools.size(), source);
    return new CliToolset(
        name, root.path("description").asString(), root.path("binary").asString(), settings, tools);
  }
}

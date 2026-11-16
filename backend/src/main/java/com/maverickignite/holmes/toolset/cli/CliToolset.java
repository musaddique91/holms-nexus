package com.maverickignite.holmes.toolset.cli;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.util.List;
import java.util.stream.Collectors;

/** A toolset whose tools are command templates declared in YAML. */
final class CliToolset implements Toolset {

  private final String name;
  private final String description;
  private final String binary;
  private final HolmesProperties.CliToolset settings;
  private final List<CliTool> tools;

  CliToolset(
      String name,
      String description,
      String binary,
      HolmesProperties.CliToolset settings,
      List<CliTool> tools) {
    this.name = name;
    this.description = description;
    this.binary = binary;
    this.settings = settings;
    this.tools = List.copyOf(tools);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public boolean enabled() {
    return settings.isEnabled();
  }

  @Override
  public List<ToolSpec> tools() {
    return tools.stream().map(t -> t.spec(settings)).collect(Collectors.toList());
  }

  @Override
  public ToolsetHealth health() {
    if (!CommandRunner.onPath(binary)) {
      return ToolsetHealth.failed("'" + binary + "' is not installed in the Holmes container");
    }
    return ToolsetHealth.ok("'" + binary + "' available");
  }
}

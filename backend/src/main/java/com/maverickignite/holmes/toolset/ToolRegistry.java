package com.maverickignite.holmes.toolset;

import com.maverickignite.holmes.toolset.Toolset.ToolsetHealth;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** All toolsets known to Holmes, and the tools currently offered to the model. */
@Slf4j
@Component
public class ToolRegistry {

  private static final Duration HEALTH_CACHE = Duration.ofSeconds(60);

  private final List<Toolset> toolsets;
  private final Map<String, CachedHealth> healthCache = new ConcurrentHashMap<>();

  private record CachedHealth(ToolsetHealth health, Instant at) {}

  public record ToolsetStatus(
      String name,
      String description,
      boolean enabled,
      Boolean healthy,
      String message,
      List<String> tools) {}

  public ToolRegistry(List<Toolset> toolsets, List<ToolsetProvider> providers) {
    List<Toolset> all = new ArrayList<>(toolsets);
    providers.forEach(p -> all.addAll(p.toolsets()));
    this.toolsets = List.copyOf(all);
    Set<String> seen = new HashSet<>();
    for (Toolset t : this.toolsets) {
      if (t.enabled()) {
        for (ToolSpec spec : t.tools()) {
          if (!seen.add(spec.name())) {
            throw new IllegalStateException("Duplicate tool name: " + spec.name());
          }
        }
      }
    }
    log.info(
        "Toolsets enabled: {}",
        this.toolsets.stream().filter(Toolset::enabled).map(Toolset::name).toList());
  }

  public List<ToolSpec> activeTools() {
    List<ToolSpec> tools = new ArrayList<>();
    for (Toolset t : toolsets) {
      if (t.enabled()) {
        tools.addAll(t.tools());
      }
    }
    return tools;
  }

  public List<ToolsetStatus> status(boolean probe) {
    return toolsets.stream()
        .map(
            t -> {
              ToolsetHealth health = t.enabled() && probe ? health(t) : null;
              return new ToolsetStatus(
                  t.name(),
                  t.description(),
                  t.enabled(),
                  health == null ? null : health.healthy(),
                  health == null ? null : health.message(),
                  t.tools().stream().map(ToolSpec::name).toList());
            })
        .toList();
  }

  private ToolsetHealth health(Toolset t) {
    CachedHealth cached = healthCache.get(t.name());
    if (cached != null && cached.at().plus(HEALTH_CACHE).isAfter(Instant.now())) {
      return cached.health();
    }
    ToolsetHealth health;
    try {
      health = t.health();
    } catch (RuntimeException e) {
      health = ToolsetHealth.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    healthCache.put(t.name(), new CachedHealth(health, Instant.now()));
    return health;
  }
}

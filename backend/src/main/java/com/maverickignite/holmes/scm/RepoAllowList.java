package com.maverickignite.holmes.scm;

import com.maverickignite.holmes.catalog.MonitoredService;
import com.maverickignite.holmes.catalog.ServiceCatalog;
import com.maverickignite.holmes.toolset.ToolException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The git read tools may only look at repositories registered on the Services page, so a
 * prompt-injected agent cannot browse every private repository the token can reach.
 */
@Component
@RequiredArgsConstructor
public class RepoAllowList {

  private final ServiceCatalog catalog;

  public String require(String repoPath) {
    if (repoPath == null || repoPath.contains("..")) {
      throw new ToolException("Invalid repository");
    }
    Set<String> allowed =
        catalog.findAll().stream()
            .filter(MonitoredService::hasRepository)
            .map(s -> RepoCoordinates.parse(s.getRepoUrl()).path().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    if (!allowed.contains(repoPath.toLowerCase(Locale.ROOT))) {
      throw new ToolException("Repository " + repoPath + " is not a registered service. Known: " + allowed);
    }
    return repoPath;
  }

  public static String safeRef(String value) {
    if (value != null && (value.contains("..") || value.startsWith("/") || value.startsWith("-"))) {
      throw new ToolException("Invalid path or ref");
    }
    return value;
  }
}

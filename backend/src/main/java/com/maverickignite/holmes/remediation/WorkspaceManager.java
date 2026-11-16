package com.maverickignite.holmes.remediation;

import com.maverickignite.holmes.catalog.GitProviderType;
import com.maverickignite.holmes.catalog.MonitoredService;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.scm.GitTokens;
import java.io.IOException;
import java.net.URI;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

/** Clones a service's repository into a private temporary directory. */
@Slf4j
@Component
public class WorkspaceManager {

  private final Path baseDir;
  private final GitTokens tokens;
  private final String githubHost;
  private final String gitlabHost;

  public WorkspaceManager(HolmesProperties properties, GitTokens tokens) throws IOException {
    this.baseDir = Path.of(properties.getWorkspaceDir());
    this.tokens = tokens;
    String apiHost = URI.create(properties.getGit().getGithub().getApiUrl()).getHost();
    // api.github.com serves github.com; GitHub Enterprise serves the API on the same host.
    this.githubHost = "api.github.com".equals(apiHost) ? "github.com" : apiHost;
    this.gitlabHost = URI.create(properties.getGit().getGitlab().getUrl()).getHost();
    Files.createDirectories(baseDir);
  }

  /** Whether Holmes holds credentials for this service's git provider. */
  public boolean canAccess(MonitoredService service) {
    if (service == null || !service.hasRepository()) {
      return false;
    }
    return switch (service.getGitProvider()) {
      case GITHUB, GITLAB -> tokens.forService(service) != null;
      case NONE -> false;
    };
  }

  public RepoWorkspace checkout(MonitoredService service, String purpose)
      throws IOException, GitAPIException {
    requireTrustedHost(service);
    Path dir = Files.createTempDirectory(baseDir, Texts.firstNonBlank(purpose, "ws") + "-");
    CredentialsProvider credentials = credentials(service);
    log.info("Cloning {} ({}) into {}", service.getRepoUrl(), service.getDefaultBranch(), dir);
    try {
      Git git =
          Git.cloneRepository()
              .setURI(service.getRepoUrl())
              .setDirectory(dir.toFile())
              .setBranch(service.getDefaultBranch())
              .setCloneAllBranches(false)
              .setDepth(1)
              .setCredentialsProvider(credentials)
              .setTimeout(120)
              .call();
      return new RepoWorkspace(git, dir, credentials, service.getDefaultBranch());
    } catch (GitAPIException | RuntimeException e) {
      deleteQuietly(dir);
      throw e;
    }
  }

  /** The git token is only ever sent to the configured GitHub / GitLab host over https. */
  private void requireTrustedHost(MonitoredService service) {
    URI uri;
    try {
      uri = URI.create(service.getRepoUrl());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid repository URL: " + service.getRepoUrl());
    }
    String expected = service.getGitProvider() == GitProviderType.GITLAB ? gitlabHost : githubHost;
    boolean ok =
        "https".equalsIgnoreCase(uri.getScheme())
            && uri.getUserInfo() == null
            && uri.getHost() != null
            && uri.getHost().toLowerCase(Locale.ROOT).equals(expected);
    if (!ok) {
      throw new IllegalArgumentException(
          "Repository URL must be https://" + expected + "/... for " + service.getGitProvider());
    }
  }

  private static void deleteQuietly(Path dir) {
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    } catch (IOException e) {
      log.warn("Could not clean {}: {}", dir, e.getMessage());
    }
  }

  /** The service's own token when set, else the global one (see GitTokens). */
  private CredentialsProvider credentials(MonitoredService service) {
    String token = tokens.forService(service);
    if (token == null) {
      throw new IllegalStateException("No git token for " + service.getName());
    }
    return switch (service.getGitProvider()) {
      case GITHUB -> new UsernamePasswordCredentialsProvider("x-access-token", token);
      case GITLAB -> new UsernamePasswordCredentialsProvider("oauth2", token);
      case NONE -> throw new IllegalArgumentException("Service has no git provider");
    };
  }
}

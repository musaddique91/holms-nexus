package com.maverickignite.holmes.scm;

import com.maverickignite.holmes.catalog.GitProviderType;
import com.maverickignite.holmes.catalog.MonitoredService;
import com.maverickignite.holmes.catalog.ServiceCatalog;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.settings.SecretCipher;
import org.springframework.stereotype.Component;

/**
 * Which git token to use for a service: its own personal access token (Services page, stored
 * encrypted) when set, otherwise the global GITHUB_TOKEN / GITLAB_TOKEN. The same token is used to
 * read the code (clone, read tools) and to open the fix pull request.
 */
@Component
public class GitTokens {

  private final ServiceCatalog catalog;
  private final SecretCipher cipher;
  private final HolmesProperties.Git git;

  public GitTokens(ServiceCatalog catalog, SecretCipher cipher, HolmesProperties properties) {
    this.catalog = catalog;
    this.cipher = cipher;
    this.git = properties.getGit();
  }

  /** The token for {@code service}, or null when Holmes has none for its provider. */
  public String forService(MonitoredService service) {
    if (service == null || service.getGitProvider() == GitProviderType.NONE) {
      return null;
    }
    String own = cipher.decrypt(service.getGitTokenEncrypted());
    return !Texts.isBlank(own) ? own : global(service.getGitProvider());
  }

  public String global(GitProviderType provider) {
    String token =
        switch (provider) {
          case GITHUB -> git.getGithub().getToken();
          case GITLAB -> git.getGitlab().getToken();
          case NONE -> null;
        };
    return Texts.isBlank(token) ? null : token;
  }

  /**
   * Token for a registered repository (read tools): the own token of a service using that repo,
   * else the global one. Call only after {@link RepoAllowList#require}.
   */
  public String forRepo(String repoPath, GitProviderType provider) {
    return catalog.findAll().stream()
        .filter(s -> s.getGitProvider() == provider && s.hasRepository())
        .filter(s -> RepoCoordinates.parse(s.getRepoUrl()).path().equalsIgnoreCase(repoPath))
        .map(s -> cipher.decrypt(s.getGitTokenEncrypted()))
        .filter(t -> !Texts.isBlank(t))
        .findFirst()
        .orElseGet(() -> global(provider));
  }

  /** Any usable token for {@code provider}: the global one, else the first service's own token. */
  public String any(GitProviderType provider) {
    String global = global(provider);
    if (global != null) {
      return global;
    }
    return catalog.findAll().stream()
        .filter(s -> s.getGitProvider() == provider)
        .map(s -> cipher.decrypt(s.getGitTokenEncrypted()))
        .filter(t -> !Texts.isBlank(t))
        .findFirst()
        .orElse(null);
  }

  /** Masked preview for the Services page, e.g. {@code ghp_…a1b2}. */
  public static String hint(String token) {
    int underscore = token.indexOf('_');
    String prefix = underscore > 0 && underscore <= 12 ? token.substring(0, underscore + 1) : "";
    return prefix + "…" + token.substring(Math.max(0, token.length() - 4));
  }
}

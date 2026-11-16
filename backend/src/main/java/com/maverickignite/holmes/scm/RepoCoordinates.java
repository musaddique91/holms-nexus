package com.maverickignite.holmes.scm;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "owner/name" (GitHub) or "group/sub/name" (GitLab) parsed from a clone URL. */
public record RepoCoordinates(String host, String path) {

  private static final Pattern SSH = Pattern.compile("^[\\w.-]+@([\\w.-]+):(.+?)(?:\\.git)?/?$");

  public static RepoCoordinates parse(String repoUrl) {
    if (repoUrl == null) {
      throw new IllegalArgumentException("Repository URL is empty");
    }
    Matcher ssh = SSH.matcher(repoUrl.trim());
    if (ssh.matches()) {
      return new RepoCoordinates(ssh.group(1), ssh.group(2));
    }
    URI uri = URI.create(repoUrl.trim());
    String p = uri.getPath().replaceFirst("^/", "").replaceFirst("\\.git/?$", "").replaceFirst("/$", "");
    if (uri.getHost() == null || p.isEmpty() || !p.contains("/")) {
      throw new IllegalArgumentException("Not a repository URL: " + repoUrl);
    }
    return new RepoCoordinates(uri.getHost(), p);
  }
}

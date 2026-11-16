package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.catalog.GitProviderType;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.scm.GitHubApi;
import com.maverickignite.holmes.scm.GitTokens;
import com.maverickignite.holmes.scm.RepoAllowList;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Recent changes on GitHub, to correlate an error with the commit that introduced it. */
@Component
class GitHubToolset implements Toolset {

  private static final Pattern REPO = Pattern.compile("[\\w.-]+/[\\w.-]+");
  private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,40}");

  private final GitHubApi api;
  private final RepoAllowList allowList;

  private final GitTokens tokens;

  GitHubToolset(GitHubApi api, RepoAllowList allowList, GitTokens tokens) {
    this.api = api;
    this.allowList = allowList;
    this.tokens = tokens;
  }

  @Override
  public String name() {
    return "github";
  }

  @Override
  public String description() {
    return "GitHub commits, diffs, files and pull requests.";
  }

  @Override
  public boolean enabled() {
    return tokens.any(GitProviderType.GITHUB) != null;
  }

  @Override
  public List<ToolSpec> tools() {
    return List.of(
        new ToolSpec(
            "github_recent_commits",
            "Latest commits on a branch, optionally only those touching a path. Use it to find a"
                + " recent change that could explain a new error.",
            Schemas.object()
                .string("repo", "owner/name", true)
                .string("branch", "Branch (default: repository default branch)", false)
                .string("path", "Only commits touching this file or directory", false)
                .build(),
            args -> {
              StringBuilder sb = new StringBuilder();
              JsonNode commits =
                  api.get(
                      token(args),
                      "/repos/" + repo(args) + "/commits?per_page=15&sha={sha}&path={path}",
                      Texts.firstNonBlank(RepoAllowList.safeRef(Args.optional(args, "branch")), ""),
                      Texts.firstNonBlank(RepoAllowList.safeRef(Args.optional(args, "path")), ""));
              for (JsonNode c : commits) {
                sb.append(c.path("sha").asString(), 0, 10).append(' ')
                    .append(c.path("commit").path("author").path("date").asString()).append(' ')
                    .append(c.path("commit").path("author").path("name").asString()).append(": ")
                    .append(c.path("commit").path("message").asString().lines().findFirst().orElse(""))
                    .append('\n');
              }
              return sb.toString();
            }),
        new ToolSpec(
            "github_commit_diff",
            "Files and patch of one commit.",
            Schemas.object()
                .string("repo", "owner/name", true)
                .string("sha", "Commit sha", true)
                .build(),
            args -> {
              String sha = Args.required(args, "sha");
              if (!SHA.matcher(sha).matches()) {
                throw new ToolException("Invalid sha");
              }
              JsonNode commit = api.get(token(args), "/repos/" + repo(args) + "/commits/" + sha);
              StringBuilder sb =
                  new StringBuilder(commit.path("commit").path("message").asString()).append("\n\n");
              for (JsonNode f : commit.path("files")) {
                sb.append("--- ").append(f.path("filename").asString()).append(" (")
                    .append(f.path("status").asString()).append(")\n")
                    .append(f.path("patch").asString("")).append("\n");
              }
              return sb.toString();
            }),
        new ToolSpec(
            "github_get_file",
            "Raw content of a file at a ref.",
            Schemas.object()
                .string("repo", "owner/name", true)
                .string("path", "File path in the repository", true)
                .string("ref", "Branch, tag or sha (default: default branch)", false)
                .build(),
            args ->
                api.getRaw(
                    token(args),
                    "/repos/" + repo(args) + "/contents/{path}?ref={ref}",
                    RepoAllowList.safeRef(Args.required(args, "path")),
                    Texts.firstNonBlank(RepoAllowList.safeRef(Args.optional(args, "ref")), ""))),
        new ToolSpec(
            "github_open_pull_requests",
            "Open pull requests, e.g. to see whether a fix is already in progress.",
            Schemas.object().string("repo", "owner/name", true).build(),
            args -> {
              StringBuilder sb = new StringBuilder();
              for (JsonNode pr : api.get(token(args), "/repos/" + repo(args) + "/pulls?state=open&per_page=20")) {
                sb.append('#').append(pr.path("number").asInt()).append(' ')
                    .append(pr.path("title").asString()).append(" (")
                    .append(pr.path("head").path("ref").asString()).append(")\n");
              }
              return sb.isEmpty() ? "No open pull requests." : sb.toString();
            }));
  }

  private String repo(JsonNode args) {
    String repo = Args.required(args, "repo");
    if (!REPO.matcher(repo).matches()) {
      throw new ToolException("repo must look like owner/name");
    }
    return allowList.require(repo);
  }

  /** The token of the service that owns the repo (validated by repo() first), else the global one. */
  private String token(JsonNode args) {
    return tokens.forRepo(repo(args), GitProviderType.GITHUB);
  }

  @Override
  public ToolsetHealth health() {
    return ToolsetHealth.ok(
        "Authenticated as " + api.user(tokens.any(GitProviderType.GITHUB)).path("login").asString());
  }
}

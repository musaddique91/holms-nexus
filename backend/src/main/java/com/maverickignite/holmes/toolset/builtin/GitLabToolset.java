package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.catalog.GitProviderType;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.scm.GitLabApi;
import com.maverickignite.holmes.scm.GitTokens;
import com.maverickignite.holmes.scm.RepoAllowList;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Recent changes and pipelines on GitLab. */
@Component
class GitLabToolset implements Toolset {

  private static final Pattern PROJECT = Pattern.compile("[\\w.-]+(/[\\w.-]+)+");
  private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,40}");

  private final GitLabApi api;
  private final RepoAllowList allowList;

  private final GitTokens tokens;

  GitLabToolset(GitLabApi api, RepoAllowList allowList, GitTokens tokens) {
    this.api = api;
    this.allowList = allowList;
    this.tokens = tokens;
  }

  @Override
  public String name() {
    return "gitlab";
  }

  @Override
  public String description() {
    return "GitLab commits, diffs, files and CI pipelines.";
  }

  @Override
  public boolean enabled() {
    return tokens.any(GitProviderType.GITLAB) != null;
  }

  @Override
  public List<ToolSpec> tools() {
    return List.of(
        new ToolSpec(
            "gitlab_recent_commits",
            "Latest commits on a branch, optionally only those touching a path.",
            Schemas.object()
                .string("project", "group/name", true)
                .string("ref", "Branch (default: default branch)", false)
                .string("path", "Only commits touching this path", false)
                .build(),
            args -> {
              Map<String, String> q = new LinkedHashMap<>();
              q.put("per_page", "15");
              q.put("ref_name", RepoAllowList.safeRef(Args.optional(args, "ref")));
              q.put("path", RepoAllowList.safeRef(Args.optional(args, "path")));
              StringBuilder sb = new StringBuilder();
              for (JsonNode c : api.get(token(args), api.uri(project(args), "/repository/commits", q))) {
                sb.append(c.path("short_id").asString()).append(' ')
                    .append(c.path("created_at").asString()).append(' ')
                    .append(c.path("author_name").asString()).append(": ")
                    .append(c.path("title").asString()).append('\n');
              }
              return sb.toString();
            }),
        new ToolSpec(
            "gitlab_commit_diff",
            "Diff of one commit.",
            Schemas.object()
                .string("project", "group/name", true)
                .string("sha", "Commit sha", true)
                .build(),
            args -> {
              String sha = Args.required(args, "sha");
              if (!SHA.matcher(sha).matches()) {
                throw new ToolException("Invalid sha");
              }
              StringBuilder sb = new StringBuilder();
              for (JsonNode d :
                  api.get(token(args), api.uri(project(args), "/repository/commits/" + sha + "/diff", null))) {
                sb.append("--- ").append(d.path("new_path").asString()).append('\n')
                    .append(d.path("diff").asString()).append('\n');
              }
              return sb.toString();
            }),
        new ToolSpec(
            "gitlab_get_file",
            "Raw content of a file at a ref.",
            Schemas.object()
                .string("project", "group/name", true)
                .string("path", "File path", true)
                .string("ref", "Branch, tag or sha", true)
                .build(),
            args ->
                api.getRaw(
                    token(args),
                    api.uri(
                        project(args),
                        "/repository/files/" + GitLabApi.encode(RepoAllowList.safeRef(Args.required(args, "path"))) + "/raw",
                        Map.of("ref", RepoAllowList.safeRef(Args.required(args, "ref")))))),
        new ToolSpec(
            "gitlab_pipelines",
            "Latest CI pipelines with status, to spot failed or in-progress deploys.",
            Schemas.object().string("project", "group/name", true).build(),
            args -> {
              StringBuilder sb = new StringBuilder();
              for (JsonNode p :
                  api.get(token(args), api.uri(project(args), "/pipelines", Map.of("per_page", "10")))) {
                sb.append(p.path("id").asLong()).append(' ')
                    .append(p.path("status").asString()).append(' ')
                    .append(p.path("ref").asString()).append(' ')
                    .append(Texts.firstNonBlank(p.path("updated_at").asString(), "")).append('\n');
              }
              return sb.toString();
            }));
  }

  private String project(JsonNode args) {
    String p = Args.required(args, "project");
    if (!PROJECT.matcher(p).matches()) {
      throw new ToolException("project must look like group/name");
    }
    return allowList.require(p);
  }

  @Override
  public ToolsetHealth health() {
    return ToolsetHealth.ok(
        "Authenticated as " + api.user(tokens.any(GitProviderType.GITLAB)).path("username").asString());
  }

  /** The token of the service that owns the project (validated by project() first), else the global one. */
  private String token(JsonNode args) {
    return tokens.forRepo(project(args), GitProviderType.GITLAB);
  }
}

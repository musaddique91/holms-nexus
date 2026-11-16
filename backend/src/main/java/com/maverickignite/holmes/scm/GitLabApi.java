package com.maverickignite.holmes.scm;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * GitLab REST API v4 (gitlab.com or self-managed via holmes.git.gitlab.url). Every call takes the
 * token to use (see {@link GitTokens}), so each service can have its own.
 */
@Component
public class GitLabApi {

  private final HolmesProperties.GitLab config;
  private final RestClient http;

  public GitLabApi(HolmesProperties properties, RestClient.Builder builder) {
    this.config = properties.getGit().getGitlab();
    this.http = builder.clone().requestFactory(HttpClients.standard()).build();
  }

  /** Builds an already-encoded URI; GitLab wants project paths and file paths as %2F-encoded ids. */
  public URI uri(String project, String suffix, Map<String, String> query) {
    StringBuilder sb =
        new StringBuilder(config.getUrl().replaceFirst("/$", ""))
            .append("/api/v4/projects/")
            .append(encode(project))
            .append(suffix);
    if (query != null && !query.isEmpty()) {
      sb.append('?');
      query.forEach(
          (k, v) -> {
            if (v != null) {
              sb.append(encode(k)).append('=').append(encode(v)).append('&');
            }
          });
      sb.setLength(sb.length() - 1);
    }
    return URI.create(sb.toString());
  }

  public static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public JsonNode get(String token, URI uri) {
    return http.get().uri(uri).headers(h -> auth(h, token)).retrieve().body(JsonNode.class);
  }

  public String getRaw(String token, URI uri) {
    return http.get().uri(uri).headers(h -> auth(h, token)).retrieve().body(String.class);
  }

  public JsonNode user(String token) {
    return http.get()
        .uri(URI.create(config.getUrl().replaceFirst("/$", "") + "/api/v4/user"))
        .headers(h -> auth(h, token))
        .retrieve()
        .body(JsonNode.class);
  }

  public PullRequestRef createMergeRequest(
      String token,
      RepoCoordinates repo, String source, String target, String title, String body, boolean draft) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("source_branch", source);
    request.put("target_branch", target);
    request.put("title", draft ? "Draft: " + title : title);
    request.put("description", body);
    request.put("remove_source_branch", true);
    JsonNode mr =
        http.post()
            .uri(uri(repo.path(), "/merge_requests", null))
            .headers(h -> auth(h, token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(JsonNode.class);
    return new PullRequestRef(mr.path("web_url").asString(), mr.path("iid").asString());
  }

  private static void auth(HttpHeaders headers, String token) {
    if (Texts.isBlank(token)) {
      throw new IllegalStateException("No GitLab token: set one on the service or GITLAB_TOKEN");
    }
    headers.set("PRIVATE-TOKEN", token);
  }
}

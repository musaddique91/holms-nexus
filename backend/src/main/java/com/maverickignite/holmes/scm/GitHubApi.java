package com.maverickignite.holmes.scm;

import com.maverickignite.holmes.common.HttpClients;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * GitHub REST API (github.com or GitHub Enterprise via holmes.git.github.api-url). Every call takes
 * the token to use (see {@link GitTokens}), so each service can have its own.
 */
@Component
public class GitHubApi {

  private final HolmesProperties.GitHub config;
  private final RestClient http;

  public GitHubApi(HolmesProperties properties, RestClient.Builder builder) {
    this.config = properties.getGit().getGithub();
    RestClient.Builder b =
        builder
            .clone()
            .baseUrl(config.getApiUrl())
            .requestFactory(HttpClients.standard())
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
    this.http = b.build();
  }

  public JsonNode get(String token, String uriTemplate, Object... vars) {
    return http.get().uri(uriTemplate, vars).headers(h -> auth(h, token)).retrieve().body(JsonNode.class);
  }

  public String getRaw(String token, String uriTemplate, Object... vars) {
    return http.get()
        .uri(uriTemplate, vars)
        .headers(h -> auth(h, token))
        .header("Accept", "application/vnd.github.raw+json")
        .retrieve()
        .body(String.class);
  }

  public JsonNode user(String token) {
    return get(token, "/user");
  }

  public PullRequestRef createPullRequest(
      String token,
      RepoCoordinates repo, String head, String base, String title, String body, boolean draft) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("title", title);
    request.put("head", head);
    request.put("base", base);
    request.put("body", body);
    request.put("draft", draft);
    JsonNode pr =
        http.post()
            .uri("/repos/" + repo.path() + "/pulls")
            .headers(h -> auth(h, token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(JsonNode.class);
    return new PullRequestRef(pr.path("html_url").asString(), pr.path("number").asString());
  }

  private static void auth(HttpHeaders headers, String token) {
    if (Texts.isBlank(token)) {
      throw new IllegalStateException("No GitHub token: set one on the service or GITHUB_TOKEN");
    }
    headers.setBearerAuth(token);
  }
}

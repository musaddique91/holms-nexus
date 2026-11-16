package com.maverickignite.holmes.remediation;

import com.maverickignite.holmes.agent.AgentRunner;
import com.maverickignite.holmes.agent.AgentRunner.AgentResult;
import com.maverickignite.holmes.agent.AgentRunner.AgentTask;
import com.maverickignite.holmes.agent.JsonBlocks;
import com.maverickignite.holmes.catalog.GitProviderType;
import com.maverickignite.holmes.catalog.MonitoredService;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.incident.Incident;
import com.maverickignite.holmes.investigation.Investigation;
import com.maverickignite.holmes.investigation.Prompts;
import com.maverickignite.holmes.investigation.ToolCallRecorder;
import com.maverickignite.holmes.scm.GitHubApi;
import com.maverickignite.holmes.scm.GitLabApi;
import com.maverickignite.holmes.scm.GitTokens;
import com.maverickignite.holmes.scm.PullRequestRef;
import com.maverickignite.holmes.scm.RepoCoordinates;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.PersonIdent;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns an analysed incident into a draft pull request: an agent edits a fresh clone, Holmes
 * checks the change against safety rules, then commits, pushes a branch and opens the PR/MR.
 */
@Slf4j
@Service
public class RemediationService {

  private static final int MAX_FILES_CHANGED = 10;
  private static final int MAX_CHANGED_LINES = 400;
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

  private final AgentRunner agent;
  private final RemediationRepository repository;
  private final ToolCallRecorder recorder;
  private final GitHubApi github;
  private final GitLabApi gitlab;
  private final GitTokens tokens;
  private final HolmesProperties properties;
  private final ObjectMapper json;

  public RemediationService(
      AgentRunner agent,
      RemediationRepository repository,
      ToolCallRecorder recorder,
      GitHubApi github,
      GitLabApi gitlab,
      GitTokens tokens,
      HolmesProperties properties,
      ObjectMapper json) {
    this.agent = agent;
    this.repository = repository;
    this.recorder = recorder;
    this.github = github;
    this.gitlab = gitlab;
    this.tokens = tokens;
    this.properties = properties;
    this.json = json;
  }

  public Remediation attemptFix(
      Incident incident, MonitoredService service, Investigation investigation, RepoWorkspace ws) {
    HolmesProperties.Remediation config = properties.getRemediation();
    String model = agent.fixModel();

    Remediation r = new Remediation();
    r.setIncidentId(incident.getId());
    r.setInvestigationId(investigation.getId());
    r.setStatus(RemediationStatus.RUNNING);
    r.setModel(model);
    r.setStartedAt(Instant.now());
    r = repository.save(r);
    Long remediationId = r.getId();

    try {
      AgentResult result =
          agent.run(
              new AgentTask(
                  model,
                  Prompts.FIX_SYSTEM,
                  Prompts.fixPrompt(incident, service, investigation),
                  CodeTools.readWrite(ws.root(), service.getCodePath()),
                  config.getMaxSteps(),
                  (seq, tool, args, outcome) ->
                      recorder.record(null, remediationId, seq, tool, args, outcome)));
      r.setSteps(result.steps());
      r.setInputTokens(result.inputTokens());
      r.setOutputTokens(result.outputTokens());

      JsonNode verdict = JsonBlocks.last(result.finalText(), json);
      boolean applied = verdict != null && verdict.path("fixApplied").asBoolean(false);
      Set<String> changed = ws.changedFiles();

      if (!applied || changed.isEmpty()) {
        r.setStatus(RemediationStatus.NO_CHANGE);
        r.setReason(
            Texts.firstNonBlank(
                verdict == null ? null : verdict.path("reason").asString(null),
                changed.isEmpty() ? "The agent made no code changes." : "The agent did not confirm the fix.",
                result.finalText()));
        return finish(r);
      }

      String violation = safetyViolation(changed, service.getCodePath());
      if (violation == null) {
        for (String f : changed) {
          if (java.nio.file.Files.isSymbolicLink(ws.root().resolve(f))) {
            violation = "Change adds or modifies a symbolic link: " + f;
            break;
          }
        }
      }
      if (violation != null) {
        r.setStatus(RemediationStatus.REJECTED);
        r.setReason(violation);
        r.setFilesChanged(String.join(",", changed));
        return finish(r);
      }

      String title =
          Texts.truncate(
              Texts.firstNonBlank(verdict.path("title").asString(null), "Fix " + incident.getTitle()), 120);
      String commitMessage =
          Texts.firstNonBlank(verdict.path("commitMessage").asString(null), "fix: " + title)
              + "\n\nAutomated fix for holms-nexus incident #" + incident.getId() + ".";
      String description = verdict.path("description").asString("");

      String diff = ws.stageAll();
      long changedLines = diff.lines().filter(l -> (l.startsWith("+") || l.startsWith("-")) && !l.startsWith("+++") && !l.startsWith("---")).count();
      if (changedLines > MAX_CHANGED_LINES) {
        r.setStatus(RemediationStatus.REJECTED);
        r.setReason("Change is " + changedLines + " lines (limit " + MAX_CHANGED_LINES + "); too large for an automated fix");
        r.setFilesChanged(String.join(",", changed));
        r.setDiff(Texts.truncateMiddle(diff, 200_000));
        return finish(r);
      }
      r.setDiff(Texts.truncateMiddle(diff, 200_000));
      String branch = config.getBranchPrefix() + incident.getId() + "-" + STAMP.format(Instant.now());
      HolmesProperties.Git git = properties.getGit();
      ws.commitAndPush(branch, commitMessage, new PersonIdent(git.getAuthorName(), git.getAuthorEmail()));

      String body = pullRequestBody(incident, service, investigation, description, changed);
      PullRequestRef pr = openPullRequest(service, branch, ws.baseBranch(), title, body, config.isDraftPullRequests());

      r.setStatus(RemediationStatus.PR_OPENED);
      r.setBranch(branch);
      r.setTitle(title);
      r.setDescription(description);
      r.setFilesChanged(String.join(",", changed));
      r.setPrUrl(pr.url());
      r.setPrNumber(pr.number());
      log.info("Opened {} for incident #{}", pr.url(), incident.getId());
      return finish(r);
    } catch (Exception e) {
      log.warn("Remediation of incident #{} failed", incident.getId(), e);
      r.setStatus(RemediationStatus.FAILED);
      r.setError(Texts.truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 2_000));
      return finish(r);
    }
  }

  /** Re-checks the final change set: the agent's tools enforce the same rules, this is the gate. */
  static String safetyViolation(Set<String> changed, String codePath) {
    if (changed.size() > MAX_FILES_CHANGED) {
      return "Change touches " + changed.size() + " files (limit " + MAX_FILES_CHANGED + ")";
    }
    List<String> blocked =
        changed.stream()
            .filter(f -> CodeTools.refusal(f, codePath) != null)
            .map(f -> f + " (" + CodeTools.refusal(f, codePath) + ")")
            .toList();
    return blocked.isEmpty() ? null : "Change touches files Holmes may not modify: " + blocked;
  }

  private PullRequestRef openPullRequest(
      MonitoredService service, String branch, String base, String title, String body, boolean draft) {
    RepoCoordinates repo = RepoCoordinates.parse(service.getRepoUrl());
    // Same token that cloned the code: the service's own PAT, else the global one.
    String token = tokens.forService(service);
    if (service.getGitProvider() == GitProviderType.GITLAB) {
      return gitlab.createMergeRequest(token, repo, branch, base, title, body, draft);
    }
    return github.createPullRequest(token, repo, branch, base, title, body, draft);
  }

  private String pullRequestBody(
      Incident incident,
      MonitoredService service,
      Investigation inv,
      String description,
      Set<String> changed) {
    String link = properties.getDashboardUrl().replaceFirst("/$", "") + "/incidents/" + incident.getId();
    StringBuilder sb = new StringBuilder();
    sb.append("## Automated fix proposed by holms-nexus\n\n");
    sb.append("| | |\n|---|---|\n");
    sb.append("| **Incident** | [#").append(incident.getId()).append("](").append(link).append(") ")
        .append(escapePipes(incident.getTitle())).append(" |\n");
    sb.append("| **Service** | ").append(service.getName())
        .append(incident.getEnvironment() == null ? "" : " (" + incident.getEnvironment() + ")").append(" |\n");
    sb.append("| **Occurrences** | ").append(incident.getOccurrenceCount())
        .append(" since ").append(incident.getFirstSeenAt()).append(" |\n");
    sb.append("| **Confidence** | ").append(inv.getConfidence()).append(" |\n\n");
    sb.append("### Root cause\n").append(Texts.firstNonBlank(inv.getRootCause(), "_n/a_")).append("\n\n");
    sb.append("### What this PR changes\n").append(Texts.firstNonBlank(description, "_n/a_")).append("\n\n");
    sb.append("### Files changed\n");
    changed.forEach(f -> sb.append("- `").append(f).append("`\n"));
    sb.append("\n> This pull request was generated automatically from production logs. Review it"
        + " carefully, run the test suite and verify the behaviour before merging.\n");
    return sb.toString();
  }

  private static String escapePipes(String s) {
    return s == null ? "" : s.replace("|", "\\|");
  }

  private Remediation finish(Remediation r) {
    r.setFinishedAt(Instant.now());
    return repository.save(r);
  }
}

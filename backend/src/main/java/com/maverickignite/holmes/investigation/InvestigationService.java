package com.maverickignite.holmes.investigation;

import com.maverickignite.holmes.agent.AgentRunner;
import com.maverickignite.holmes.agent.AgentRunner.AgentResult;
import com.maverickignite.holmes.agent.AgentRunner.AgentTask;
import com.maverickignite.holmes.catalog.MonitoredService;
import com.maverickignite.holmes.common.NotFoundException;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.incident.Incident;
import com.maverickignite.holmes.remediation.CodeTools;
import com.maverickignite.holmes.remediation.RepoWorkspace;
import com.maverickignite.holmes.toolset.ToolRegistry;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Runs root-cause investigations and operator questions, and records their outcome. */
@Slf4j
@Service
public class InvestigationService {

  private final AgentRunner agent;
  private final ToolRegistry registry;
  private final InvestigationRepository repository;
  private final ToolCallRecorder recorder;
  private final AnalysisParser parser;
  private final HolmesProperties properties;
  private final ObjectMapper json;

  public InvestigationService(
      AgentRunner agent,
      ToolRegistry registry,
      InvestigationRepository repository,
      ToolCallRecorder recorder,
      AnalysisParser parser,
      HolmesProperties properties,
      ObjectMapper json) {
    this.agent = agent;
    this.registry = registry;
    this.repository = repository;
    this.recorder = recorder;
    this.parser = parser;
    this.properties = properties;
    this.json = json;
  }

  public Investigation get(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Investigation", id));
  }

  /** Synchronous; call from a worker thread. Never throws: failures are recorded and returned. */
  public Investigation investigate(Incident incident, MonitoredService service, RepoWorkspace ws) {
    Investigation inv = start(InvestigationKind.INCIDENT, incident.getId(), null);
    List<ToolSpec> tools = new ArrayList<>(registry.activeTools());
    if (ws != null) {
      tools.addAll(CodeTools.readOnly(ws.root()));
    }
    List<String> toolsets =
        registry.status(false).stream()
            .filter(ToolRegistry.ToolsetStatus::enabled)
            .map(ToolRegistry.ToolsetStatus::name)
            .toList();
    String prompt = Prompts.investigationPrompt(incident, service, ws != null, toolsets);
    return run(inv, Prompts.INVESTIGATION_SYSTEM, prompt, tools, true);
  }

  public Investigation startQuestion(String question) {
    return start(InvestigationKind.CHAT, null, question);
  }

  public Investigation answerQuestion(Investigation inv) {
    return run(inv, Prompts.CHAT_SYSTEM, inv.getQuestion(), registry.activeTools(), false);
  }

  private Investigation start(InvestigationKind kind, Long incidentId, String question) {
    Investigation inv = new Investigation();
    inv.setKind(kind);
    inv.setIncidentId(incidentId);
    inv.setQuestion(question);
    inv.setStatus(InvestigationStatus.RUNNING);
    inv.setProvider(agent.provider());
    inv.setModel(agent.defaultModel());
    inv.setStartedAt(Instant.now());
    return repository.save(inv);
  }

  private Investigation run(
      Investigation inv, String system, String prompt, List<ToolSpec> tools, boolean structured) {
    Long id = inv.getId();
    try {
      AgentResult result =
          agent.run(
              new AgentTask(
                  null,
                  system,
                  prompt,
                  tools,
                  properties.getInvestigation().getMaxSteps(),
                  (seq, tool, args, outcome) -> recorder.record(id, null, seq, tool, args, outcome)));
      inv.setRawAnswer(result.finalText());
      inv.setSteps(result.steps());
      inv.setToolCallCount(result.toolCalls());
      inv.setInputTokens(result.inputTokens());
      inv.setOutputTokens(result.outputTokens());
      if (structured) {
        apply(inv, parser.parse(result.finalText()));
      } else {
        inv.setReport(result.finalText());
        inv.setSummary(Texts.truncate(result.finalText(), 500));
      }
      inv.setStatus(InvestigationStatus.COMPLETED);
    } catch (RuntimeException e) {
      log.warn("Investigation #{} failed: {}", id, e.getMessage(), e);
      inv.setStatus(InvestigationStatus.FAILED);
      inv.setError(Texts.truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 2_000));
    }
    inv.setFinishedAt(Instant.now());
    return repository.save(inv);
  }

  private void apply(Investigation inv, Analysis a) {
    inv.setSummary(Texts.truncate(a.summary(), 2_000));
    inv.setRootCause(a.rootCause());
    inv.setEvidence(json.writeValueAsString(a.evidence()));
    inv.setSuggestedFix(a.suggestedFix());
    inv.setConfidence(a.confidence());
    inv.setSeverity(a.severity());
    inv.setCategory(a.category());
    inv.setCodeFixRecommended(a.codeFixRecommended());
    inv.setSuspectedFiles(json.writeValueAsString(a.suspectedFiles()));
    inv.setReport(a.report());
  }
}

package com.maverickignite.holmes.workflow;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.incident.Incident;
import com.maverickignite.holmes.investigation.Investigation;
import com.maverickignite.holmes.investigation.ToolCallRecord;
import com.maverickignite.holmes.remediation.Remediation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** API response shapes. Entities never leave the service layer directly. */
public final class Views {

  private Views() {}

  public record PageView<T>(List<T> items, int page, int size, long total, int totalPages) {
    static <E, T> PageView<T> of(Page<E> page, java.util.function.Function<E, T> map) {
      return new PageView<>(
          page.getContent().stream().map(map).toList(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }

  public record IncidentView(
      Long id,
      String serviceName,
      String environment,
      String title,
      String errorType,
      String level,
      String status,
      String severity,
      long occurrenceCount,
      Instant firstSeenAt,
      Instant lastSeenAt,
      Instant statusChangedAt,
      String sampleMessage,
      String sampleStackTrace,
      String logger,
      String host,
      String source) {

    static IncidentView of(Incident i, boolean withSamples) {
      return new IncidentView(
          i.getId(),
          i.getServiceName(),
          i.getEnvironment(),
          i.getTitle(),
          i.getErrorType(),
          i.getLevel(),
          i.getStatus().name(),
          i.getSeverity() == null ? null : i.getSeverity().name(),
          i.getOccurrenceCount(),
          i.getFirstSeenAt(),
          i.getLastSeenAt(),
          i.getStatusChangedAt(),
          withSamples ? i.getSampleMessage() : Texts.truncate(i.getSampleMessage(), 200),
          withSamples ? i.getSampleStackTrace() : null,
          i.getLogger(),
          i.getHost(),
          i.getSource());
    }
  }

  public record ToolCallView(
      int sequence,
      String toolName,
      String arguments,
      String output,
      boolean success,
      long durationMs,
      Instant createdAt) {

    static ToolCallView of(ToolCallRecord r) {
      return new ToolCallView(
          r.getSequence(),
          r.getToolName(),
          r.getArguments(),
          r.getOutput(),
          r.isSuccess(),
          r.getDurationMs(),
          r.getCreatedAt());
    }
  }

  public record InvestigationView(
      Long id,
      Long incidentId,
      String kind,
      String status,
      String provider,
      String model,
      String question,
      String summary,
      String rootCause,
      List<String> evidence,
      String suggestedFix,
      String confidence,
      String severity,
      String category,
      boolean codeFixRecommended,
      List<String> suspectedFiles,
      String report,
      int steps,
      int toolCallCount,
      long inputTokens,
      long outputTokens,
      String error,
      Instant startedAt,
      Instant finishedAt,
      List<ToolCallView> toolCalls) {

    static InvestigationView of(Investigation i, List<ToolCallRecord> calls, ObjectMapper json) {
      return new InvestigationView(
          i.getId(),
          i.getIncidentId(),
          i.getKind().name(),
          i.getStatus().name(),
          i.getProvider(),
          i.getModel(),
          i.getQuestion(),
          i.getSummary(),
          i.getRootCause(),
          strings(i.getEvidence(), json),
          i.getSuggestedFix(),
          i.getConfidence(),
          i.getSeverity(),
          i.getCategory(),
          i.isCodeFixRecommended(),
          strings(i.getSuspectedFiles(), json),
          i.getReport(),
          i.getSteps(),
          i.getToolCallCount(),
          i.getInputTokens(),
          i.getOutputTokens(),
          i.getError(),
          i.getStartedAt(),
          i.getFinishedAt(),
          calls.stream().map(ToolCallView::of).toList());
    }
  }

  public record RemediationView(
      Long id,
      Long investigationId,
      String status,
      String model,
      String branch,
      String prUrl,
      String prNumber,
      String title,
      String description,
      List<String> filesChanged,
      String diff,
      String reason,
      String error,
      Instant startedAt,
      Instant finishedAt,
      List<ToolCallView> toolCalls) {

    static RemediationView of(Remediation r, List<ToolCallRecord> calls) {
      return new RemediationView(
          r.getId(),
          r.getInvestigationId(),
          r.getStatus().name(),
          r.getModel(),
          r.getBranch(),
          r.getPrUrl(),
          r.getPrNumber(),
          r.getTitle(),
          r.getDescription(),
          Texts.splitCsv(r.getFilesChanged()),
          r.getDiff(),
          r.getReason(),
          r.getError(),
          r.getStartedAt(),
          r.getFinishedAt(),
          calls.stream().map(ToolCallView::of).toList());
    }
  }

  public record IncidentDetail(
      IncidentView incident,
      List<InvestigationView> investigations,
      List<RemediationView> remediations) {}

  static List<String> strings(String jsonArray, ObjectMapper json) {
    List<String> out = new ArrayList<>();
    if (Texts.isBlank(jsonArray)) {
      return out;
    }
    try {
      JsonNode node = json.readTree(jsonArray);
      node.forEach(n -> out.add(n.asString()));
    } catch (JacksonException e) {
      out.add(jsonArray);
    }
    return out;
  }
}

package com.maverickignite.holmes.investigation;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.toolset.ToolExecutor;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Persists each tool call immediately, so the UI can follow a running investigation. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallRecorder {

  private static final int MAX_STORED_OUTPUT = 20_000;

  private final ToolCallRepository repository;

  public void record(
      Long investigationId,
      Long remediationId,
      int sequence,
      String tool,
      String arguments,
      ToolExecutor.Outcome outcome) {
    try {
      ToolCallRecord r = new ToolCallRecord();
      r.setInvestigationId(investigationId);
      r.setRemediationId(remediationId);
      r.setSequence(sequence);
      r.setToolName(tool);
      r.setArguments(Texts.truncate(arguments, 4_000));
      r.setOutput(Texts.truncateMiddle(outcome.output(), MAX_STORED_OUTPUT));
      r.setSuccess(outcome.success());
      r.setDurationMs(outcome.durationMs());
      r.setCreatedAt(Instant.now());
      repository.save(r);
    } catch (RuntimeException e) {
      // The audit trail must never break the investigation itself.
      log.warn("Could not record tool call {}: {}", tool, e.getMessage());
    }
  }
}

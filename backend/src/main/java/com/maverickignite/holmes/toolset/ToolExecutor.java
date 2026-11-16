package com.maverickignite.holmes.toolset;

import com.maverickignite.holmes.common.Texts;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Runs a tool with a hard timeout and turns every failure into text the model can reason about,
 * so a broken integration degrades one investigation step instead of aborting the investigation.
 */
@Slf4j
@Component
public class ToolExecutor implements AutoCloseable {

  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

  public record Outcome(String output, boolean success, long durationMs) {}

  public Outcome execute(ToolSpec tool, JsonNode arguments, int maxChars) {
    long start = System.nanoTime();
    Future<String> future = pool.submit(() -> tool.handler().invoke(arguments));
    String output;
    boolean success;
    try {
      output = future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      success = true;
      if (Texts.isBlank(output)) {
        output = "(no output)";
      }
    } catch (TimeoutException e) {
      future.cancel(true);
      output = "Tool timed out after " + TIMEOUT.toSeconds() + "s";
      success = false;
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      success = false;
      if (cause instanceof ToolException) {
        output = "Error: " + cause.getMessage();
      } else {
        log.debug("Tool {} failed", tool.name(), cause);
        output = "Error: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      output = "Interrupted";
      success = false;
    }
    long ms = (System.nanoTime() - start) / 1_000_000;
    return new Outcome(Texts.truncateMiddle(output, maxChars), success, ms);
  }

  @Override
  public void close() {
    pool.shutdownNow();
  }
}

package com.maverickignite.holmes.toolset.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs a command as an argv array, never through a shell, so argument values cannot inject extra
 * commands. Output is capped and the process is killed on timeout.
 */
final class CommandRunner {

  private static final int MAX_OUTPUT_BYTES = 512 * 1024;
  private static final java.util.Set<String> INHERITED =
      java.util.Set.of("PATH", "HOME", "LANG", "LC_ALL", "TZ", "TMPDIR", "KUBECONFIG", "DOCKER_HOST",
          "DOCKER_CONFIG", "DOCKER_CONTEXT", "HTTPS_PROXY", "HTTP_PROXY", "NO_PROXY");
  /** Cloud CLI credentials are expected to come from env (AWS_*, AZURE_*) or mounted files. */
  private static final java.util.List<String> INHERITED_PREFIXES = java.util.List.of("AWS_", "AZURE_");

  record Result(int exitCode, String output, boolean timedOut) {}

  private CommandRunner() {}

  static Result run(List<String> argv, Map<String, String> env, Duration timeout)
      throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(argv).redirectErrorStream(true);
    // Never hand Holmes's own secrets (JWT key, LLM key, git token, DB password) to a child
    // process: start from an empty environment and pass through only what the CLIs need.
    Map<String, String> childEnv = pb.environment();
    childEnv.clear();
    System.getenv()
        .forEach(
            (k, v) -> {
              if (INHERITED.contains(k) || INHERITED_PREFIXES.stream().anyMatch(k::startsWith)) {
                childEnv.put(k, v);
              }
            });
    childEnv.putAll(env);
    Process process = pb.start();
    process.getOutputStream().close();

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Thread reader =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (InputStream in = process.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                      synchronized (buffer) {
                        if (buffer.size() < MAX_OUTPUT_BYTES) {
                          buffer.write(chunk, 0, Math.min(n, MAX_OUTPUT_BYTES - buffer.size()));
                        }
                      }
                    }
                  } catch (IOException ignored) {
                    // process killed; keep what was read
                  }
                });

    boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
    }
    reader.join(2_000);
    String output;
    synchronized (buffer) {
      output = buffer.toString(StandardCharsets.UTF_8);
    }
    return new Result(finished ? process.exitValue() : -1, output, !finished);
  }

  static boolean onPath(String binary) {
    String path = System.getenv("PATH");
    if (path == null) {
      return false;
    }
    for (String dir : path.split(java.io.File.pathSeparator)) {
      java.io.File f = new java.io.File(dir, binary);
      if (f.isFile() && f.canExecute()) {
        return true;
      }
    }
    return false;
  }
}

package com.maverickignite.holmes.remediation;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * File tools over a cloned repository. Every path is resolved inside the clone (no {@code ..}, no
 * symlink escapes); write tools refuse CI/CD, deployment and secret files, because a prompt
 * injected through a log line must never be able to change how code is built or deployed.
 */
public final class CodeTools {

  private static final Set<String> SKIP_DIRS =
      Set.of(".git", "node_modules", "target", "build", "dist", ".gradle", ".idea", "out", ".next");
  private static final List<Pattern> PROTECTED =
      List.of(
          Pattern.compile("(^|/)\\.git(/|$)"),
          Pattern.compile("(^|/)\\.github/"),
          Pattern.compile("(^|/)\\.gitlab-ci\\.ya?ml$"),
          Pattern.compile("(^|/)Jenkinsfile[^/]*$"),
          Pattern.compile("(^|/)docker-compose[^/]*\\.ya?ml$"),
          Pattern.compile("(^|/)Dockerfile[^/]*$"),
          Pattern.compile("(^|/)\\.env[^/]*$"),
          Pattern.compile("\\.(pem|key|p12|jks|keystore)$"),
          Pattern.compile("(^|/)(mvnw|gradlew)(\\.cmd)?$"),
          Pattern.compile("(^|/)\\.mvn/"));
  private static final long MAX_READ_BYTES = 400_000;
  private static final int MAX_LINES = 400;

  /** Build, dependency and packaging files: changing them can run code in CI. */
  private static final Pattern BUILD_FILES =
      Pattern.compile(
          "(^|/)(pom\\.xml|build\\.gradle(\\.kts)?|settings\\.gradle(\\.kts)?|gradle\\.properties"
              + "|package(-lock)?\\.json|yarn\\.lock|pnpm-[\\w.-]+|Makefile|setup\\.py|setup\\.cfg"
              + "|pyproject\\.toml|requirements[\\w.-]*\\.txt|go\\.(mod|sum)|Cargo\\.(toml|lock)"
              + "|[\\w.-]*\\.(sh|bat|cmd|ps1)|[\\w.-]*Dockerfile[\\w.-]*|compose[\\w.-]*\\.ya?ml"
              + "|[\\w.-]*pipelines?[\\w.-]*\\.ya?ml|buildspec\\.ya?ml|cloudbuild\\.ya?ml|vite\\.config\\.\\w+)$");

  private final Path root;
  /** Repo-relative directory the fix may touch (the service's code path); null = whole repo. */
  private final String scope;

  private CodeTools(Path root) {
    this(root, null);
  }

  private CodeTools(Path root, String scope) {
    this.scope = scope == null || scope.isBlank() ? null : scope.replaceAll("^/+|/+$", "") + "/";
    try {
      this.root = root.toRealPath();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static List<ToolSpec> readOnly(Path root) {
    return new CodeTools(root).readTools();
  }

  public static List<ToolSpec> readWrite(Path root, String codePath) {
    CodeTools tools = new CodeTools(root, codePath);
    List<ToolSpec> all = new ArrayList<>(tools.readTools());
    all.addAll(tools.writeTools());
    return all;
  }

  static boolean isProtected(String relativePath) {
    String p = relativePath.replace('\\', '/');
    return PROTECTED.stream().anyMatch(r -> r.matcher(p).find());
  }

  /**
   * Allow-list for automated fixes: only files inside a {@code src/} tree (application code,
   * tests, resources) of the service's code path, never dot-files, build/CI/packaging files or
   * scripts. Returns the reason a path is refused, or null when it may be changed.
   */
  static String refusal(String relativePath, String codePath) {
    String p = relativePath.replace('\\', '/');
    String scope = codePath == null || codePath.isBlank() ? null : codePath.replaceAll("^/+|/+$", "") + "/";
    if (scope != null && !p.startsWith(scope)) {
      return "outside the service's code path '" + scope + "'";
    }
    if (isProtected(p) || BUILD_FILES.matcher(p).find()) {
      return "a CI/CD, build, deployment or secret file";
    }
    for (String segment : p.split("/")) {
      if (segment.startsWith(".")) {
        return "a hidden file or directory";
      }
    }
    if (!("/" + p).contains("/src/")) {
      return "not under a src/ directory";
    }
    return null;
  }

  private List<ToolSpec> readTools() {
    return List.of(
        new ToolSpec(
            "repo_list",
            "List a directory of the checked-out repository (directories end with '/').",
            Schemas.object().string("path", "Directory relative to the repo root (default '.')", false).build(),
            args -> {
              Path dir = resolve(Texts.firstNonBlank(Args.optional(args, "path"), "."));
              if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                throw new ToolException("Not a directory");
              }
              StringBuilder sb = new StringBuilder();
              try (Stream<Path> s = Files.list(dir)) {
                s.sorted()
                    .limit(300)
                    .forEach(
                        p ->
                            sb.append(root.relativize(p))
                                .append(Files.isDirectory(p) ? "/" : "")
                                .append('\n'));
              }
              return sb.toString();
            }),
        new ToolSpec(
            "repo_find_files",
            "Find files whose path contains the given text (case-insensitive), e.g. 'OrderService'"
                + " or 'application.yml'.",
            Schemas.object().string("name", "Part of the file path", true).build(),
            args -> {
              String needle = Args.required(args, "name").toLowerCase(Locale.ROOT);
              List<String> hits = new ArrayList<>();
              walk(
                  p -> {
                    String rel = root.relativize(p).toString();
                    if (rel.toLowerCase(Locale.ROOT).contains(needle) && hits.size() < 100) {
                      hits.add(rel);
                    }
                  });
              return hits.isEmpty() ? "No matching files." : String.join("\n", hits);
            }),
        new ToolSpec(
            "repo_search",
            "Search file contents with a Java regex; returns path:line: text (max 60 hits).",
            Schemas.object()
                .string("pattern", "Regular expression, e.g. 'class OrderService' or 'findByTenant'", true)
                .string("glob", "Optional file glob, e.g. '**/*.java'", false)
                .build(),
            args -> {
              Pattern pattern;
              try {
                pattern = Pattern.compile(Args.required(args, "pattern"));
              } catch (PatternSyntaxException e) {
                throw new ToolException("Invalid regex: " + e.getDescription());
              }
              String glob = Args.optional(args, "glob");
              PathMatcher matcher =
                  glob == null ? null : FileSystems.getDefault().getPathMatcher("glob:" + glob);
              List<String> hits = new ArrayList<>();
              walk(
                  p -> {
                    Path rel = root.relativize(p);
                    if (hits.size() >= 60 || (matcher != null && !matcher.matches(rel))) {
                      return;
                    }
                    List<String> lines = readLines(p);
                    for (int i = 0; i < lines.size() && hits.size() < 60; i++) {
                      if (pattern.matcher(lines.get(i)).find()) {
                        hits.add(rel + ":" + (i + 1) + ": " + Texts.truncate(lines.get(i).strip(), 200));
                      }
                    }
                  });
              return hits.isEmpty() ? "No matches." : String.join("\n", hits);
            }),
        new ToolSpec(
            "repo_read",
            "Read a file with line numbers (max " + MAX_LINES + " lines per call).",
            Schemas.object()
                .string("path", "File path relative to the repo root", true)
                .integer("start_line", "First line, 1-based (default 1)", false)
                .integer("end_line", "Last line (default start+" + (MAX_LINES - 1) + ")", false)
                .build(),
            args -> {
              Path file = resolve(Args.required(args, "path"));
              if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new ToolException("File not found (symbolic links are not followed)");
              }
              if (Files.size(file) > MAX_READ_BYTES) {
                throw new ToolException("File too large; use repo_search to find the relevant lines");
              }
              List<String> lines = readLines(file);
              int start = Args.integer(args, "start_line", 1, 1, Math.max(1, lines.size()));
              int end =
                  Args.integer(args, "end_line", start + MAX_LINES - 1, start, start + MAX_LINES - 1);
              StringBuilder sb = new StringBuilder();
              for (int i = start; i <= Math.min(end, lines.size()); i++) {
                sb.append(i).append('\t').append(lines.get(i - 1)).append('\n');
              }
              sb.append("(").append(lines.size()).append(" lines total)");
              return sb.toString();
            }));
  }

  private List<ToolSpec> writeTools() {
    return List.of(
        new ToolSpec(
            "repo_edit",
            "Replace an exact snippet in a file. old_text must match exactly once (include enough"
                + " surrounding lines to be unique, keep indentation).",
            Schemas.object()
                .string("path", "File path relative to the repo root", true)
                .string("old_text", "Exact text to replace", true)
                .string("new_text", "Replacement text", true)
                .build(),
            args -> {
              Path file = writable(Args.required(args, "path"));
              if (!Files.isRegularFile(file)) {
                throw new ToolException("File not found; use repo_write to create files");
              }
              String content = Files.readString(file, StandardCharsets.UTF_8);
              String oldText = Args.required(args, "old_text");
              String newText = Args.optional(args, "new_text");
              int first = content.indexOf(oldText);
              if (first < 0) {
                throw new ToolException("old_text not found; re-read the file and copy it exactly");
              }
              if (content.indexOf(oldText, first + 1) >= 0) {
                throw new ToolException("old_text matches more than once; include more context");
              }
              Files.writeString(
                  file,
                  content.substring(0, first)
                      + (newText == null ? "" : newText)
                      + content.substring(first + oldText.length()),
                  StandardCharsets.UTF_8);
              return "Edited " + root.relativize(file);
            }),
        new ToolSpec(
            "repo_write",
            "Create a new file (or fully overwrite a small one), e.g. a new unit test.",
            Schemas.object()
                .string("path", "File path relative to the repo root", true)
                .string("content", "Full file content", true)
                .build(),
            args -> {
              Path file = writable(Args.required(args, "path"));
              String content = Args.required(args, "content");
              if (content.length() > 200_000) {
                throw new ToolException("Content too large");
              }
              Files.createDirectories(file.getParent());
              Files.writeString(file, content, StandardCharsets.UTF_8);
              return "Wrote " + root.relativize(file);
            }));
  }

  /**
   * Resolves a repo-relative path. The deepest existing ancestor is resolved through symlinks
   * and must stay inside the clone, so a committed symlink cannot be used to read or write
   * outside it (including for files that do not exist yet).
   */
  private Path resolve(String relative) {
    Path p = root.resolve(relative.replaceFirst("^/+", "")).normalize();
    if (!p.startsWith(root)) {
      throw new ToolException("Path escapes the repository");
    }
    Path existing = p;
    while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
      existing = existing.getParent();
    }
    try {
      if (existing == null || !existing.toRealPath().startsWith(root)) {
        throw new ToolException("Path escapes the repository");
      }
    } catch (IOException e) {
      throw new ToolException("Cannot resolve path");
    }
    return p;
  }

  private Path writable(String relative) {
    Path p = resolve(relative);
    if (Files.isSymbolicLink(p)) {
      throw new ToolException("Editing symbolic links is not allowed");
    }
    String rel = root.relativize(p).toString();
    String refused = refusal(rel, scope);
    if (refused != null) {
      throw new ToolException("Editing " + rel + " is not allowed: " + refused);
    }
    try {
      Path parentReal = p.getParent().toFile().exists() ? p.getParent().toRealPath() : null;
      if (parentReal != null && isProtected(root.relativize(parentReal.resolve(p.getFileName())).toString())) {
        throw new ToolException("Editing " + rel + " is not allowed (CI/CD, deployment or secrets)");
      }
    } catch (IOException e) {
      throw new ToolException("Cannot resolve path");
    }
    return p;
  }

  private interface FileVisitor {
    void visit(Path file) throws IOException;
  }

  private void walk(FileVisitor visitor) throws IOException {
    try (Stream<Path> s =
        Files.walk(root)
            .filter(p -> root.relativize(p).getNameCount() > 0)
            .filter(p -> !skipped(p))) {
      for (Path p : (Iterable<Path>) s::iterator) {
        if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && Files.size(p) <= MAX_READ_BYTES) {
          visitor.visit(p);
        }
      }
    }
  }

  private boolean skipped(Path p) {
    for (Path part : root.relativize(p)) {
      if (SKIP_DIRS.contains(part.toString())) {
        return true;
      }
    }
    return false;
  }

  private static List<String> readLines(Path p) throws IOException {
    try {
      return Files.readAllLines(p, StandardCharsets.UTF_8);
    } catch (MalformedInputException e) {
      return List.of();
    }
  }
}

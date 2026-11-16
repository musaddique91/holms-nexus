package com.maverickignite.holmes.ingestion;

import com.maverickignite.holmes.common.Texts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Groups occurrences of "the same" error. The fingerprint uses the root-cause exception type and
 * the top application frames (method names only, since line numbers shift with every commit). When
 * there is no stack trace it falls back to the message with variable parts (ids, numbers, quoted
 * values) masked out.
 */
@Component
public class Fingerprinter {

  private static final Pattern EXCEPTION_LINE =
      Pattern.compile(
          "^(?:Caused by:\\s*)?([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+"
              + "(?:Exception|Error|Throwable|Fault))(?::\\s*(.*))?$");
  private static final Pattern FRAME = Pattern.compile("^\\s*at\\s+([\\w$.<>/]+)\\(");
  private static final List<String> FRAMEWORK_PREFIXES =
      List.of(
          "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.", "org.springframework.",
          "org.apache.", "org.hibernate.", "com.zaxxer.", "io.netty.", "reactor.", "kotlin.",
          "org.eclipse.jetty.", "io.undertow.", "com.fasterxml.", "tools.jackson.",
          "net.bytebuddy.");
  private static final int FRAMES = 5;

  private static final Pattern UUID =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
  private static final Pattern HEX = Pattern.compile("\\b(?:0x)?[0-9a-fA-F]{12,}\\b");
  private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"");
  private static final Pattern NUMBER = Pattern.compile("\\d+");

  public record Fingerprint(String hash, String errorType, String title) {}

  public Fingerprint fingerprint(
      String service, String environment, String message, String stackTrace) {
    List<String> lines = Texts.isBlank(stackTrace) ? List.of() : stackTrace.lines().toList();

    String topType = null;
    String topMessage = null;
    String rootType = null;
    List<String> appFrames = new ArrayList<>();
    List<String> anyFrames = new ArrayList<>();
    boolean inTopSection = true;

    for (String line : lines) {
      Matcher ex = EXCEPTION_LINE.matcher(line.strip());
      if (ex.matches()) {
        if (topType == null) {
          topType = ex.group(1);
          topMessage = ex.group(2);
        } else {
          inTopSection = false;
        }
        rootType = ex.group(1);
        continue;
      }
      Matcher frame = FRAME.matcher(line);
      if (inTopSection && frame.find()) {
        String method = frame.group(1);
        if (anyFrames.size() < FRAMES) {
          anyFrames.add(method);
        }
        if (appFrames.size() < FRAMES
            && FRAMEWORK_PREFIXES.stream().noneMatch(method::startsWith)) {
          appFrames.add(method);
        }
      }
    }

    List<String> frames = appFrames.isEmpty() ? anyFrames : appFrames;
    String basis;
    if (rootType != null && !frames.isEmpty()) {
      basis = rootType + "|" + String.join(">", frames);
    } else {
      String errorLine = rootType != null ? rootType + ": " + topMessage : message;
      basis = (rootType == null ? "" : rootType) + "|" + normalize(errorLine);
    }

    String hash = sha256(nullToEmpty(service) + "|" + nullToEmpty(environment) + "|" + basis);
    return new Fingerprint(hash, rootType, title(rootType, topType, topMessage, message));
  }

  static String normalize(String text) {
    if (text == null) {
      return "";
    }
    String s = text.lines().findFirst().orElse("");
    s = UUID.matcher(s).replaceAll("<uuid>");
    s = EMAIL.matcher(s).replaceAll("<email>");
    s = HEX.matcher(s).replaceAll("<hex>");
    s = QUOTED.matcher(s).replaceAll("<str>");
    s = NUMBER.matcher(s).replaceAll("<n>");
    return Texts.truncate(s.strip(), 300);
  }

  private static String title(String rootType, String topType, String topMessage, String message) {
    String type = rootType != null ? rootType : topType;
    String detail = Texts.firstNonBlank(firstLine(message), topMessage);
    String simple = type == null ? null : type.substring(type.lastIndexOf('.') + 1);
    String title;
    if (simple != null && detail != null) {
      title = simple + ": " + detail;
    } else {
      title = Texts.firstNonBlank(detail, simple, "Unknown error");
    }
    return Texts.truncate(title, 300);
  }

  private static String firstLine(String s) {
    return s == null ? null : s.lines().findFirst().orElse(null);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String sha256(String s) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}

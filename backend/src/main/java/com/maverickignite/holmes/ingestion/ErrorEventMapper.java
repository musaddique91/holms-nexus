package com.maverickignite.holmes.ingestion;

import com.maverickignite.holmes.common.Texts;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Maps log records of any common shape to {@link ErrorEvent}: Spring Boot structured logs
 * (logstash/ECS), Vector/OpenObserve rows, OpenObserve alert payloads, or plain text lines whose
 * stack trace is embedded in the message.
 */
@Component
public class ErrorEventMapper {

  private static final String[] SERVICE_KEYS = {
    "service", "service_name", "app", "application", "container_name",
    "kubernetes_container_name", "k8s_container_name"
  };
  private static final String[] MESSAGE_KEYS = {"message", "msg", "log", "body", "text"};
  private static final String[] STACK_KEYS = {
    "stack_trace", "stackTrace", "error_stack_trace", "exception", "error_stacktrace", "stack"
  };
  private static final String[] LEVEL_KEYS = {"level", "log_level", "severity", "levelname"};
  private static final String[] LOGGER_KEYS = {"logger_name", "logger", "log_logger"};
  private static final String[] HOST_KEYS = {"host", "hostname", "host_name", "node"};
  private static final String[] ENV_KEYS = {"environment", "env", "deployment_environment"};
  private static final String[] TIME_KEYS = {
    "@timestamp", "timestamp", "_timestamp", "time", "startsAt", "starts_at"
  };
  private static final String[] BATCH_KEYS = {"rows", "hits", "data", "events", "records"};
  private static final String[] ALERT_SERVICE_KEYS = {
    "service", "service_name", "app", "application", "container", "job", "alertname"
  };
  private static final String[] ALERT_ENV_KEYS = {"environment", "env", "namespace"};
  private static final String[] ALERT_HOST_KEYS = {"instance", "node", "pod", "host"};
  private static final String[] ALERT_SUMMARY_KEYS = {"summary", "message", "title"};
  private static final String[] ALERT_DESC_KEYS = {"description", "detail", "info"};

  private static final Pattern LEVEL_IN_TEXT =
      Pattern.compile("\\b(FATAL|CRITICAL|SEVERE|ERROR|WARN(?:ING)?|INFO|DEBUG|TRACE)\\b");
  /** Spring Boot's default console prefix: "2026-.. ERROR 1 --- [app] [thread] logger : message". */
  private static final Pattern SPRING_CONSOLE_PREFIX =
      Pattern.compile(
          "^\\S+(?:\\s\\S+)?\\s+(?:FATAL|ERROR|WARN|INFO|DEBUG|TRACE)\\s+\\d+\\s+---\\s+.*?\\s:\\s(.*)$");
  private static final Pattern STACK_START =
      Pattern.compile("\\n(?=[\\w$.]+(?:Exception|Error|Throwable)\\b|\\s+at\\s|Caused by:)");

  /** Expands batch envelopes ({@code {"rows":[...]}}, arrays) into individual events. */
  public List<ErrorEvent> events(JsonNode node, String source) {
    List<ErrorEvent> out = new ArrayList<>();
    collect(node, source, out, 0);
    return out;
  }

  /**
   * Maps Prometheus Alertmanager webhook payload into individual {@link ErrorEvent} instances.
   * Only active/firing alerts are mapped; resolved alerts are ignored.
   */
  public List<ErrorEvent> fromAlertmanager(JsonNode body) {
    List<ErrorEvent> out = new ArrayList<>();
    if (body == null || body.isNull()) {
      return out;
    }
    JsonNode alerts = body.path("alerts");
    if (alerts.isArray()) {
      for (JsonNode alert : alerts) {
        ErrorEvent event = mapAlert(alert, body);
        if (event != null) {
          out.add(event);
        }
      }
    } else if (body.has("labels")) {
      ErrorEvent event = mapAlert(body, body);
      if (event != null) {
        out.add(event);
      }
    }
    return out;
  }

  /**
   * Maps OpenTelemetry (OTLP/JSON) trace payloads containing error spans or exceptions into
   * {@link ErrorEvent} instances.
   */
  public List<ErrorEvent> fromOtlpTraces(JsonNode body) {
    List<ErrorEvent> out = new ArrayList<>();
    if (body == null || body.isNull()) {
      return out;
    }
    JsonNode resourceSpans = body.path("resourceSpans");
    if (!resourceSpans.isArray()) {
      return out;
    }
    for (JsonNode rs : resourceSpans) {
      JsonNode resourceAttrs = rs.path("resource").path("attributes");
      String service = extractAttr(resourceAttrs, "service.name", "service", "app");
      String env = extractAttr(resourceAttrs, "deployment.environment", "environment", "env");
      String host = extractAttr(resourceAttrs, "host.name", "node", "instance");

      JsonNode scopeSpans = rs.path("scopeSpans");
      if (scopeSpans.isArray()) {
        for (JsonNode ss : scopeSpans) {
          JsonNode spans = ss.path("spans");
          if (spans.isArray()) {
            for (JsonNode span : spans) {
              ErrorEvent ev = mapOtlpSpan(span, service, env, host);
              if (ev != null) {
                out.add(ev);
              }
            }
          }
        }
      }
    }
    return out;
  }

  private ErrorEvent mapOtlpSpan(JsonNode span, String defaultService, String defaultEnv, String defaultHost) {
    JsonNode status = span.path("status");
    int statusCode = status.path("code").asInt(0);
    String statusMsg = status.path("message").asString(null);
    boolean hasException = false;
    String exceptionMsg = null;
    String exceptionType = null;
    String stackTrace = null;

    JsonNode events = span.path("events");
    if (events.isArray()) {
      for (JsonNode ev : events) {
        if ("exception".equalsIgnoreCase(ev.path("name").asString(""))) {
          hasException = true;
          JsonNode evAttrs = ev.path("attributes");
          exceptionMsg = extractAttr(evAttrs, "exception.message", "message");
          exceptionType = extractAttr(evAttrs, "exception.type", "type");
          stackTrace = extractAttr(evAttrs, "exception.stacktrace", "stacktrace");
        }
      }
    }

    // OTLP status code 2 = STATUS_CODE_ERROR
    if (statusCode != 2 && !hasException && !span.path("attributes").toString().contains("\"error\":true")) {
      return null;
    }

    JsonNode spanAttrs = span.path("attributes");
    String service = Texts.firstNonBlank(extractAttr(spanAttrs, "service.name"), defaultService, "unknown-service");
    String env = Texts.firstNonBlank(extractAttr(spanAttrs, "environment", "deployment.environment"), defaultEnv);
    String spanName = span.path("name").asString("span");
    String traceId = span.path("traceId").asString(null);
    String spanId = span.path("spanId").asString(null);

    String message = Texts.firstNonBlank(
        exceptionMsg != null && exceptionType != null ? exceptionType + ": " + exceptionMsg : null,
        exceptionMsg,
        statusMsg,
        "[" + spanName + "] Span failed with error");

    StringBuilder ctx = new StringBuilder();
    ctx.append("Trace ID: ").append(traceId).append('\n');
    ctx.append("Span ID: ").append(spanId).append('\n');
    ctx.append("Span Name: ").append(spanName).append('\n');
    if (!Texts.isBlank(statusMsg)) {
      ctx.append("Status Message: ").append(statusMsg).append('\n');
    }
    if (!Texts.isBlank(stackTrace)) {
      ctx.append("\nStacktrace:\n").append(stackTrace).append('\n');
    }

    Instant ts = parseOtlpTimestamp(span.path("startTimeUnixNano").asString(null));

    return new ErrorEvent(
        service,
        env,
        "ERROR",
        message,
        ctx.toString().strip(),
        spanName,
        defaultHost,
        ts,
        "otlp");
  }

  /**
   * Maps OpenTelemetry (OTLP/JSON) log payloads containing error records into {@link ErrorEvent} instances.
   */
  public List<ErrorEvent> fromOtlpLogs(JsonNode body) {
    List<ErrorEvent> out = new ArrayList<>();
    if (body == null || body.isNull()) {
      return out;
    }
    JsonNode resourceLogs = body.path("resourceLogs");
    if (!resourceLogs.isArray()) {
      return out;
    }
    for (JsonNode rl : resourceLogs) {
      JsonNode resourceAttrs = rl.path("resource").path("attributes");
      String service = extractAttr(resourceAttrs, "service.name", "service", "app");
      String env = extractAttr(resourceAttrs, "deployment.environment", "environment", "env");
      String host = extractAttr(resourceAttrs, "host.name", "node", "instance");

      JsonNode scopeLogs = rl.path("scopeLogs");
      if (scopeLogs.isArray()) {
        for (JsonNode sl : scopeLogs) {
          JsonNode logs = sl.path("logRecords");
          if (logs.isArray()) {
            for (JsonNode logRec : logs) {
              ErrorEvent ev = mapOtlpLog(logRec, service, env, host);
              if (ev != null) {
                out.add(ev);
              }
            }
          }
        }
      }
    }
    return out;
  }

  private ErrorEvent mapOtlpLog(JsonNode logRec, String defaultService, String defaultEnv, String defaultHost) {
    int severityNum = logRec.path("severityNumber").asInt(0);
    String severityText = logRec.path("severityText").asString("").toUpperCase(Locale.ROOT);
    boolean isError = severityNum >= 17 || severityText.contains("ERROR") || severityText.contains("FATAL") || severityText.contains("CRIT");
    if (!isError) {
      return null;
    }

    JsonNode body = logRec.path("body");
    String message = body.isObject() && body.has("stringValue")
        ? body.path("stringValue").asString("")
        : body.asString("");

    if (Texts.isBlank(message)) {
      message = "OpenTelemetry log event";
    }

    JsonNode attrs = logRec.path("attributes");
    String service = Texts.firstNonBlank(extractAttr(attrs, "service.name", "app"), defaultService, "unknown-service");
    String env = Texts.firstNonBlank(extractAttr(attrs, "environment", "deployment.environment"), defaultEnv);
    String traceId = logRec.path("traceId").asString(null);
    String spanId = logRec.path("spanId").asString(null);
    String stack = extractAttr(attrs, "exception.stacktrace", "stack_trace", "stack");

    StringBuilder ctx = new StringBuilder();
    if (!Texts.isBlank(traceId)) ctx.append("Trace ID: ").append(traceId).append('\n');
    if (!Texts.isBlank(spanId)) ctx.append("Span ID: ").append(spanId).append('\n');
    if (!Texts.isBlank(stack)) ctx.append("\nStacktrace:\n").append(stack).append('\n');

    Instant ts = parseOtlpTimestamp(logRec.path("timeUnixNano").asString(null));

    return new ErrorEvent(
        service,
        env,
        Texts.firstNonBlank(severityText, "ERROR"),
        message,
        ctx.toString().strip(),
        service,
        defaultHost,
        ts,
        "otlp");
  }

  private String extractAttr(JsonNode attrs, String... keys) {
    if (attrs == null || attrs.isNull()) {
      return null;
    }
    if (attrs.isArray()) {
      for (String k : keys) {
        for (JsonNode item : attrs) {
          if (k.equalsIgnoreCase(item.path("key").asString(""))) {
            JsonNode v = item.path("value");
            if (v.has("stringValue")) return v.path("stringValue").asString();
            if (v.has("intValue")) return v.path("intValue").asString();
            if (v.has("boolValue")) return v.path("boolValue").asString();
            return v.asString();
          }
        }
      }
    } else if (attrs.isObject()) {
      for (String k : keys) {
        if (attrs.has(k)) {
          return attrs.path(k).asString();
        }
      }
    }
    return null;
  }

  private Instant parseOtlpTimestamp(String nanoStr) {
    if (!Texts.isBlank(nanoStr)) {
      try {
        long nanos = Long.parseLong(nanoStr);
        return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
      } catch (Exception ignored) {
      }
    }
    return Instant.now();
  }

  private ErrorEvent mapAlert(JsonNode alert, JsonNode parent) {
    String status = alert.path("status").asString(parent.path("status").asString("firing"));
    if ("resolved".equalsIgnoreCase(status)) {
      return null;
    }

    JsonNode labels = alert.path("labels");
    JsonNode annotations = alert.path("annotations");

    String alertName = labels.path("alertname").asString("Alert");
    String service = text(labels, ALERT_SERVICE_KEYS);
    String environment = text(labels, ALERT_ENV_KEYS);
    String host = text(labels, ALERT_HOST_KEYS);
    String severity = text(labels, LEVEL_KEYS);
    String level = severity != null ? severity.toUpperCase(Locale.ROOT) : "ERROR";

    String summary = text(annotations, ALERT_SUMMARY_KEYS);
    String description = text(annotations, ALERT_DESC_KEYS);

    String message = "[" + alertName + "] " + Texts.firstNonBlank(summary, description, alertName);

    StringBuilder context = new StringBuilder();
    context.append("Alert: ").append(alertName).append('\n');
    if (!Texts.isBlank(summary)) {
      context.append("Summary: ").append(summary).append('\n');
    }
    if (!Texts.isBlank(description)) {
      context.append("Description: ").append(description).append('\n');
    }
    if (labels.isObject()) {
      context.append("Labels:\n");
      labels.properties().forEach(entry -> {
        context.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().asString()).append('\n');
      });
    }
    String runbook = text(annotations, new String[] {"runbook_url", "runbook"});
    if (!Texts.isBlank(runbook)) {
      context.append("Runbook: ").append(runbook).append('\n');
    }
    String generatorUrl = alert.path("generatorURL").asString(null);
    if (!Texts.isBlank(generatorUrl)) {
      context.append("Generator URL: ").append(generatorUrl).append('\n');
    }

    Instant timestamp = timestamp(alert);
    if (timestamp == null) {
      timestamp = timestamp(parent);
    }
    if (timestamp == null) {
      timestamp = Instant.now();
    }

    return new ErrorEvent(
        service,
        environment,
        level,
        message,
        context.toString().strip(),
        alertName,
        host,
        timestamp,
        "alertmanager");
  }

  private void collect(JsonNode node, String source, List<ErrorEvent> out, int depth) {
    if (node == null || node.isNull() || depth > 3) {
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> collect(child, source, out, depth + 1));
      return;
    }
    if (!node.isObject()) {
      return;
    }
    for (String key : BATCH_KEYS) {
      if (node.path(key).isArray()) {
        collect(node.path(key), source, out, depth + 1);
        return;
      }
    }
    out.add(map(node, source));
  }

  ErrorEvent map(JsonNode n, String source) {
    String message = text(n, MESSAGE_KEYS);
    String stack = text(n, STACK_KEYS);
    if (stack == null && message != null) {
      Matcher m = STACK_START.matcher(message);
      if (m.find()) {
        stack = message.substring(m.start() + 1);
        message = message.substring(0, m.start());
      }
    }
    String level = text(n, LEVEL_KEYS);
    if (level == null && message != null) {
      Matcher m = LEVEL_IN_TEXT.matcher(firstLine(message));
      if (m.find()) {
        level = m.group(1);
      }
    }
    if (level == null && stack != null) {
      level = "ERROR";
    }
    if (message != null) {
      Matcher prefix = SPRING_CONSOLE_PREFIX.matcher(firstLine(message));
      if (prefix.matches()) {
        message = prefix.group(1) + message.substring(firstLine(message).length());
      }
    }
    return new ErrorEvent(
        text(n, SERVICE_KEYS),
        text(n, ENV_KEYS),
        level == null ? null : level.toUpperCase(Locale.ROOT),
        message == null ? null : message.strip(),
        stack == null ? null : stack.strip(),
        text(n, LOGGER_KEYS),
        text(n, HOST_KEYS),
        timestamp(n),
        source);
  }

  private static String text(JsonNode n, String[] keys) {
    for (String key : keys) {
      JsonNode v = n.get(key);
      if (v == null || v.isNull()) {
        continue;
      }
      String s = v.isValueNode() ? v.asString() : v.toString();
      if (!Texts.isBlank(s)) {
        return s;
      }
    }
    return null;
  }

  private static Instant timestamp(JsonNode n) {
    for (String key : TIME_KEYS) {
      JsonNode v = n.get(key);
      if (v == null || v.isNull()) {
        continue;
      }
      if (v.isNumber()) {
        return fromEpoch(v.asLong());
      }
      String s = v.asString();
      try {
        return Instant.parse(s);
      } catch (DateTimeParseException e) {
        if (!s.isEmpty() && s.chars().allMatch(Character::isDigit)) {
          return fromEpoch(Long.parseLong(s));
        }
      }
    }
    return Instant.now();
  }

  /** Accepts seconds, millis or micros (OpenObserve's {@code _timestamp}). */
  private static Instant fromEpoch(long value) {
    if (value > 100_000_000_000_000L) {
      return Instant.ofEpochSecond(value / 1_000_000, (value % 1_000_000) * 1_000);
    }
    if (value > 100_000_000_000L) {
      return Instant.ofEpochMilli(value);
    }
    return Instant.ofEpochSecond(value);
  }

  private static String firstLine(String s) {
    int nl = s.indexOf('\n');
    return nl < 0 ? s : s.substring(0, nl);
  }
}

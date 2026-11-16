package com.maverickignite.holmes.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable of mvx-holmes lives under {@code holmes.*}. Defaults are chosen so a fresh
 * deployment is safe: auto-fix opens <em>draft</em> PRs only, and every external integration
 * stays off until it is given credentials.
 */
@Data
@ConfigurationProperties(prefix = "holmes")
public class HolmesProperties {

  /** Public URL of the dashboard, used for links in notifications. */
  private String dashboardUrl = "http://localhost:5173";

  /** Where repositories are cloned for code-aware investigation and fixes. */
  private String workspaceDir = System.getProperty("java.io.tmpdir") + "/holmes-workspaces";

  private Security security = new Security();
  private Llm llm = new Llm();
  private Ingestion ingestion = new Ingestion();
  private Investigation investigation = new Investigation();
  private Remediation remediation = new Remediation();
  private Git git = new Git();
  private Notifications notifications = new Notifications();
  private OpenObserve openobserve = new OpenObserve();
  private Toolsets toolsets = new Toolsets();

  /** Services created on first boot if they do not exist yet (editable afterwards in the UI). */
  private List<SeedService> seedServices = new ArrayList<>();

  @Data
  public static class Security {
    private String adminUsername = "admin";
    /** {@code {bcrypt}...} or {@code {noop}...}; a bare value is treated as plain text. */
    private String adminPassword;
    /** HMAC secret for login tokens, at least 32 bytes. */
    private String jwtSecret;
    /** Encrypts secrets stored in the database; derived from jwtSecret when unset. */
    private String settingsKey;
    private Duration tokenTtl = Duration.ofHours(12);
    private List<String> corsOrigins = new ArrayList<>(List.of("http://localhost:5173"));
  }

  @Data
  public static class Llm {
    /** anthropic | openai | azure | ollama | nvidia | openrouter | gemini | openai-compatible. */
    private String provider = "anthropic";
    private String model = "claude-sonnet-5-5";
    /** Optional stronger/cheaper model for the code-fix phase; falls back to {@link #model}. */
    private String fixModel;
    private String apiKey;
    /** Override for OpenAI-compatible gateways (LiteLLM, vLLM, NVIDIA NIM, Bedrock gateway...). */
    private String baseUrl;
    /** Azure OpenAI only. */
    private String azureDeployment;
    private String azureApiVersion = "2024-10-21";
    private int maxTokens = 4096;
    private double temperature = 0.1;
    /** Newer OpenAI models reject {@code max_tokens} and require {@code max_completion_tokens}. */
    private String maxTokensParam = "max_tokens";
    private Duration timeout = Duration.ofSeconds(180);
  }

  @Data
  public static class Ingestion {
    private Kafka kafka = new Kafka();
    private Poller poller = new Poller();
    /** Shared secret for the OpenObserve alert webhook; blank disables the webhook. */
    private String webhookToken;
    /** Only these levels open incidents (case-insensitive). */
    private List<String> levels = new ArrayList<>(List.of("ERROR", "FATAL", "CRITICAL", "SEVERE"));
    /** Messages matching any of these regexes are dropped as noise. */
    private List<String> ignorePatterns = new ArrayList<>();
  }

  @Data
  public static class Kafka {
    private boolean enabled = true;
    private String topic = "mvx.errors";
    private String groupId = "mvx-holmes";
    private int partitions = 3;
    private int replicas = 1;
  }

  @Data
  public static class Poller {
    /** Pull ERROR rows straight from OpenObserve on a schedule and publish them to Kafka. */
    private boolean enabled = false;
    private Duration interval = Duration.ofSeconds(30);
    private String stream = "mvx_apps";
    private String filter = "level IN ('ERROR','FATAL')";
    private int batchSize = 500;
    private Duration initialLookback = Duration.ofMinutes(5);
  }

  @Data
  public static class Investigation {
    private boolean enabled = true;
    private int maxSteps = 15;
    private int maxToolOutputChars = 12_000;
    private int concurrency = 2;
    /** Hard cap on LLM investigations started per rolling hour, to bound spend. */
    private int maxPerHour = 30;
    private Duration pollInterval = Duration.ofSeconds(10);
    /**
     * In-flight rows get a heartbeat every poll; rows without one for this long were orphaned by
     * a crashed instance and are re-queued.
     */
    private Duration staleAfter = Duration.ofMinutes(10);
  }

  @Data
  public static class Remediation {
    private boolean enabled = true;
    /** Open PRs as drafts so nothing merges without a human. */
    private boolean draftPullRequests = true;
    private int maxSteps = 25;
    private String branchPrefix = "holmes/fix-";
    /** Minimum investigation confidence for an automatic fix attempt: LOW | MEDIUM | HIGH. */
    private String minConfidence = "MEDIUM";
  }

  @Data
  public static class Git {
    private String authorName = "mvx-nexus";
    private String authorEmail = "mvx-nexus@example.com";
    private GitHub github = new GitHub();
    private GitLab gitlab = new GitLab();
  }

  @Data
  public static class GitHub {
    private String token;
    private String apiUrl = "https://api.github.com";
  }

  @Data
  public static class GitLab {
    private String token;
    private String url = "https://gitlab.com";
  }

  @Data
  public static class Notifications {
    private Slack slack = new Slack();
    private Email email = new Email();
    private Telegram telegram = new Telegram();
  }

  @Data
  public static class Slack {
    private boolean enabled = false;
    /** Incoming-webhook URL (simplest), or use botToken + defaultChannel for per-service channels. */
    private String webhookUrl;
    private String botToken;
    private String defaultChannel;
  }

  @Data
  public static class Email {
    private boolean enabled = false;
    private String from = "MVX Nexus <alerts@example.com>";
    private List<String> to = new ArrayList<>();
  }

  @Data
  public static class Telegram {
    private boolean enabled = false;
    private String botToken;
    private String chatId;
    private String apiUrl = "https://api.telegram.org";
  }

  @Data
  public static class OpenObserve {
    private String url = "http://openobserve:5080";
    private String organization = "default";
    private String username;
    private String password;
  }

  @Data
  public static class Toolsets {
    /** CLI toolsets declared in classpath:toolsets/*.yaml, keyed by toolset name. */
    private Map<String, CliToolset> cli = new LinkedHashMap<>();
    /** Optional directory of extra CLI toolset YAML files (same format as the built-in ones). */
    private String customDir;
    private boolean openobserve = true;
    private Grafana grafana = new Grafana();
    private Prometheus prometheus = new Prometheus();
    private Loki loki = new Loki();
    private OpenTelemetry opentelemetry = new OpenTelemetry();
    private List<KafkaCluster> kafka = new ArrayList<>();
    private List<Database> databases = new ArrayList<>();
    private List<Mongo> mongo = new ArrayList<>();
    private List<RabbitMq> rabbitmq = new ArrayList<>();
  }

  @Data
  public static class CliToolset {
    private boolean enabled = false;
    private Map<String, String> env = new LinkedHashMap<>();
    private Duration timeout = Duration.ofSeconds(30);
  }

  @Data
  public static class Grafana {
    private boolean enabled = false;
    private String url = "http://grafana:3000";
    private String apiKey;
  }

  @Data
  public static class Prometheus {
    private boolean enabled = false;
    private String url = "http://prometheus:9090";
    private String alertmanagerUrl;
    private String bearerToken;
  }

  @Data
  public static class Loki {
    private boolean enabled = false;
    private String url = "http://loki:3100";
    private String tenantId;
    private String bearerToken;
  }

  @Data
  public static class OpenTelemetry {
    private boolean enabled = false;
    private String url = "http://openobserve:5080";
    private String tracesUrl;
    private String bearerToken;
  }

  @Data
  public static class KafkaCluster {
    private String name;
    private String bootstrapServers;
    /** Extra AdminClient properties (security.protocol, sasl.jaas.config, ...). */
    private Map<String, String> properties = new LinkedHashMap<>();
  }

  @Data
  public static class Database {
    private String name;
    /** postgres | mysql | sqlserver. */
    private String type;
    private String url;
    private String username;
    private String password;
  }

  @Data
  public static class Mongo {
    private String name;
    private String uri;
    private String database;
  }

  @Data
  public static class RabbitMq {
    private String name;
    /** Management API base URL, e.g. http://rabbitmq:15672. */
    private String url;
    private String username;
    private String password;
  }

  @Data
  public static class SeedService {
    private String name;
    private String displayName;
    private List<String> aliases = new ArrayList<>();
    private String environment;
    private String repoUrl;
    private String gitProvider = "GITHUB";
    private String defaultBranch = "main";
    private String codePath;
    private String runbook;
    private boolean autoFix = false;
  }
}

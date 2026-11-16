# Intelligence architecture

A HolmesGPT-style AI SRE agent in Spring Boot: it watches application errors, investigates
the root cause with read-only tools, proposes or opens a fix PR, and notifies the team.

```
 app containers ──logs──► Vector ──all lines──► OpenObserve (search, retention)
                             │                        ▲
                             └─ERROR/FATAL──► Kafka holms.errors          │ log search tool
                                                 │                      │
 remote VMs (Vector) ──HTTPS──► /api/ingest ─────┤                      │
 OpenObserve alert / poller ────────────────────►┤                      │
                                                 ▼                      │
                               ┌─────────────── Intelligence ────────────────────────────┐
                               │ ingestion: map → filter → fingerprint → upsert        │
                               │ incident (Postgres, dedup by fingerprint)             │
                               │ scheduler: claim NEW (SKIP LOCKED), budget per hour   │
                               │ workflow: clone repo → investigate → fix → notify     │
                               │   agent loop (LLM ⇄ tools)                            │
                               │   toolsets: k8s · docker · compose · aws · azure ·    │
                               │     openobserve · grafana · github · gitlab · kafka · │
                               │     postgres/mysql/sqlserver · mongo · rabbitmq ·     │
                               │     repo_* (code)                                     │
                               │   remediation: edit clone → safety checks → branch →  │
                               │     push → draft PR/MR                                │
                               │   notifications: slack · email · telegram             │
                               └───────────────────────────────────────────────────────┘
                                                 ▲
                                    React dashboard (nginx) /api
```

## Modules (`com.maverickignite.holmes`)

| Package | Responsibility |
|---|---|
| `ingestion` | Kafka listener, webhook (`/api/ingest/*`), OpenObserve poller, lenient event mapper, fingerprinting, noise filter |
| `incident` | Incident entity; atomic upsert / claim / requeue in SQL (`IncidentStore`) |
| `catalog` | Monitored services: aliases → canonical name, repo, runbook, auto-fix flag, channels |
| `workflow` | `IncidentWorkflow` (investigate → fix → notify), `InvestigationScheduler`, REST views |
| `agent` | Provider-neutral tool-calling loop, JSON answer extraction |
| `llm` | `AnthropicClient` (prompt caching) and `OpenAiCompatibleClient` (OpenAI, Azure, Gemini, Ollama, OpenRouter, LiteLLM/Bedrock/Vertex gateways) |
| `toolset` | `Toolset`/`ToolSpec` contracts, registry, executor (timeouts, truncation); `cli` = YAML-defined command tools; `builtin` = Java toolsets |
| `remediation` | JGit workspaces, sandboxed code tools, PR safety rules, PR/MR creation |
| `scm` | GitHub and GitLab REST clients |
| `notification` | Slack (webhook or bot), e-mail (SMTP), Telegram |
| `investigation` | Investigation + tool-call audit trail, prompts, analysis parsing, "Ask Holmes" |
| `auth`, `config` | Single-operator login issuing HS256 JWTs; Kafka DLT; settings |

## Design decisions

- **Kafka consumer only records.** Investigations are driven from the database by a
  scheduler, so a slow LLM never causes consumer lag or rebalances, work survives restarts,
  and several replicas can share the load (`FOR UPDATE SKIP LOCKED`).
- **One incident per fingerprint.** Root-cause exception type + top application frames
  (without line numbers); message-only errors are normalised (ids, numbers, quoted values
  masked). Repeats only bump counters; a resolved incident re-opens if the error returns.
- **Cost guards.** Hourly investigation budget, concurrency cap, step budget per run,
  tool-output truncation, prompt caching on Anthropic.
- **Read-only by construction.** CLI tools run as argv (no shell) with validated arguments
  (no whitespace/metacharacters/leading `-`); SQL tools accept a single SELECT on a read-only
  connection that is always rolled back; prefer read-only DB users anyway.
- **Prompt-injection aware.** Logs are data, not instructions (stated in every prompt). The
  fix agent cannot edit CI/CD, Docker, compose, env or key files; changes over 10 files are
  rejected; PRs are drafts by default and never merged by Holmes.
- **Extensible.** A new CLI integration is a YAML file in `toolsets/` (or
  `HOLMES_CUSTOM_TOOLSETS_DIR`); a new API integration is one `@Component implements Toolset`.

## Incident lifecycle

`NEW → INVESTIGATING → INVESTIGATED | FIXING → PR_OPENED`, plus `FAILED`, and operator
states `RESOLVED` / `IGNORED`. All status changes are compare-and-set in SQL, so operators and
workers never overwrite each other. In-flight rows get a heartbeat every poll; rows without one
for `holmes.investigation.stale-after` (10 min) were orphaned by a crash and are re-queued. A
`FAILED` incident (e.g. LLM outage) is retried when the error recurs after a 30-minute cooldown;
a `RESOLVED` one re-opens as soon as it recurs.

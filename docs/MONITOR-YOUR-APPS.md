# Monitor your apps

MVX Nexus turns error log lines into incidents. There are four ways to get errors in. Use
whichever fits; they can be combined.

| Your apps run… | Use | Setup |
|---|---|---|
| in Docker on the same machine as MVX Nexus | the bundled Vector agent | nothing: it is automatic |
| in Docker on other machines | the remote log agent | `deploy/log-agent/` |
| anywhere, with Prometheus Alertmanager | the Alertmanager webhook | one receiver in Alertmanager |
| anywhere, with OpenTelemetry | the OTLP endpoints | point your collector at MVX Nexus |

Every webhook needs the shared secret from `.env` (`HOLMES_WEBHOOK_TOKEN`) in the
`X-Holmes-Token` header. Alertmanager and OTLP may send it as `Authorization: Bearer <token>` instead.

## 1. Same machine: automatic

The `vector` container reads the logs of every container on the host, except MVX Nexus's own.
All lines go to OpenObserve (searchable, and used by the AI as context). Lines at `ERROR` or
`FATAL` level also go to Kafka and become incidents.

To watch only some containers, edit `deploy/vector/vector.yaml`: replace `exclude_containers`
with `include_containers: ["orders-", "payments-"]` (name prefixes), then
`docker compose restart vector`.

## 2. Other machines: remote log agent

On each remote machine:

```bash
cd deploy/log-agent
cp .env.example .env        # OpenObserve user/password and HOLMES_WEBHOOK_TOKEN
# edit the two URLs in vector-remote.yaml: your OpenObserve and MVX Nexus HTTPS addresses
docker compose up -d
```

Kafka is never exposed: the agent posts errors to `https://<your-nexus>/api/ingest/events`.

## 3. Prometheus Alertmanager

```yaml
# alertmanager.yml
receivers:
  - name: mvx-nexus
    webhook_configs:
      - url: https://<your-nexus>/api/ingest/alertmanager
        http_config:
          http_headers:
            X-Holmes-Token: { values: ["<HOLMES_WEBHOOK_TOKEN>"] }
```

Each firing alert opens (or updates) an incident; resolved alerts are ignored. The service comes
from the first of the labels `service`, `service_name`, `app`, `application`, `container`, `job`,
`alertname`; `environment`/`env`/`namespace` and `severity` are used when present.

## 4. OpenTelemetry (OTLP over HTTP)

Point an OpenTelemetry Collector or SDK at:

- traces: `https://<your-nexus>/api/ingest/otlp/v1/traces`
- logs: `https://<your-nexus>/api/ingest/otlp/v1/logs`

with the header `X-Holmes-Token: <HOLMES_WEBHOOK_TOKEN>` (or `Authorization: Bearer <token>`).
Use OTLP's **JSON** encoding (`encoding: json` on the Collector's `otlphttp` exporter); protobuf
is not accepted. Spans with an error status and log records at ERROR or above become incidents.

## Make your logs easy to read

Plain-text logs work: the level is detected from the line. JSON logs work better, because the
level, logger and stack trace arrive as separate fields.

- **Spring Boot 3.4+:** set `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` on the container.
- **Node (pino):** use text levels: `formatters: { level: (label) => ({ level: label }) }`.
- **Anything else:** one JSON object per line with `level` and `message` fields.

## Link an app to its code

So the AI can read the code and open fix pull requests, add the app under **Intelligence →
Services**: its name, the container names seen in logs (aliases), the git repository and,
optionally, its own access token. Without a repository, incidents are still investigated from
logs and infrastructure.

Give each container a stable name: incidents are grouped by service name, and a random name
(`brave_tesla`) changes on every restart.

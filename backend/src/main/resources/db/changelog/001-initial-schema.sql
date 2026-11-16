--liquibase formatted sql

--changeset holmes:001-monitored-service
CREATE TABLE monitored_service (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(120) NOT NULL UNIQUE,
    display_name      VARCHAR(200),
    aliases           TEXT,
    environment       VARCHAR(60),
    repo_url          VARCHAR(500),
    git_provider      VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    default_branch    VARCHAR(120) NOT NULL DEFAULT 'main',
    code_path         VARCHAR(300),
    runbook           TEXT,
    auto_fix          BOOLEAN      NOT NULL DEFAULT FALSE,
    slack_channel     VARCHAR(120),
    email_recipients  TEXT,
    telegram_chat_id  VARCHAR(60),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

--changeset holmes:001-incident
CREATE TABLE incident (
    id                  BIGSERIAL PRIMARY KEY,
    fingerprint         VARCHAR(64)  NOT NULL UNIQUE,
    service_name        VARCHAR(120) NOT NULL,
    environment         VARCHAR(60),
    title               TEXT         NOT NULL,
    error_type          TEXT,
    level               VARCHAR(20),
    sample_message      TEXT,
    sample_stack_trace  TEXT,
    logger              TEXT,
    host                VARCHAR(200),
    source              VARCHAR(40),
    status              VARCHAR(20)  NOT NULL,
    severity            VARCHAR(20),
    occurrence_count    BIGINT       NOT NULL DEFAULT 1,
    first_seen_at       TIMESTAMPTZ  NOT NULL,
    last_seen_at        TIMESTAMPTZ  NOT NULL,
    status_changed_at   TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Scheduler claim query and stale re-queue.
CREATE INDEX idx_incident_status ON incident (status, status_changed_at);
CREATE INDEX idx_incident_service ON incident (service_name, last_seen_at DESC);
CREATE INDEX idx_incident_last_seen ON incident (last_seen_at DESC);

--changeset holmes:001-investigation
CREATE TABLE investigation (
    id                    BIGSERIAL PRIMARY KEY,
    incident_id           BIGINT REFERENCES incident (id) ON DELETE CASCADE,
    kind                  VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    provider              VARCHAR(40),
    model                 VARCHAR(120),
    question              TEXT,
    summary               TEXT,
    root_cause            TEXT,
    evidence              TEXT,
    suggested_fix         TEXT,
    confidence            VARCHAR(10),
    severity              VARCHAR(20),
    category              VARCHAR(30),
    code_fix_recommended  BOOLEAN NOT NULL DEFAULT FALSE,
    suspected_files       TEXT,
    report                TEXT,
    raw_answer            TEXT,
    steps                 INT     NOT NULL DEFAULT 0,
    tool_call_count       INT     NOT NULL DEFAULT 0,
    input_tokens          BIGINT  NOT NULL DEFAULT 0,
    output_tokens         BIGINT  NOT NULL DEFAULT 0,
    error                 TEXT,
    started_at            TIMESTAMPTZ NOT NULL,
    finished_at           TIMESTAMPTZ
);
CREATE INDEX idx_investigation_incident ON investigation (incident_id, started_at DESC);
CREATE INDEX idx_investigation_kind_started ON investigation (kind, started_at DESC);

--changeset holmes:001-remediation
CREATE TABLE remediation (
    id                BIGSERIAL PRIMARY KEY,
    incident_id       BIGINT NOT NULL REFERENCES incident (id) ON DELETE CASCADE,
    investigation_id  BIGINT REFERENCES investigation (id) ON DELETE SET NULL,
    status            VARCHAR(20) NOT NULL,
    model             VARCHAR(120),
    branch            VARCHAR(200),
    pr_url            VARCHAR(500),
    pr_number         VARCHAR(40),
    title             TEXT,
    description       TEXT,
    files_changed     TEXT,
    diff              TEXT,
    reason            TEXT,
    error             TEXT,
    steps             INT    NOT NULL DEFAULT 0,
    input_tokens      BIGINT NOT NULL DEFAULT 0,
    output_tokens     BIGINT NOT NULL DEFAULT 0,
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ
);
CREATE INDEX idx_remediation_incident ON remediation (incident_id, started_at DESC);
CREATE INDEX idx_remediation_status ON remediation (status);

--changeset holmes:001-tool-call
CREATE TABLE tool_call (
    id                BIGSERIAL PRIMARY KEY,
    investigation_id  BIGINT REFERENCES investigation (id) ON DELETE CASCADE,
    remediation_id    BIGINT REFERENCES remediation (id) ON DELETE CASCADE,
    sequence          INT          NOT NULL,
    tool_name         VARCHAR(150) NOT NULL,
    arguments         TEXT,
    output            TEXT,
    success           BOOLEAN      NOT NULL,
    duration_ms       BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_tool_call_investigation ON tool_call (investigation_id, sequence);
CREATE INDEX idx_tool_call_remediation ON tool_call (remediation_id, sequence);

--changeset holmes:001-ingestion-checkpoint
CREATE TABLE ingestion_checkpoint (
    name        VARCHAR(100) PRIMARY KEY,
    position    BIGINT       NOT NULL,
    updated_at  TIMESTAMPTZ
);

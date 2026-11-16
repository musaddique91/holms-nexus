--liquibase formatted sql

--changeset nexus:1-init
CREATE TABLE mcp_server (
    id           UUID PRIMARY KEY,
    name         VARCHAR(120) NOT NULL UNIQUE,
    description  TEXT,
    transport    VARCHAR(20)  NOT NULL,
    command      VARCHAR(500),
    args         TEXT         NOT NULL DEFAULT '[]',
    env          TEXT         NOT NULL DEFAULT '{}',
    url          VARCHAR(1000),
    headers      TEXT         NOT NULL DEFAULT '{}',
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    icon         VARCHAR(60),
    color        VARCHAR(20),
    catalog_id   VARCHAR(60),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE llm_provider (
    id             UUID PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    api_key        TEXT,
    base_url       VARCHAR(500),
    default_model  VARCHAR(200) NOT NULL,
    models         TEXT         NOT NULL DEFAULT '[]',
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE conversation (
    id           UUID PRIMARY KEY,
    title        VARCHAR(300) NOT NULL,
    provider_id  UUID REFERENCES llm_provider (id) ON DELETE SET NULL,
    model        VARCHAR(200),
    server_ids   TEXT         NOT NULL DEFAULT '[]',
    pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_conversation_updated ON conversation (updated_at DESC);

CREATE TABLE chat_message (
    id               UUID PRIMARY KEY,
    conversation_id  UUID         NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    role             VARCHAR(20)  NOT NULL,
    content          TEXT         NOT NULL,
    parts            TEXT         NOT NULL DEFAULT '[]',
    model            VARCHAR(200),
    duration_ms      BIGINT,
    error            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_message_conversation ON chat_message (conversation_id, created_at);

CREATE TABLE app_setting (
    key    VARCHAR(100) PRIMARY KEY,
    value  TEXT NOT NULL
);

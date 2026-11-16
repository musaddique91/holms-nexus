--liquibase formatted sql

--changeset nexus:2-workspaces
CREATE TABLE workspace (
    id            UUID PRIMARY KEY,
    name          VARCHAR(120) NOT NULL UNIQUE,
    description   TEXT,
    icon          VARCHAR(60),
    color         VARCHAR(20),
    all_servers   BOOLEAN      NOT NULL DEFAULT FALSE,
    server_ids    TEXT         NOT NULL DEFAULT '[]',
    instructions  TEXT,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

ALTER TABLE conversation ADD COLUMN workspace_id UUID REFERENCES workspace (id) ON DELETE SET NULL;
CREATE INDEX idx_conversation_workspace ON conversation (workspace_id, updated_at DESC);

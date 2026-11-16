--liquibase formatted sql

--changeset holmes:002-llm-settings
-- Runtime LLM configuration, edited on the Settings page. A single row (id = 1).
-- The API key is never stored here in plain text: it is AES-GCM encrypted by the app,
-- and it is not seeded (a key in a changelog would end up in git and in the image).
CREATE TABLE llm_settings (
    id                 SMALLINT     PRIMARY KEY CHECK (id = 1),
    provider           VARCHAR(40)  NOT NULL,
    model              VARCHAR(200) NOT NULL,
    fix_model          VARCHAR(200),
    base_url           VARCHAR(500),
    azure_deployment   VARCHAR(200),
    api_key_encrypted  TEXT,
    api_key_hint       VARCHAR(20),
    max_tokens         INT          NOT NULL DEFAULT 8192,
    max_tokens_param   VARCHAR(40)  NOT NULL DEFAULT 'max_tokens',
    temperature        NUMERIC(3,2) NOT NULL DEFAULT 0.10,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         VARCHAR(120)
);

-- Default: NVIDIA NIM free tier. Mistral Large 4 answered tool calls reliably and fast on the
-- free endpoint (Nov 2026); change it any time on the Settings page.
INSERT INTO llm_settings (id, provider, model, max_tokens, updated_by)
VALUES (1, 'nvidia', 'mistralai/mistral-large-4-1t-a52b', 8192, 'liquibase');

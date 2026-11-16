--liquibase formatted sql

--changeset holmes:003-notification-settings
-- Notification channels (Slack, e-mail, Telegram), edited on the Settings page. A single row
-- (id = 1). Not seeded: until the first save Holmes uses the SLACK_* / EMAIL_* / TELEGRAM_* /
-- MAIL_* environment variables, and the first save copies them in. Webhook URLs, tokens and the
-- SMTP password are AES-GCM encrypted by the app; *_hint columns hold a masked preview only.
CREATE TABLE notification_settings (
    id                        SMALLINT     PRIMARY KEY CHECK (id = 1),
    min_severity              VARCHAR(20)  NOT NULL DEFAULT 'LOW',

    slack_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    slack_webhook_encrypted   TEXT,
    slack_webhook_hint        VARCHAR(60),
    slack_bot_token_encrypted TEXT,
    slack_bot_token_hint      VARCHAR(40),
    slack_channel             VARCHAR(120),

    email_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    smtp_host                 VARCHAR(255),
    smtp_port                 INT,
    smtp_username             VARCHAR(255),
    smtp_password_encrypted   TEXT,
    smtp_starttls             BOOLEAN      NOT NULL DEFAULT TRUE,
    email_from                VARCHAR(255),
    email_to                  TEXT,

    telegram_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    telegram_token_encrypted  TEXT,
    telegram_token_hint       VARCHAR(40),
    telegram_chat_id          VARCHAR(120),

    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by                VARCHAR(120)
);

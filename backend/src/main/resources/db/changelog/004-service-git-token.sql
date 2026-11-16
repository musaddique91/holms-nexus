--liquibase formatted sql

--changeset holmes:004-service-git-token
-- Optional per-service git personal access token (Services page). AES-GCM encrypted by the app;
-- used to clone the code, for the read tools and to open the fix PR. Falls back to the global
-- GITHUB_TOKEN / GITLAB_TOKEN when empty.
ALTER TABLE monitored_service ADD COLUMN git_token_encrypted TEXT;
ALTER TABLE monitored_service ADD COLUMN git_token_hint VARCHAR(40);

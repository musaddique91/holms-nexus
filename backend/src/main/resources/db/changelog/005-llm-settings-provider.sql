--liquibase formatted sql

--changeset holmes:005-llm-settings-provider
-- MVX Nexus: MVXHolmes takes its model from the shared provider list (llm_provider, managed under
-- Models). provider_id NULL = the original standalone settings in this table still apply.
ALTER TABLE llm_settings ADD COLUMN provider_id UUID REFERENCES llm_provider (id) ON DELETE SET NULL;

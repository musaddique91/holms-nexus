--liquibase formatted sql

--changeset nexus:3-catalog-id-length
ALTER TABLE mcp_server ALTER COLUMN catalog_id TYPE VARCHAR(300);

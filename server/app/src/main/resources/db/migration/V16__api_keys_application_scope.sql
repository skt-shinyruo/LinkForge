ALTER TABLE api_keys
    ADD COLUMN application_id BIGINT NULL AFTER tenant_id,
    ADD KEY idx_api_keys_application_id (application_id);

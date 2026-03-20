ALTER TABLE short_links
    ADD COLUMN application_id BIGINT NULL AFTER tenant_id,
    ADD COLUMN domain_id BIGINT NULL AFTER application_id,
    ADD COLUMN lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER code;

CREATE INDEX idx_short_links_tenant_application_created_at
    ON short_links (tenant_id, application_id, created_at);

CREATE INDEX idx_short_links_domain_code
    ON short_links (domain_id, code);

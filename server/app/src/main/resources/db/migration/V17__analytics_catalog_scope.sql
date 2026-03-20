ALTER TABLE analytics_link_catalog
    ADD COLUMN application_id BIGINT NULL AFTER link_id,
    ADD COLUMN domain_id BIGINT NULL AFTER application_id,
    ADD KEY idx_alc_tenant_application (tenant_id, application_id),
    ADD KEY idx_alc_tenant_domain (tenant_id, domain_id);

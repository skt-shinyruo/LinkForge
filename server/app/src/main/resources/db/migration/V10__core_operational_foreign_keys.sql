-- Operational integrity hardening: add foreign keys only for live transactional tables.
-- Intentionally do not add analytics/history FKs to short_links because retention outlives source-row deletion.

ALTER TABLE users
    ADD CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE api_keys
    ADD CONSTRAINT fk_api_keys_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE short_links
    ADD CONSTRAINT fk_short_links_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE tags
    ADD CONSTRAINT fk_tags_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE link_tags
    ADD CONSTRAINT fk_link_tags_link
        FOREIGN KEY (link_id) REFERENCES short_links (id);

ALTER TABLE link_tags
    ADD CONSTRAINT fk_link_tags_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id);

-- Stable keyset pagination for the common tenant/archive and application-scoped list paths.
CREATE INDEX idx_short_links_tenant_archived_created_id
    ON short_links (tenant_id, archived_at, created_at, id);

CREATE INDEX idx_short_links_tenant_application_archived_created_id
    ON short_links (tenant_id, application_id, archived_at, created_at, id);

-- Split code prefix lookup from natural-language URL/note search so each can use an appropriate index.
CREATE FULLTEXT INDEX idx_short_links_search_text
    ON short_links (original_url, note);

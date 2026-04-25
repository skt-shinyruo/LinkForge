ALTER TABLE short_links
    DROP INDEX uk_short_links_domain_code,
    ADD COLUMN domain_route_key BIGINT GENERATED ALWAYS AS (IFNULL(domain_id, 0)) STORED AFTER domain_id,
    ADD UNIQUE KEY uk_short_links_domain_route_code (domain_route_key, code),
    ADD KEY idx_short_links_domain_code (domain_id, code);

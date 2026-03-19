ALTER TABLE short_links
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

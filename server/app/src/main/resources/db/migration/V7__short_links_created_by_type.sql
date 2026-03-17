-- Audit hardening: distinguish who created a short link (USER vs API_KEY).

ALTER TABLE short_links
    ADD COLUMN created_by_type VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER query_forward_allowlist;

-- Defensive backfill (MySQL will usually fill with DEFAULT automatically for existing rows).
UPDATE short_links
SET created_by_type = 'USER'
WHERE created_by_type IS NULL OR created_by_type = '';


-- Enforce case-sensitive semantics for short codes (e.g. Abcdef != abcdef).
--
-- Rationale:
-- - Application allows mixed-case Base62 codes and custom codes [0-9A-Za-z].
-- - MySQL default collations (e.g. utf8mb4_unicode_ci) are case-insensitive and would
--   incorrectly treat codes differing only by case as duplicates (also impacting outbox de-dup).
--
-- Implementation:
-- - Use ASCII + binary collation for predictable, case-sensitive uniqueness and lookups.

ALTER TABLE short_links
  MODIFY COLUMN code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE link_cache_outbox
  MODIFY COLUMN code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;


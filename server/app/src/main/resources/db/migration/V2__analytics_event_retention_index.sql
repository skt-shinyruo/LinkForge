-- LinkForge: Analytics retention performance
--
-- The retention job deletes by `created_at` with a LIMIT, so we need an index to avoid full table scans
-- and long-running delete loops on large datasets.
ALTER TABLE link_visit_events
    ADD INDEX idx_visit_created_at (created_at);


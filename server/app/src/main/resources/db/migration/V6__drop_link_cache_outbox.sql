-- LinkForge: drop legacy shortlink cache outbox
--
-- The new architecture relies on:
-- - shortlink -> integration_events (append in same tx)
-- - redirect projector -> redirect_link_projection + Redis cache side effects
--
-- Therefore link_cache_outbox is obsolete.

DROP TABLE IF EXISTS link_cache_outbox;


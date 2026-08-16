-- LinkForge: generation-aware redirect cache invalidation intents.
--
-- The default keeps older application instances able to insert rows while a rolling
-- deployment is in progress. Existing rows are backfilled to generation 1 by MySQL.

ALTER TABLE redirect_cache_invalidation_outbox
  ADD COLUMN generation BIGINT NOT NULL DEFAULT 1 AFTER status;

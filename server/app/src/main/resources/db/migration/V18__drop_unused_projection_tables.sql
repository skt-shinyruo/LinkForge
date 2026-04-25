-- LinkForge: single-runtime simplification removes independent redirect and
-- analytics shortlink projection storage from the active schema.

DROP TABLE IF EXISTS redirect_link_projection;
DROP TABLE IF EXISTS analytics_link_catalog;

-- LinkForge: Redirect read model projection (from shortlink integration events)
--
-- Redirect resolves by `code`, so projection primary key is `code`.

CREATE TABLE IF NOT EXISTS redirect_link_projection (
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  original_url TEXT NOT NULL,
  enabled BIT(1) NOT NULL,
  expires_at DATETIME NULL,
  redirect_status_code INT NULL,
  preview_enabled BIT(1) NOT NULL,
  unavailable_landing_url TEXT NULL,
  query_forward_mode VARCHAR(16) NULL,
  query_forward_allowlist VARCHAR(1024) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_rlp_tenant_link (tenant_id, link_id),
  KEY idx_rlp_link_id (link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


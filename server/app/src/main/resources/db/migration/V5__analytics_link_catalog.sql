-- LinkForge: Analytics link catalog projection (from shortlink integration events)
--
-- Purpose: remove analytics topLinks N+1 lookups to shortlink write DB / ports.

CREATE TABLE IF NOT EXISTS analytics_link_catalog (
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  original_url TEXT NULL,
  archived_at DATETIME NULL,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, link_id),
  KEY idx_alp_link_id (link_id),
  KEY idx_alp_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


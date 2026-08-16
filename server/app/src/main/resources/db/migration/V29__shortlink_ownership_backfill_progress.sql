-- Durable, tenant-scoped progress for bounded legacy short-link ownership reconciliation.

CREATE TABLE shortlink_ownership_backfill_checkpoints (
  tenant_id BIGINT NOT NULL PRIMARY KEY,
  application_id BIGINT NOT NULL,
  domain_id BIGINT NOT NULL,
  last_scanned_link_id BIGINT NOT NULL DEFAULT 0,
  scan_exhausted BIT(1) NOT NULL DEFAULT b'0',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shortlink_ownership_backfill_items (
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  domain_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, link_id),
  KEY idx_shortlink_backfill_actionable (tenant_id, status, updated_at, link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

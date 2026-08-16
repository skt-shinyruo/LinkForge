-- Platform-owned coordination rows for transactional legacy binding reconciliation.

CREATE TABLE platform_legacy_binding_locks (
  tenant_id BIGINT NOT NULL PRIMARY KEY,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

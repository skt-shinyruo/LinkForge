-- LinkForge: durable redirect cache invalidation retry queue

CREATE TABLE IF NOT EXISTS redirect_cache_invalidation_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  domain_id BIGINT NULL,
  -- domain_scope normalizes NULL domain_id to 0 so one unscoped link key can be deduplicated.
  domain_scope BIGINT NOT NULL,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  next_attempt_at DATETIME NOT NULL,
  processed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rci_outbox_link_key (tenant_id, domain_scope, code),
  KEY idx_rci_outbox_due (status, next_attempt_at, id),
  KEY idx_rci_outbox_processed (status, processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

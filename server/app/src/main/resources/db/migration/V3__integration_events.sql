-- LinkForge: Integration Event Log + consumer checkpoints + dead letter
--
-- Semantics:
-- - All *_at fields use UTC meaning (MySQL DATETIME stores no timezone).
-- - integration_events is append-only; consumers track progress independently via checkpoints.

CREATE TABLE IF NOT EXISTS integration_events (
  seq BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  producer VARCHAR(64) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  tenant_id BIGINT NULL,
  aggregate_type VARCHAR(64) NULL,
  aggregate_id BIGINT NULL,
  occurred_at DATETIME NOT NULL,
  payload_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ie_event_id (event_id),
  KEY idx_ie_producer_seq (producer, seq),
  KEY idx_ie_type_seq (event_type, seq),
  KEY idx_ie_tenant_seq (tenant_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_consumer_checkpoint (
  consumer VARCHAR(64) NOT NULL PRIMARY KEY,
  last_seq BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_consumer_dead_letter (
  consumer VARCHAR(64) NOT NULL,
  seq BIGINT NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  producer VARCHAR(64) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  occurred_at DATETIME NOT NULL,
  payload_json JSON NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  first_failed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_failed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (consumer, seq),
  KEY idx_icdl_consumer_failed_at (consumer, last_failed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


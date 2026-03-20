-- LinkForge governance bootstrap schema

CREATE TABLE IF NOT EXISTS approval_requests (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  target_application_id BIGINT NULL,
  requested_by_user_id BIGINT NOT NULL,
  requested_by_email VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL,
  approver_user_id BIGINT NULL,
  approver_email VARCHAR(256) NULL,
  decision_reason VARCHAR(512) NULL,
  before_snapshot TEXT NULL,
  after_snapshot TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at DATETIME NULL,
  executed_at DATETIME NULL,
  KEY idx_approval_requests_tenant_status (tenant_id, status),
  KEY idx_approval_requests_operation_type (operation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  actor_user_id BIGINT NOT NULL,
  actor_email VARCHAR(256) NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NOT NULL,
  request_id BIGINT NULL,
  before_snapshot TEXT NULL,
  after_snapshot TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_logs_tenant_created_at (tenant_id, created_at),
  KEY idx_audit_logs_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

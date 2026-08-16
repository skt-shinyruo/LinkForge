-- LinkForge database baseline for disposable development environments.
-- Run against an empty MySQL 8 database.

CREATE TABLE tenants (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenants_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  email VARCHAR(256) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  token_version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_tenant_email (tenant_id, email),
  KEY idx_users_tenant_id (tenant_id),
  CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  PRIMARY KEY (user_id, role_code),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE api_keys (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  application_id BIGINT NULL,
  name VARCHAR(128) NOT NULL,
  key_hash VARCHAR(255) NOT NULL,
  key_id VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL,
  last_used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_api_keys_tenant_id (tenant_id),
  KEY idx_api_keys_status (status),
  KEY idx_api_keys_application_id (application_id),
  CONSTRAINT fk_api_keys_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE applications (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  application_key VARCHAR(64) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_applications_tenant_key (tenant_id, application_key),
  KEY idx_applications_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE domains (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  application_id BIGINT NULL,
  hostname VARCHAR(255) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  trust_class VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_domains_hostname (hostname),
  KEY idx_domains_tenant_scope (tenant_id, scope),
  KEY idx_domains_application_id (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_domain_authorizations (
  application_id BIGINT NOT NULL,
  domain_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (application_id, domain_id),
  KEY idx_application_domain_authorizations_domain_id (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_quotas (
  application_id BIGINT NOT NULL PRIMARY KEY,
  monthly_link_limit INT NOT NULL,
  monthly_click_limit BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_policies (
  application_id BIGINT NOT NULL PRIMARY KEY,
  default_domain_scope VARCHAR(32) NOT NULL,
  default_redirect_status_code INT NOT NULL,
  preview_enabled INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE short_links (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  application_id BIGINT NULL,
  domain_id BIGINT NULL,
  domain_route_key BIGINT GENERATED ALWAYS AS (IFNULL(domain_id, 0)) STORED,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  original_url TEXT NOT NULL,
  note VARCHAR(512) NULL,
  enabled BIT(1) NOT NULL,
  expires_at DATETIME NULL,
  archived_at DATETIME NULL,
  redirect_status_code INT NULL,
  preview_enabled BIT(1) NOT NULL DEFAULT b'0',
  unavailable_landing_url TEXT NULL,
  query_forward_mode VARCHAR(16) NULL,
  query_forward_allowlist VARCHAR(1024) NULL,
  created_by_type VARCHAR(16) NOT NULL DEFAULT 'USER',
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_short_links_domain_route_code (domain_route_key, code),
  KEY idx_short_links_tenant_created_at (tenant_id, created_at),
  KEY idx_short_links_tenant_enabled (tenant_id, enabled),
  KEY idx_short_links_tenant_archived_created_at (tenant_id, archived_at, created_at),
  KEY idx_short_links_tenant_application_created_at (tenant_id, application_id, created_at),
  KEY idx_short_links_code (code),
  KEY idx_short_links_domain_code (domain_id, code),
  KEY idx_short_links_tenant_archived_created_id (tenant_id, archived_at, created_at, id),
  KEY idx_short_links_tenant_application_archived_created_id
    (tenant_id, application_id, archived_at, created_at, id),
  FULLTEXT KEY idx_short_links_search_text (original_url, note),
  CONSTRAINT fk_short_links_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tags (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tags_tenant_name (tenant_id, name),
  KEY idx_tags_tenant_id (tenant_id),
  CONSTRAINT fk_tags_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link_tags (
  link_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (link_id, tag_id),
  KEY idx_link_tags_tag_id (tag_id),
  CONSTRAINT fk_link_tags_link FOREIGN KEY (link_id) REFERENCES short_links (id),
  CONSTRAINT fk_link_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link_stats_daily (
  link_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  day DATE NOT NULL,
  pv BIGINT NOT NULL,
  uv BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (link_id, day),
  KEY idx_stats_tenant_day_link (tenant_id, day, link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link_visit_events (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  occurred_at DATETIME NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  ip_hash VARCHAR(64) NULL,
  ua_raw VARCHAR(512) NULL,
  ua_family VARCHAR(64) NULL,
  os_family VARCHAR(64) NULL,
  device_type VARCHAR(32) NULL,
  referer_domain VARCHAR(255) NULL,
  language VARCHAR(32) NULL,
  utm_source VARCHAR(128) NULL,
  utm_medium VARCHAR(128) NULL,
  utm_campaign VARCHAR(128) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_visit_tenant_request (tenant_id, request_id),
  KEY idx_visit_tenant_link_time (tenant_id, link_id, occurred_at),
  KEY idx_visit_tenant_time (tenant_id, occurred_at),
  KEY idx_visit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link_stats_dim_daily (
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  day DATE NOT NULL,
  dim_type VARCHAR(32) NOT NULL,
  dim_value VARCHAR(255) NOT NULL,
  pv BIGINT NOT NULL,
  uv BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, link_id, day, dim_type, dim_value),
  KEY idx_dim_tenant_day_type (tenant_id, day, dim_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analytics_link_catalog (
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  application_id BIGINT NULL,
  domain_id BIGINT NULL,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  original_url TEXT NULL,
  archived_at DATETIME NULL,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, link_id),
  KEY idx_alp_link_id (link_id),
  KEY idx_alp_tenant_deleted (tenant_id, deleted),
  KEY idx_alc_tenant_application (tenant_id, application_id),
  KEY idx_alc_tenant_domain (tenant_id, domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analytics_scope_stats_daily (
  tenant_id BIGINT NOT NULL,
  scope_type VARCHAR(16) NOT NULL,
  scope_id BIGINT NOT NULL,
  day DATE NOT NULL,
  uv BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, scope_type, scope_id, day),
  KEY idx_assd_tenant_day_scope (tenant_id, day, scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE integration_events (
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

CREATE TABLE integration_consumer_checkpoint (
  consumer VARCHAR(64) NOT NULL PRIMARY KEY,
  last_seq BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE integration_consumer_dead_letter (
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

CREATE TABLE application_link_monthly_usages (
  tenant_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  month_start DATE NOT NULL,
  used_count BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, application_id, month_start),
  KEY idx_application_link_monthly_usages_application_month (application_id, month_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE redirect_cache_invalidation_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  domain_id BIGINT NULL,
  domain_scope BIGINT NOT NULL,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  generation BIGINT NOT NULL DEFAULT 1,
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

CREATE TABLE approval_requests (
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
  KEY idx_approval_requests_operation_type (operation_type),
  KEY idx_approval_requests_tenant_created_id (tenant_id, created_at DESC, id DESC),
  KEY idx_approval_requests_tenant_status_created_id (tenant_id, status, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
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
  KEY idx_audit_logs_request_id (request_id),
  KEY idx_audit_logs_tenant_created_id (tenant_id, created_at DESC, id DESC),
  KEY idx_audit_logs_tenant_action_created_id (tenant_id, action_type, created_at DESC, id DESC),
  KEY idx_audit_logs_tenant_resource_created_id (tenant_id, resource_type, created_at DESC, id DESC),
  KEY idx_audit_logs_tenant_action_resource_created_id
    (tenant_id, action_type, resource_type, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

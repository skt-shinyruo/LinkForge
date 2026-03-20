-- LinkForge platform control-plane bootstrap schema

CREATE TABLE IF NOT EXISTS applications (
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

CREATE TABLE IF NOT EXISTS domains (
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

CREATE TABLE IF NOT EXISTS application_domain_authorizations (
  application_id BIGINT NOT NULL,
  domain_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (application_id, domain_id),
  KEY idx_application_domain_authorizations_domain_id (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS application_quotas (
  application_id BIGINT NOT NULL PRIMARY KEY,
  monthly_link_limit INT NOT NULL,
  monthly_click_limit BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS application_policies (
  application_id BIGINT NOT NULL PRIMARY KEY,
  default_domain_scope VARCHAR(32) NOT NULL,
  default_redirect_status_code INT NOT NULL,
  preview_enabled INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

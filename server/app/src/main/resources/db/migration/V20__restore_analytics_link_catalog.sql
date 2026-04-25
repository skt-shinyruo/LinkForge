-- LinkForge: restore analytics-owned shortlink catalog for historical scoped reports.
--
-- Application/domain analytics must not derive ownership from the mutable
-- short_links table because deleted links still have historical stats.

CREATE TABLE IF NOT EXISTS analytics_link_catalog (
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

INSERT INTO analytics_link_catalog (
    tenant_id,
    link_id,
    application_id,
    domain_id,
    code,
    original_url,
    archived_at,
    deleted
)
SELECT
    tenant_id,
    id,
    application_id,
    domain_id,
    code,
    original_url,
    archived_at,
    b'0'
FROM short_links
ON DUPLICATE KEY UPDATE
    application_id = VALUES(application_id),
    domain_id = VALUES(domain_id),
    code = VALUES(code),
    original_url = VALUES(original_url),
    archived_at = VALUES(archived_at),
    deleted = b'0';

DELETE FROM integration_consumer_checkpoint
WHERE consumer = 'analytics-link-catalog-projector';

CREATE TABLE IF NOT EXISTS application_link_monthly_usages (
  tenant_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  month_start DATE NOT NULL,
  used_count BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, application_id, month_start),
  KEY idx_application_link_monthly_usages_application_month (application_id, month_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

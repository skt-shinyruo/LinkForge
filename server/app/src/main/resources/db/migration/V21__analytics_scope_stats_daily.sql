CREATE TABLE IF NOT EXISTS analytics_scope_stats_daily (
  tenant_id BIGINT NOT NULL,
  scope_type VARCHAR(16) NOT NULL,
  scope_id BIGINT NOT NULL,
  day DATE NOT NULL,
  uv BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, scope_type, scope_id, day),
  KEY idx_assd_tenant_day_scope (tenant_id, day, scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

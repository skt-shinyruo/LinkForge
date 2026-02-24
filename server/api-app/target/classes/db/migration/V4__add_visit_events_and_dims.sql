-- 统计增强：访问明细事件表 + 维度按天聚合表

CREATE TABLE IF NOT EXISTS link_visit_events (
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
  KEY idx_visit_tenant_link_time (tenant_id, link_id, occurred_at),
  KEY idx_visit_tenant_time (tenant_id, occurred_at),
  UNIQUE KEY uk_visit_tenant_request (tenant_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS link_stats_dim_daily (
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


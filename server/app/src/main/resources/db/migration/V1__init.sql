-- LinkForge 初始表结构（MVP，已 squash 到最新 schema）
-- 注意：生产环境可根据运维规范调整字符集/时区/权限等

CREATE TABLE IF NOT EXISTS tenants (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenants_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  email VARCHAR(256) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- IAM 加固：email 全局唯一（用于简化登录，不再要求选择租户）
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_tenant_email (tenant_id, email),
  KEY idx_users_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  PRIMARY KEY (user_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS api_keys (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  key_hash VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_api_keys_tenant_id (tenant_id),
  KEY idx_api_keys_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS short_links (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  -- 短码严格区分大小写：使用 ASCII + binary collation（Abcdef != abcdef）
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_url TEXT NOT NULL,
  note VARCHAR(512) NULL,
  enabled BIT(1) NOT NULL,
  expires_at DATETIME NULL,
  -- 生命周期治理：归档字段（可恢复）
  archived_at DATETIME NULL,
  -- 跳转体验与跳转策略字段（可选，向后兼容）
  redirect_status_code INT NULL,
  preview_enabled BIT(1) NOT NULL DEFAULT b'0',
  unavailable_landing_url TEXT NULL,
  query_forward_mode VARCHAR(16) NULL,
  query_forward_allowlist VARCHAR(1024) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_short_links_code (code),
  KEY idx_short_links_tenant_created_at (tenant_id, created_at),
  KEY idx_short_links_tenant_enabled (tenant_id, enabled),
  KEY idx_short_links_tenant_archived_created_at (tenant_id, archived_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tags (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tags_tenant_name (tenant_id, name),
  KEY idx_tags_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS link_tags (
  link_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (link_id, tag_id),
  KEY idx_link_tags_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS link_stats_daily (
  link_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  day DATE NOT NULL,
  pv BIGINT NOT NULL,
  uv BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (link_id, day),
  -- 统计查询索引优化：为 Top 链接报表按租户+日期范围聚合提供更好的访问路径
  KEY idx_stats_tenant_day_link (tenant_id, day, link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

-- LinkForge: shortlink cache outbox（durable refresh/evict）

CREATE TABLE IF NOT EXISTS link_cache_outbox (
  -- 短码全局唯一，因此可直接作为 outbox key，实现天然去重/合并
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  status VARCHAR(16) NOT NULL,
  available_at DATETIME NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  processed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_lco_status_available_at (status, available_at),
  KEY idx_lco_status_processed_at (status, processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

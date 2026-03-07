-- LinkForge: shortlink cache outbox (durable refresh/evict)
-- 目的：保证短链缓存刷新/驱逐具备“最终一致”能力（commit 后即使进程崩溃也不丢动作）。

CREATE TABLE IF NOT EXISTS link_cache_outbox (
  -- 短码全局唯一，因此可直接作为 outbox key，实现天然去重/合并
  code VARCHAR(32) NOT NULL PRIMARY KEY,
  status VARCHAR(16) NOT NULL,
  available_at DATETIME NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  processed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_lco_status_available_at (status, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


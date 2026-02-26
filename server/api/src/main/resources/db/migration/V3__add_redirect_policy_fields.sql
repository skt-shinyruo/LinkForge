-- 为短链增加跳转体验与跳转策略字段（可选，向后兼容）

ALTER TABLE short_links
  ADD COLUMN redirect_status_code INT NULL,
  ADD COLUMN preview_enabled BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN unavailable_landing_url TEXT NULL,
  ADD COLUMN query_forward_mode VARCHAR(16) NULL,
  ADD COLUMN query_forward_allowlist VARCHAR(1024) NULL;


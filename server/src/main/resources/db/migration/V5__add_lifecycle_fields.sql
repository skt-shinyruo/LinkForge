-- 生命周期治理增强：短链归档字段（可恢复）

ALTER TABLE short_links
  ADD COLUMN archived_at DATETIME NULL AFTER expires_at;

ALTER TABLE short_links
  ADD KEY idx_short_links_tenant_archived_created_at (tenant_id, archived_at, created_at);


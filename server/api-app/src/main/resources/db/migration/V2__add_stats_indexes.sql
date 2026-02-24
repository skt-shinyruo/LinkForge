-- 统计查询索引优化（为 Top 链接报表按租户+日期范围聚合提供更好的访问路径）

-- 说明：
-- - V1 中已有 idx_stats_tenant_day(tenant_id, day)
-- - Top 报表常见模式：WHERE tenant_id=? AND day BETWEEN ? AND ? GROUP BY link_id
-- - 使用 (tenant_id, day, link_id) 复合索引可覆盖 day 范围扫描并减少回表/排序成本

ALTER TABLE link_stats_daily
  DROP INDEX idx_stats_tenant_day,
  ADD INDEX idx_stats_tenant_day_link (tenant_id, day, link_id);


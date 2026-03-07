-- LinkForge: link_cache_outbox cleanup/monitor indexes
-- 目的：为 DONE 清理与 PENDING 监控提供可预测的索引访问路径，避免全表扫带来的抖动。

CREATE INDEX idx_lco_status_processed_at ON link_cache_outbox (status, processed_at);


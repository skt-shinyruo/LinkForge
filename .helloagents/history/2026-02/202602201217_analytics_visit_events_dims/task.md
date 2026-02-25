# 任务清单：P1 统计维度与访问明细增强（A+B）

Directory: `.helloagents/plan/202602201217_analytics_visit_events_dims/`

---

## 1. 采集模型（shared / platform）
- [√] 1.1 扩展 `VisitInfo` 字段并保持向后兼容：`server/shared/src/main/java/com/linkforge/platform/web/VisitInfo.java`，验证 why.md#requirement-analytics-dimensions-dim-distribution
- [√] 1.2 新增维度归一化工具（referer domain / language / utm 提取 / 长度限制）：`server/shared/src/main/java/com/linkforge/analytics/service/VisitDimensionNormalizer.java`（新），验证 why.md#requirement-analytics-dimensions-dim-distribution

## 2. Edge 侧采集写入（redirect-edge）
- [√] 2.1 在 `EdgeRiskControlFilter` 构造更完整的 `VisitInfo`（referer/accept-language）：`server/edge-app/src/main/java/com/linkforge/edge/web/EdgeRiskControlFilter.java`，验证 why.md#requirement-analytics-dimensions
- [√] 2.2 扩展 `AnalyticsService`：在现有 PV/UV 基础上追加（可配置）维度计数与事件写入（Redis Stream，best-effort）：`server/shared/src/main/java/com/linkforge/analytics/service/AnalyticsService.java`，验证 why.md#requirement-analytics-visit-events-event-ingest

## 3. Redis Key 与配置（shared）
- [√] 3.1 扩展 `AnalyticsKeys`：新增维度 key、维度活跃集合 key、stream key：`server/shared/src/main/java/com/linkforge/analytics/service/AnalyticsKeys.java`，验证 why.md#requirement-analytics-dimensions
- [√] 3.2 扩展 `AppProperties.Analytics`：增加 events/dims 的开关、采样率、stream maxlen、明细留存天数、UTM 追踪白名单：`server/shared/src/main/java/com/linkforge/platform/config/AppProperties.java`，验证 why.md#requirement-analytics-visit-events

## 4. 数据库迁移（api-service）
- [√] 4.1 新增 Flyway migration：创建 `link_visit_events` 与 `link_stats_dim_daily`：`server/api-app/src/main/resources/db/migration/V4__add_visit_events_and_dims.sql`（新），验证 why.md#requirement-analytics-visit-events

## 5. API 侧作业：事件落库 + 维度聚合 flush（api-service）
- [√] 5.1 新增 stream 消费作业（批量 insert、幂等）：`server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsEventIngestJob.java`（新），验证 why.md#requirement-analytics-visit-events-event-ingest
- [√] 5.2 新增明细留存清理作业：`server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsEventRetentionJob.java`（新），验证 why.md#requirement-analytics-visit-events-event-ingest
- [√] 5.3 新增维度 flush 作业（从 Redis 读维度计数/UV）：`server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsDimensionFlushJob.java`（新），验证 why.md#requirement-analytics-dimensions-dim-distribution
- [√] 5.4 修复访问明细时间边界一致性：`occurred_at` 以 UTC 语义写入/查询，避免 DATETIME + Timestamp 时区偏移导致 `/events` 查询为空：`server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsEventIngestJob.java`、`server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsQueryService.java`

## 6. 查询 API（api-service）
- [√] 6.1 扩展查询服务：维度分布查询 + 最近事件查询：`server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsQueryService.java`，验证 why.md#requirement-analytics-visit-events-event-query
- [√] 6.2 扩展控制器：新增 `/dimensions` 与 `/events` 端点（仅后台可用）：`server/api-app/src/main/java/com/linkforge/analytics/web/StatsController.java`，验证 why.md#requirement-analytics-dimensions

## 7. 安全检查
- [√] 7.1 执行安全检查（输入校验、敏感信息处理、权限控制、EHRB 风险规避），重点：referer/utm 归一化与落库脱敏
  > Note: 明细表默认落 `ip_hash`（不落明文 IP）；referer 仅落 domain；UTM 仅白名单采集并截断；维度/事件查询端点仅后台权限可用（`!hasRole('OPENAPI')`）

## 8. 文档同步（知识库）
- [√] 8.1 更新数据模型文档：`.helloagents/wiki/data.md`
- [√] 8.2 更新 analytics 模块说明：`.helloagents/wiki/modules/analytics.md`
- [√] 8.3 更新 redirect-edge 模块对统计字段的描述（如需要）：`.helloagents/wiki/modules/redirect-edge.md`
- [√] 8.4 更新变更记录：`.helloagents/CHANGELOG.md`

## 9. 测试
- [√] 9.1 单元测试：维度归一化与指纹/哈希稳定性：`server/shared/src/test/java/com/linkforge/analytics/service/VisitorFingerprintTest.java`（扩展）或新增 `VisitDimensionNormalizerTest.java`
- [√] 9.2 集成测试：Redis Stream → MySQL 落库作业（可选，若项目已有 Testcontainers Redis）：`server/api-app/src/test/java/...`

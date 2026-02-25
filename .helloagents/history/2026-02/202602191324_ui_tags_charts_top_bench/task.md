# Task List: 管理后台标签管理 + 统计趋势图/Top 报表 + Redirect 压测

Directory: `.helloagents/history/2026-02/202602191324_ui_tags_charts_top_bench/`

---

## 1. analytics（Top 链接报表 API）
- [√] 1.1 新增 Top 链接查询 DTO 与查询方法（JdbcTemplate 聚合 + JOIN），实现于 `server/src/main/java/com/linkforge/analytics/service/AnalyticsQueryService.java`，验证 why.md#requirement-analytics-top-links-api / why.md#scenario-top-links-range
- [√] 1.2 在 `server/src/main/java/com/linkforge/analytics/web/StatsController.java` 增加 `[GET] /api/v1/stats/top-links`，包含参数校验（from<=to、limit 默认与上限）与权限限制（禁止 OPENAPI），验证 why.md#requirement-analytics-top-links-api / why.md#scenario-top-links-range，依赖任务 1.1
- [√] 1.3 扩展集成测试：构造多次 redirect/flush 后查询 Top 列表并断言排序/租户隔离，更新 `server/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`，依赖任务 1.2
- [√] 1.4 （索引优化）补充统计表索引 Flyway V2，新增 `server/src/main/resources/db/migration/V2__add_stats_indexes.sql`

## 2. admin-ui（图表库 + 统计页增强）
- [√] 2.1 引入 ECharts 依赖并补齐类型定义，更新 `web/package.json`
- [√] 2.2 新增轻量图表组件（折线图），新增 `web/src/components/LineChart.vue`（或等效命名），验证 why.md#requirement-ui-stats-dashboard / why.md#scenario-ui-tenant-trend-7-30
- [√] 2.3 增强统计页：支持 7/30 天切换、租户趋势图、Top 链接表格（调用新 API），更新 `web/src/views/StatsView.vue`，验证 why.md#requirement-ui-stats-dashboard / why.md#scenario-ui-tenant-trend-7-30 与 why.md#scenario-ui-top-links
- [√] 2.4 更新类型定义（TopLinkStat 等），更新 `web/src/services/types.ts`

## 3. admin-ui（标签管理 UI）
- [√] 3.1 新增标签管理页：列表 + 创建，新增 `web/src/views/TagsView.vue`，验证 why.md#requirement-ui-tags-management / why.md#scenario-ui-tags-list-create
- [√] 3.2 增加路由与导航入口，更新 `web/src/router/index.ts` 与必要的页面按钮（如 `web/src/views/LinksView.vue`）

## 4. redirect（压测脚本 + 结果记录）
- [√] 4.1 新增 k6 压测脚本与说明文档，新增 `bench/redirect/redirect.js`、`bench/redirect/README.md`，验证 why.md#requirement-redirect-benchmark / why.md#scenario-k6-baseline-1000qps
- [√] 4.2 执行一次本地基线压测并记录结果（环境 + 指标 + 结论），新增/更新 `bench/redirect/results.md`

## 5. Security Check
- [√] 5.1 执行安全检查（权限控制、tenant 隔离、输入校验、避免泄露敏感信息；禁止对生产环境压测的文档提示）

## 6. Documentation Update（SSOT 同步）
- [√] 6.1 更新 API 手册：补充 `/api/v1/stats/top-links`，更新 `.helloagents/wiki/api.md`
- [√] 6.2 更新模块文档：admin-ui/analytics/redirect 的能力与状态，更新 `.helloagents/wiki/modules/admin-ui.md`、`.helloagents/wiki/modules/analytics.md`、`.helloagents/wiki/modules/redirect.md`
- [√] 6.3 更新变更记录，更新 `.helloagents/CHANGELOG.md`

## 7. Testing
- [√] 7.1 后端：执行 `mvn test`（Java 21），记录关键结果
- [√] 7.2 前端：执行 `npm run build`，记录关键结果

# Task List: Top 报表支持按 UV 排序 + 图表更细粒度按需加载

Directory: `.helloagents/history/2026-02/202602191429_top_uv_sort_chart_lazyload/`

---

## 1. analytics（Top 报表排序）
- [√] 1.1 扩展 Top 报表 API：支持 `sortBy=pv|uv`，更新 `server/api-app/src/main/java/com/linkforge/analytics/web/StatsController.java`，验证 why.md#requirement-analytics-top-links-sort / why.md#scenario-top-links-sortBy-uv
- [√] 1.2 扩展查询层：按 sortBy 选择不同 ORDER BY，更新 `server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsQueryService.java`，依赖任务 1.1
- [√] 1.3 扩展集成测试覆盖 pv/uv 两种排序，更新 `server/api-app/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`，依赖任务 1.2

## 2. admin-ui（Top 排序切换 + 图表按需加载）
- [√] 2.1 统计页 Top 表格增加“按 PV/UV”切换，并带上 `sortBy` 请求参数，更新 `web/src/views/StatsView.vue`，验证 why.md#requirement-ui-top-links-sort / why.md#scenario-ui-switch-pv-uv
- [√] 2.2 图表组件拆分为异步组件加载（defineAsyncComponent），更新 `web/src/views/StatsView.vue`，验证 why.md#requirement-ui-chart-lazyload / why.md#scenario-ui-load-chart-on-demand
- [√] 2.3 ECharts 模块化引入，减少图表 chunk 体积，更新 `web/src/components/LineChart.vue`

## 3. Security Check
- [√] 3.1 校验 sortBy 白名单与参数校验逻辑；确认接口仍禁止 OPENAPI；确认不引入 SQL 注入风险

## 4. Documentation Update（SSOT）
- [√] 4.1 更新 API 手册：补充 `/api/v1/stats/top-links` 的 `sortBy` 参数，更新 `.helloagents/wiki/api.md`
- [√] 4.2 更新变更日志，更新 `.helloagents/CHANGELOG.md`

## 5. Testing
- [√] 5.1 后端：在 `server/` 执行 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test`
- [√] 5.2 前端：在 `web/` 执行 `npm run build`，确认 chunk 拆分与构建成功

# Change Proposal: Top 报表支持按 UV 排序 + 图表更细粒度按需加载

## Requirement Background
当前统计看板已提供 Top 链接报表与租户趋势图，但仍有两个可用性/性能体验缺口：

1) **Top 报表仅按 PV 排序**：运营/增长场景经常需要按 UV（触达人数）查看排行，避免 PV 被少数用户重复访问放大。
2) **图表依赖体积较大**：ECharts 会显著增大统计页构建 chunk；希望更细粒度按需加载，降低非统计路径的加载成本，并让统计页加载更可控。

## Change Content
1. Top 链接报表 API 增加可选参数 `sortBy=pv|uv`（默认 pv），支持按 UV 降序排序
2. 管理后台统计页增加“按 PV / 按 UV”切换按钮，驱动 Top 报表查询
3. 图表组件改为更细粒度的按需加载（异步组件 + ECharts 模块化引入），减少 StatsView 主 chunk 体积
4. 同步更新 SSOT：API 文档与变更日志；补充后端集成测试覆盖 UV 排序

## Impact Scope
- **Modules:** analytics, admin-ui
- **Files (expected):**
  - server/api-app/src/main/java/com/linkforge/analytics/web/StatsController.java
  - server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsQueryService.java
  - server/api-app/src/test/java/com/linkforge/LinkForgeIntegrationTest.java
  - web/src/views/StatsView.vue
  - web/src/components/LineChart.vue
  - .helloagents/wiki/api.md
  - .helloagents/CHANGELOG.md

## Core Scenarios

### Requirement: analytics-top-links-sort
**Module:** analytics
Top 链接报表支持按 UV 排序，且保持租户隔离与向后兼容。

#### Scenario: top-links-sortBy-uv
前置条件：JWT 登录用户（非 OPENAPI）
- 请求 `/api/v1/stats/top-links?from=...&to=...&sortBy=uv` 返回按 UV 降序的 Top 链接
- 默认 `sortBy=pv` 与现有行为一致

---

### Requirement: ui-top-links-sort
**Module:** admin-ui
统计页提供 Top 报表排序切换。

#### Scenario: ui-switch-pv-uv
前置条件：已登录
- 点击“按 PV / 按 UV”切换后，Top 表格按对应指标重新加载

---

### Requirement: ui-chart-lazyload
**Module:** admin-ui
图表能力更细粒度按需加载，降低主 bundle 体积。

#### Scenario: ui-load-chart-on-demand
前置条件：进入统计页
- StatsView 先渲染主体（筛选、Top 表格、单链明细）
- 图表组件与其依赖按需异步加载，避免将完整图表库打入 StatsView 主 chunk

## Risk Assessment
- **Risk:** API 新增参数可能导致调用方误传值
  - **Mitigation:** 严格白名单校验，仅接受 pv/uv；默认 pv；错误提示明确
- **Risk:** 异步加载图表可能带来首次渲染闪烁
  - **Mitigation:** 提供简单 loading 占位；只在有数据时加载图表

# Change Proposal: 管理后台标签管理 + 统计趋势图/Top 报表 + Redirect 压测

## Requirement Background
当前 LinkForge 已具备短链创建/跳转与基础统计能力，但管理后台仍偏 MVP：
1) 标签能力后端已存在（/api/v1/tags、短链 tags 字段），前端缺少可视化的标签管理入口；
2) 统计页目前仅展示“单短链-今日表格”，缺少 7/30 天趋势图与 Top 链接报表；
3) Redirect 核心链路缺少可复用的压测脚本与结果记录，不利于容量评估与回归验证。

本变更补齐管理后台的数据可视化与可运维性，使其更贴近“可用的短链系统后台”。

## Change Content
1. 新增“标签管理”页面：租户内标签列表与创建
2. 增强“统计看板”页面：支持近 7/30 天 PV/UV 趋势图（租户概览）与 Top 链接报表
3. 后端补充 Top 链接统计 API（按时间范围聚合）
4. 提供 Redirect 压测脚本（k6）与一次基线压测结果记录（本地环境）

## Impact Scope
- **Modules:**
  - admin-ui（Vue3 管理后台）
  - analytics（统计查询 API）
  - redirect（压测覆盖与文档记录）
- **Files (expected):**
  - web/src/views/StatsView.vue
  - web/src/views/TagsView.vue (new)
  - web/src/router/index.ts
  - web/package.json
  - server/src/main/java/com/linkforge/analytics/web/StatsController.java
  - server/src/main/java/com/linkforge/analytics/service/AnalyticsQueryService.java
  - server/src/test/java/com/linkforge/LinkForgeIntegrationTest.java
  - bench/redirect/* (new)
  - .helloagents/wiki/api.md（补充 API）
  - .helloagents/wiki/modules/admin-ui.md / analytics.md / redirect.md（同步文档）
- **APIs:**
  - + [GET] /api/v1/stats/top-links（新增）
- **Data:**
  - 不新增业务表；可选补充统计表索引以支撑 Top 聚合查询

## Core Scenarios

### Requirement: ui-tags-management
**Module:** admin-ui
提供标签管理入口与基础操作，便于租户维护统一标签集合。

#### Scenario: ui-tags-list-create
前置条件：已登录（JWT）且属于某租户
- 进入“标签管理”页可看到当前租户标签列表
- 可创建新标签；创建成功后列表更新
- 重名标签提示友好错误（复用后端校验）

---

### Requirement: ui-stats-dashboard
**Module:** admin-ui
提供统计看板的趋势与排行能力，覆盖“看趋势 + 看 Top”两类常见管理诉求。

#### Scenario: ui-tenant-trend-7-30
前置条件：已登录（JWT）
- 支持快速选择近 7 天/30 天
- 展示租户维度 PV/UV 折线趋势图

#### Scenario: ui-top-links
前置条件：已登录（JWT）
- 展示 Top 链接列表（默认按 PV 排序），含短码/原始链接/PV/UV
- 点击可快速跳转到短链或复制

---

### Requirement: analytics-top-links-api
**Module:** analytics
提供按时间范围聚合的 Top 链接报表查询接口。

#### Scenario: top-links-range
前置条件：请求为 JWT（非 OPENAPI）
- 输入 from/to/limit（可选）返回 Top 链接聚合结果
- 仅返回当前租户数据（tenant isolation）

---

### Requirement: redirect-benchmark
**Module:** redirect
提供可复用的 Redirect 压测脚本与结果记录。

#### Scenario: k6-baseline-1000qps
前置条件：本地 docker-compose 启动服务，存在可用短码
- 使用 k6 在固定 VU/迭代条件下压测 /r/{code}
- 记录关键指标（吞吐、p95、错误率）与环境信息

## Risk Assessment
- **Risk:** 引入图表库会增加前端依赖与包体积
  - **Mitigation:** 选择单一图表库（ECharts），封装轻量组件，避免多库混用；构建产物做一次体积对比记录（可选）
- **Risk:** Top 聚合 SQL 在大范围数据下可能较重
  - **Mitigation:** 限制 limit 默认值与上限；必要时增加索引；后续可引入按天预聚合/物化视图策略
- **Risk:** 压测可能误打到非本地环境造成风险
  - **Mitigation:** 脚本默认 target=localhost，并在 README 强制提示“禁止对生产执行”

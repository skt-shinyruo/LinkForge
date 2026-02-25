# analytics

## Purpose
提供跳转统计的采集、聚合落库与报表查询能力（PV/UV 等），并对跳转链路做“写放大控制”（Redirect Edge 只写 Redis，API Service 负责落库与查询）。

## Module Overview
- **Responsibility:** Redirect Edge 侧采集写入（Redis：PV/UV/维度计数/事件流）、统计结构设计（PV/UV/active-set/维度聚合/访问明细）、API Service 定时落库（flush job / dim flush / event ingest / retention）、报表查询 API
- **Status:** 🚧In Development
- **Last Updated:** 2026-02-25
- **Code Packages:**
  - shared（契约/keys）：`com.linkforge.analytics.*`（示例：`AnalyticsKeys`）
  - Redirect Edge（采集写 Redis）：`com.linkforge.edge.analytics.*`
  - API Service（落库/查询/作业）：`com.linkforge.api.analytics.*`

## Specifications

### Requirement: analytics-collect（统计采集）
**Module:** analytics
Redirect Edge 链路做轻量写入，避免 MySQL 明细写放大。

#### Scenario: collect-pv（PV）
- 每次跳转对 Redis PV 计数做 `INCR`

#### Scenario: collect-uv（UV）
- 使用 Redis HyperLogLog 进行 UV 近似去重（可接受误差，换取高性能）

#### Scenario: collect-active-set（活跃索引）
- 每次跳转将 `{tenantId}:{linkId}` 写入当日活跃集合 `stats:active:{yyyyMMdd}`
- 目的：让 flush job 只遍历活跃集合，而不是全量扫描 keyspace

#### Scenario: collect-dimensions（维度计数，MVP）
- 可选：在 Edge 侧按配置启用维度计数写入 Redis（Hash）
- 维度值必须先归一化（referer domain / language / UA family / device type / UTM 白名单等），避免高基数爆炸

#### Scenario: collect-visit-events（访问明细事件，MVP）
- 可选：Edge 将访问事件写入 Redis Stream（采样 + MAXLEN ~ 截断）
- 事件仅用于“最近访问明细”排障与近期分析；长期明细建议走 OLAP
- ⚠️ 已知风险（best-effort）：Stream 在写入侧做近似裁剪（`MAXLEN ~`），当 ingest 落后或暂停时可能截断未消费数据，导致“明细丢失但跳转主链路不受影响”

#### Redis Key 约定（实现对齐）
- `stats:pv:{tenantId}:{linkId}:{yyyyMMdd}`：PV 计数
- `stats:uv:{tenantId}:{linkId}:{yyyyMMdd}`：UV HLL
- `stats:active:{yyyyMMdd}`：当日活跃链接集合（成员：`{tenantId}:{linkId}`）
- `stats:dim:pv:{tenantId}:{linkId}:{yyyyMMdd}:{dimType}`：维度 PV（Hash：field=dimValue，value=pvCount）
- `stats:visit:events`：访问明细事件 Stream（field map）

---

### Requirement: analytics-flush（聚合落库）
**Module:** analytics
定时任务将 Redis 计数与去重结果聚合写入 MySQL。

#### Scenario: flush-daily（按天聚合）
- 写入/更新 `link_stats_daily`
- 支持幂等与重试
- **实现约束：** flush 任务以 active-set 为输入，增量批量化处理，避免 `SCAN stats:pv:*`
- **多实例治理：** flush 作业通过 ShedLock（Redis）互斥，水平扩容时确保同一时刻仅 1 个实例执行，避免重复扫描/重复写库放大
- **可追赶回补：** 支持按回补窗口（最近 N 天，包含今天）追赶 flush（配置：`app.analytics.flush-backfill-days`，默认 7）
- **TTL 与数据窗口：** Redis 侧 `stats:*` key 会设置统一 TTL（配置：`app.analytics.redis-key-ttl-days`）。当 flush 停摆超过 TTL 时，Redis 侧统计数据可能已过期，无法再回补（best-effort 语义）。

---

### Requirement: analytics-dimensions（可运营维度聚合）
**Module:** analytics
提供来源/语言/设备/渠道等维度的按天聚合与查询能力。

#### Scenario: flush-dim-daily（按天维度聚合）
- API Service 定时任务从 Redis 读取维度 PV（Hash）并写入 `link_stats_dim_daily`
- 以 active-set 为入口增量处理，避免全量扫描
- UV 维度可后续扩展（MVP 可先填 0）
- **多实例治理：** dim flush 作业同样使用 ShedLock（Redis）互斥，避免重复写库

---

### Requirement: analytics-visit-events（访问明细事件）
**Module:** analytics
提供“最近访问明细”的落库与查询能力，服务排障与异常访问识别。

#### Scenario: ingest-events（事件落库）
- API Service 通过 consumer group 消费 Redis Stream，并批量写入 `link_visit_events`
- 以 `(tenant_id, request_id)` 做幂等去重（避免重复消费导致重复写入）
- 时间基准：`occurred_at` 以 UTC 语义写入/查询（MySQL `DATETIME` 不带时区），应用侧统一使用 `LocalDateTime(UTC)` 传参，避免 `Timestamp` 隐式时区转换导致查询范围不命中
- **consumer 稳定性：** 支持配置稳定 consumerName（`app.analytics.events.consumer-name`），未配置时派生稳定默认值（HOSTNAME + workerId/datacenterId）
- **pending 治理：** 支持定期接管 pending（`XPENDING + XCLAIM`），处理 consumer 漂移/下线导致的积压，避免长期“不落库”

#### Scenario: retention-cleanup（留存清理）
- 按配置 `retentionDays` 定期清理历史明细，避免表无限膨胀
- **多实例治理：** retention cleanup 使用 ShedLock（Redis）互斥，避免重复 delete 放大与锁争用

---

### Requirement: analytics-report（报表查询）
**Module:** analytics
管理后台可查询短链按天统计与租户概览。

#### Scenario: link-daily-stats（单链路按天）
- 按时间范围返回 PV/UV

#### Scenario: tenant-overview（租户概览）
- 返回近 7/30 天趋势与 TOP 链接（可选）

#### Scenario: top-links（Top 链接报表）
- 按时间范围聚合返回 Top 链接列表（支持 `sortBy=pv|uv`，默认 pv）

## API Interfaces

### [GET] /api/v1/stats/links/{id}/daily
**Description:** 单短链按天统计

### [GET] /api/v1/stats/overview
**Description:** 租户统计概览

### [GET] /api/v1/stats/top-links
**Description:** Top 链接报表

### [GET] /api/v1/stats/links/{id}/dimensions
**Description:** 单短链维度分布（TopN）

### [GET] /api/v1/stats/links/{id}/events
**Description:** 单短链最近访问明细（短期留存）

## Data Models

### link_stats_daily
见 `.helloagents/wiki/data.md`。

### link_stats_dim_daily / link_visit_events
见 `.helloagents/wiki/data.md`。

## Dependencies
- redirect-edge（采集入口）
- api-service（flush job + 报表查询 API）
- shortlink（短链维度）
- shared（Redis key 契约 + 平台基础能力：Redis/MySQL、调度、幂等、错误处理）

## Known Issues / TODO（已记录，待后续演进）
- active-set “增量”仍是“每轮扫描全量 active-set”，规模上来会变重；下一步建议将活跃索引改为可消费队列/Stream，或 flush 后按桶 SREM/分片集合
- Stream trim 在写入侧进行，ingest 落后可能截断未消费数据；需明确“可丢事件”的产品语义并配套告警，或改为 ingest 侧按 lag 控制裁剪

## Change History
- [202602182227_shortlink_system_mvp](../../history/2026-02/202602182227_shortlink_system_mvp/) - MVP：PV/UV 采集、定时聚合落库、统计 API
- [202602191324_ui_tags_charts_top_bench](../../history/2026-02/202602191324_ui_tags_charts_top_bench/) - 新增 Top 链接报表 API（按时间范围聚合）
- [202602191429_top_uv_sort_chart_lazyload](../../history/2026-02/202602191429_top_uv_sort_chart_lazyload/) - Top 报表排序扩展：支持按 UV 排序（`sortBy=uv`）
- [202602191426_edge_api_split_refactor](../../history/2026-02/202602191426_edge_api_split_refactor/) - 统计链路重构：active-set 增量驱动 flush，Edge 只写 Redis，API 负责落库与查询
- [202602201217_analytics_visit_events_dims](../../history/2026-02/202602201217_analytics_visit_events_dims/) - 统计增强：维度按天聚合 + 访问明细事件（短期留存）+ 新增维度/明细查询 API

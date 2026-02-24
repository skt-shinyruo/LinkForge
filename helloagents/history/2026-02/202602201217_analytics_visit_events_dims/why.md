# 变更提案：P1 统计维度与访问明细增强（A+B）

## 需求背景

当前 Redirect 侧统计链路以“最小可用”为目标：

1. `VisitInfo` 只有 `ip` 与 `userAgent`，缺少 `referer`、`accept-language`、UTM/渠道参数、设备/浏览器解析与地理信息等运营维度。
2. 数据层只有 `link_stats_daily`（按天聚合表），缺少访问明细/事件表，导致：
   - 难以做来源分布/渠道效果/设备浏览器占比/地域热力等运营分析；
   - 难以做异常访问识别与实时排查（只能看聚合 PV/UV，缺少样本与上下文）。

目标是在不显著影响跳转主链路性能的前提下，引入“维度聚合 + 访问明细（短期留存）”的双层统计能力，支撑运营与排障。

## 产品分析

### 目标用户与场景
- **运营/增长/市场：**
  - 投放渠道效果评估：按 `utm_source/utm_medium/utm_campaign` 看 PV/UV、趋势与占比；
  - 来源分布：按 `referer_domain` 识别主要来源站点与异常来源；
  - 设备/浏览器占比：支持落地页适配与投放策略调整。
- **研发/运维/安全：**
  - 异常访问排查：查看“最近访问明细”，按时间窗口定位异常峰值、UA 特征、来源与语言；
  - 反作弊/风控联动：为 Edge 风控策略提供更可解释的证据维度（不在本次强依赖，但保留接口与字段）。

### 价值主张与成功指标
- **价值主张：** 在保持 Edge 跳转链路轻量的前提下，提供可运营、可排障的统计维度与明细能力。
- **成功指标（可验收）：**
  1. 可查询 Top `referer_domain`、Top UTM 渠道分布（时间范围、单链接/租户维度）。
  2. 可查询“最近访问明细”（支持按 linkId / 时间范围过滤、分页、排序）。
  3. 主链路可用性不受影响：统计写入失败必须 fail-safe（降级为日志），并支持采样/限长/短期留存。

### 人文关怀（隐私与合规）
- 最小化采集：默认仅采集必要 header 与营销参数（UTM 等）；避免采集敏感 query（token/手机号/邮箱等）。
- 保护策略：
  - IP 建议默认哈希化/脱敏化存储（明细表可配置是否存明文/仅存 hash）。
  - 明细事件设置短期留存（如 7/14 天）并定期清理。
  - 对明细查询接口做权限控制与审计（仅管理后台可访问）。

## 变更内容
1. **采集增强（A）：** 扩展 `VisitInfo`（referer、accept-language、渠道参数等），并引入维度归一化（域名提取、语言主标签、UA 解析结果等）。
2. **明细补齐（B）：** 新增“访问明细事件”写入与落库链路（Edge → Redis Stream → API Service 消费落库 → MySQL 明细表），并提供查询 API。
3. **维度聚合（A 的落地形态）：** 新增常用维度的聚合统计（如 referer_domain / utm_source / browser_family / os_family / language / country），用于运营报表快速查询（避免对明细表做全量 group-by）。
4. **治理与可控性：** 提供采样率、最大长度、留存天数、维度白名单等开关；统计链路失败不影响跳转。

## 影响范围
- **模块：**
  - `redirect-edge`：采集增强、写入维度/明细事件（best-effort）。
  - `analytics`：新增维度/事件模型、Redis key 约定、flush/ingest 作业、查询接口。
  - `api-service`：新增明细落库表、维度聚合表、查询 API。
  - `platform/shared`：`VisitInfo` 扩展、维度归一化工具。
- **文件（预估）：**
  - `server/shared/src/main/java/com/linkforge/platform/web/VisitInfo.java`
  - `server/shared/src/main/java/com/linkforge/analytics/service/*`
  - `server/edge-app/src/main/java/com/linkforge/redirect/web/RedirectController.java`
  - `server/edge-app/src/main/java/com/linkforge/edge/web/EdgeRiskControlFilter.java`
  - `server/api-app/src/main/java/com/linkforge/analytics/service/*`
  - `server/api-app/src/main/resources/db/migration/V4__add_visit_events_and_dims.sql`（新）
- **API（新增/扩展）：**
  - 访问明细查询 API
  - 维度分布查询 API（单链接/租户）
- **数据：**
  - 新增明细表（短期留存）
  - 新增维度聚合表（按天/按维度）

## 核心场景

### Requirement: analytics-dimensions（可运营维度）
**Module:** analytics
提供运营常用维度的采集与查询能力。

#### Scenario: dim-distribution（维度分布）
给定 tenantId/linkId + 日期范围，返回指定维度（referer_domain/utm_source/browser_family/...）的 TopN 分布与占比。
- 返回 TopN + 其他（可选），用于前端图表展示

---

### Requirement: analytics-visit-events（访问明细事件）
**Module:** analytics
提供“最近访问明细”的采集、落库与查询能力，用于排障与异常访问识别。

#### Scenario: event-ingest（事件落库）
Edge 将事件写入 Redis（可采样/限长），API Service 异步消费并批量落库到 MySQL 明细表。
- 写入失败不影响跳转
- 支持留存与清理

#### Scenario: event-query（事件查询）
管理后台可查询最近访问明细（按链接/时间范围过滤，分页排序）。
- 返回必要字段（时间、referer_domain、utm、ua_family、language、ip_hash/掩码等）

## 风险评估
- **风险：高基数维度导致写放大/存储膨胀。**
  - **缓解：** 维度归一化（域名/主标签/UA family），对 UTM 做长度限制与白名单；对明细事件启用采样与短期留存。
- **风险：隐私与合规（IP/UA/referer 属于个人数据或可识别信息）。**
  - **缓解：** 默认哈希化/脱敏化存储；最小化采集；权限控制；留存策略。
- **风险：主链路性能回退。**
  - **缓解：** Edge 写入全程 best-effort（try/catch）；Redis Stream/计数写入失败 fail-open；批量化消费落库。


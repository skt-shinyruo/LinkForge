# api

## Purpose
提供管理后台/自助创建/OpenAPI 的后端能力（`/api/v1/**`），并承担统计落库（flush/dim flush）、访问明细落库（event ingest/retention）与报表查询能力。

## Module Overview
- **Responsibility:** IAM（注册/登录/用户/角色）、短链 CRUD、标签、导入导出、OpenAPI（API Key）、统计查询、统计落库（定时 flush / dim flush）、访问明细落库（Redis Stream ingest + retention）
- **Deployment:** 独立可部署服务（API Service，默认端口 8080）
- **Code Root Package:** `com.linkforge.api.*`（应用编排与实现）；依赖 shared 的 `com.linkforge.platform.*` / `com.linkforge.redirect.*` / `com.linkforge.analytics.*`（契约/可复用能力）
- **Status:** 🚧In Development
- **Last Updated:** 2026-02-25

## Specifications

### Requirement: api-routing（路由边界）
**Module:** api
- 仅负责 `/api/v1/**`（含 `/api/v1/open/**`）
- 不暴露 `/r/**`（跳转链路属于 Redirect Edge Service）

### Requirement: api-layering-boundary（分层边界）
**Module:** api
- Controller 仅做协议适配（HTTP/DTO/分页参数等）
- 业务逻辑必须落在 Service/UseCase 层
- Repository 仅在 Service 层可见（配合架构测试/代码审计防回退）

### Requirement: api-tenant-guard（多租户护栏）
**Module:** api
- 以认证主体（JWT / API Key）解析出的 tenantId 为准
- Service 层必须校验“入参 tenantId 与当前主体 tenantId 一致”，避免越权与误用

### Requirement: analytics-flush（统计落库）
**Module:** api
- Edge 侧只写 Redis 计数/去重结构
- API Service 定时任务增量 flush 落库（active-set 驱动），避免 Redis keyspace 全量扫描
- 多实例部署：flush/dim flush/retention 等定时作业通过 ShedLock（Redis）互斥，避免重复执行放大

### Requirement: analytics-dimensions（维度聚合落库）
**Module:** api
- Edge 侧可选写入维度计数（Redis Hash）
- API Service 定时任务将维度计数聚合写入 `link_stats_dim_daily`

### Requirement: analytics-visit-events（访问明细落库）
**Module:** api
- Edge 侧可选写入访问明细事件（Redis Stream）
- API Service 消费 Stream 并批量落库到 `link_visit_events`，同时按留存策略定期清理

## API Interfaces

API 详情见：
- `.helloagents/wiki/api.md`

## Data Models

主数据与统计表结构见：
- `.helloagents/wiki/data.md`

## Dependencies
- platform（统一响应/错误码/RequestId/配置约定）
- MySQL（写主数据、写统计聚合表）
- Redis（缓存、统计结构、active-set）

## Change History
- [202602182227_shortlink_system_mvp](../../history/2026-02/202602182227_shortlink_system_mvp/) - MVP：单体后端提供 `/api/v1/**` 与 `/r/**`
- [202602191426_edge_api_split_refactor](../../history/2026-02/202602191426_edge_api_split_refactor/) - 拆分为 API Service：负责 `/api/v1/**` 与统计落库/查询
- [202602201026_redirect_experience_control](../../history/2026-02/202602201026_redirect_experience_control/) - 短链 API 扩展跳转策略字段（按链接状态码/预览页/query 透传/不可用落地页）
- [202602201217_analytics_visit_events_dims](../../history/2026-02/202602201217_analytics_visit_events_dims/) - 统计增强：维度聚合 + 访问明细事件（Redis Stream ingest + retention）+ 新增统计查询 API
- [202602250305_m0_id_edge_cache_hardening](../../history/2026-02/202602250305_m0_id_edge_cache_hardening/) - M0 护栏：prod/strict 禁止默认 Snowflake 1/1（多实例安全）

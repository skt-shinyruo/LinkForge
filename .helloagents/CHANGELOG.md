# 变更日志（Changelog）

本文件记录 LinkForge 项目所有重要变更。
格式参考 Keep a Changelog 与语义化版本（Semantic Versioning）。

## [Unreleased]
- 管理后台：新增标签管理页（标签列表/创建）
- 统计看板：新增近 7/30 天趋势图（租户概览）与 Top 链接报表
- 统计 API：新增 `/api/v1/stats/top-links`（按时间范围聚合）
- 统计看板：Top 链接报表支持按 UV 排序（`sortBy=uv`）
- 前端性能：统计图表更细粒度按需加载（异步组件 + ECharts 模块化引入）
- 前端性能：统计趋势图拆分多卡片并支持按需展示（点击“显示图表”后再加载图表 chunk）
- Redirect：新增 k6 压测脚本与本地基线结果记录（`bench/redirect/`）
- 部署：`deploy/docker-compose.yml` 增加 MySQL/Redis healthcheck + server 重启策略，并将 Redis 暴露端口调整为 6380（避免 6379 冲突）
- 后端架构：拆分为 API Service（`/api/v1/**`）与 Redirect Edge Service（`/r/**`），并抽取 `server/shared` 作为跨服务 SSOT
- 后端架构：落地 package ownership（`com.linkforge.api.*` / `com.linkforge.edge.*` / shared 契约），消除跨模块 split package；CI 增加 split package 检测（`server/tools/check_split_packages.py`）（package-ownership-refactor#D001）
- 构建基线：后端 Java 版本基线调整为 17（与当前开发/测试环境对齐；目标仍可升级到 21）
- 统计链路：Redirect Edge 轻量写 Redis；API Service 的 flush job 改为 active-set 增量驱动（避免全量 `SCAN stats:pv:*`）
- 统计增强：新增维度按天聚合（referer/language/ua/os/device/utm_*）与访问明细事件（Redis Stream → MySQL 短期留存），并新增统计查询接口 `/api/v1/stats/links/{id}/dimensions` 与 `/api/v1/stats/links/{id}/events`
- 调度治理：API Service 引入 ShedLock（Redis）对 flush/dim flush/retention 等定时作业做多实例互斥，避免水平扩容导致重复跑任务/重复写库放大
- 统计落库：flush 支持可配置回补窗口（`app.analytics.flush-backfill-days`，默认 7），降低部署中断导致“历史缺天”的风险
- 访问明细：事件 ingest consumer 稳定化（可配置 `app.analytics.events.consumer-name`）并支持 pending reclaim（`XPENDING + XCLAIM`），避免 consumer 漂移/下线导致长期积压
- 修复：访问明细事件 `occurred_at` 写入/查询统一使用 UTC 语义（避免 MySQL `DATETIME` + `Timestamp` 时区偏移导致 `/events` 查询为空）
- 安全与一致性：统一错误响应（`ApiResponse + requestId`）、缺失认证主体返回 401；Service 层引入 tenant guard 防越权
- 配置与部署：新增启动期关键配置校验（strict-config/prod）；CORS 收敛白名单并支持 Cookie 会话的 credentials 校验；Snowflake workerId/datacenterId 配置化
- 配置加固：在 `prod` 或 `app.strict-config=true` 下禁止 Snowflake 使用默认 `workerId/datacenterId=1/1`，避免多实例部署时发生 ID 冲突（主键冲突/数据错写）
- 前端会话：统一 401 处理（自动登出并跳登录）；token 存储默认收敛到 `sessionStorage`，并提供可选 Cookie 模式
- Redirect Edge：新增可信代理链校验（安全取 IP），并提供可配置的 /r/** 风控能力（IP 黑白名单、Redis 限流、bot 降频）
- 部署体验：`deploy/docker-compose.yml` 默认配置 `EDGE_TRUSTED_PROXIES`，并在 README/.env.example 补充说明，避免 docker+nginx 默认部署下 IP/UV 统计失真
- Redirect：跳转体验与跳转行为可控性增强（404/410 HTML、可选落地页、预览页确认后跳转、按链接 301/302、Query 透传策略 OFF/ALLOWLIST/ALL）
- Redirect：Edge `/r/**` 增加短码格式快速拒绝 + 短码不存在负缓存（可配置 `app.redirect.not-found-cache-ttl-seconds`），降低缓存穿透导致的 MySQL 回源放大风险
- 管理后台：短链创建/编辑表单补齐跳转策略配置项（状态码、预览页、Query 透传与 allowlist、不可用落地页）
- 生命周期治理：短链归档/恢复/删除；用户启用/禁用/重置密码；API Key 启用/禁用/轮换；管理后台对齐（customCode/expiresAt/tags/归档筛选）

## [0.1.0] - 2026-02-18

### Added
- 初始化 helloagents 知识库（SSOT）：`wiki/overview.md`、`wiki/arch.md`、`wiki/api.md`、`wiki/data.md`、`wiki/modules/*`
- 创建并归档短链系统 MVP 方案包：`history/2026-02/202602182227_shortlink_system_mvp/`（why/how/task）
- 后端服务 `server/`：Spring Boot（Java21 + Maven），实现 IAM/ShortLink/Redirect/Analytics/OpenAPI
- 前端管理后台 `web/`：Vue3 + Vite + TS（登录、短链管理、统计基础页、导入导出基础交互）
- 部署 `deploy/`：Docker Compose（MySQL + Redis + server + web）与环境示例
- CI：GitHub Actions（后端测试 + 前端构建）
- 集成测试：Testcontainers（MySQL/Redis）覆盖注册登录、创建短链、跳转、统计与 OpenAPI

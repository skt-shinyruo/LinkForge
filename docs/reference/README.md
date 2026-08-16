# LinkForge 参考文档

本目录集中存放项目当前参考文档，适合用来做架构阅读、需求评审、故障定位和二次开发前的代码导览。

## 架构与总览

- [架构总览](architecture.md)：模块化单体边界、分层规则、部署拓扑和关键技术约束。
- [核心逻辑地图](core-logic.md)：核心链路、上下文职责和各权威专题入口。
- [Foundation 与关键配置](foundation-and-configuration.md)：ID、请求 ID、认证主体、事务后动作、事件存储、启动门禁和默认配置。
- [发布契约](published-contracts.md)：共享 API、跨上下文端口、事件、审批 payload、Redis key 与兼容性规则。
- [核心逻辑文档规范](documentation-standards.md)：注释范围、事实源分层和评审清单。
- [测试与质量门禁](testing.md)：快速/集成测试、共享 Testcontainers 隔离协议、覆盖率和 Compose smoke。

## 链路索引

- [认证与会话链路](auth-and-session.md)：注册、登录、JWT/Cookie、CSRF、`/me` 和安全过滤链。
- [平台控制面链路](platform-control-plane.md)：应用、域名、授权、策略、额度和跨上下文端口。
- [短链管理链路](shortlink-management.md)：创建、查询、更新、归档、恢复、删除、标签、CSV 导入导出。
- [OpenAPI 与 API Key 链路](openapi-api-key.md)：API Key 生命周期、认证过滤器、应用绑定和 OpenAPI 发链。
- [跳转与缓存链路](redirect-and-cache.md)：`/r/{code}`、风控、Redis 正/负缓存、预览页、query 转发和点击额度。
- [统计采集与报表链路](analytics-ingestion-and-reporting.md)：访问事件、Redis Stream、PV/UV 聚合、明细入库、报表和导出审批入口。
- [审批与审计链路](governance-approval-audit.md)：敏感操作申请、审批矩阵、执行器、目标地址变更和审计日志。
- [前端控制台链路](frontend-console.md)：Vue 路由守卫、auth store、HTTP service、页面 composables 和主要页面流转。
- [跨上下文事件与一致性链路](cross-context-events.md)：集成事件、事务后缓存驱逐、Shortlink 读契约、Analytics 目录投影。

## 阅读建议

如果只关心一次短链访问，从 [跳转与缓存链路](redirect-and-cache.md) 开始，再读 [统计采集与报表链路](analytics-ingestion-and-reporting.md)。

如果要改发链或控制台能力，从 [平台控制面链路](platform-control-plane.md)、[短链管理链路](shortlink-management.md)、[前端控制台链路](frontend-console.md) 依次读。

如果要排查数据一致性问题，先读 [跨上下文事件与一致性链路](cross-context-events.md)，再回到具体业务链路。

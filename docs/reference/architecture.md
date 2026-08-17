# LinkForge 架构

LinkForge 是一个模块化单体：后端由 Maven Reactor 组织，前端是独立的 Vue 应用。当前架构优先保证单一部署运行时内部的模块边界清晰，而不是把系统拆成独立部署的服务。

## 后端

- `server/foundation`：提供共享配置、ID、事务、Web 和启动支持；应用组合根直接使用 Spring 标准组件扫描。
- `server/contracts/*`：沉淀单体内部仍值得维护的共享语言。`contract-api` 保存通用 API 契约，`contract-shortlink` 承载短链集成事件载荷，`contract-redirect` 承载跳转读取和缓存契约，`contract-analytics` 承载统计契约，`contract-platform` 承载应用、域名、授权相关词汇，`contract-governance` 承载稳定的审批编排语言。
- `server/accounts`：负责账号、租户、认证和 API Key 管理，按 domain/application/infrastructure/interfaces Maven 模块组织。
- `server/platform`：负责租户应用、域名、配额和策略等控制平面能力。它建模 `tenant -> application -> domain` 关系，并暴露自助控制台使用的租户和平台管理 HTTP 接口。
- `server/governance`：负责审批和审计管理，当前支持链接目标变更及其审计 API。
- `server/shortlink`：短链管理写侧，拥有持久化状态并向下游投递集成事件。
- `server/redirect`：基于缓存的跳转服务；缓存未命中时回源 Shortlink，正确性不依赖独立 redirect 投影。
- `server/analytics`：负责 Redis PV/UV 聚合、V2 marker flush、报表和短链目录读模型。
- `server/app`：Spring Boot 可执行组合根，使用 Spring 标准组件扫描和显式 MyBatis mapper 扫描，不维护一层层空的 runtime glue。
- `server/integration-tests`：基于 Testcontainers 验证跨模块行为。

## DDD 上下文映射

后端限界上下文是单一部署单体内部的代码所有权边界，不是独立部署的服务。

### Accounts

拥有租户、用户、角色、API Key、认证状态和账号状态检查。Accounts 可以发布认证和账号状态能力，但持久化细节、Token 解析内部实现、角色存储都保持为上下文私有。

### Platform

拥有租户应用、域名、配额和应用策略。Platform 通过 `contract-platform` 发布应用范围、域名主机名查询和配额视图。

### Shortlink

拥有持久化链接状态、链接生命周期、目标规则、标签、修订记录和短链变更事件。`ShortLink` 是战术 DDD 加固中的第一个聚合根。其他上下文只能通过 `contract-shortlink` 读取跳转元数据、所有权和摘要信息。

### Redirect

拥有流量平面的跳转解析、Redis 缓存行为、预览和未找到响应，以及轻量访问事件追加。Redirect 不拥有链接事实；缓存未命中时使用 shortlink 发布的读取契约。

### Analytics

拥有 Redis PV/UV 聚合、日表 flush、统计读取和短链目录。Analytics 读模型保持私有；跨上下文链接补全使用 shortlink 发布的契约。

### Governance

拥有审批请求生命周期、审批决策、敏感操作记录和审计日志。需要跨上下文审批编排时，Governance 通过发布契约暴露相关能力。

## 战术 DDD 规则

- `domain` 拥有聚合行为、不变量、值对象和领域服务；集成事件由应用层在持久化成功后追加。
- `application` 拥有用例编排、事务、仓储端口、授权输入处理和集成事件发布。应用服务可以接收显式的参与者和输入对象，但不能读取隐藏的运行时安全上下文。应用代码中的公开请求和结果模型应当是顶层类型，不应把 DTO、Result、Request 容器嵌套在服务类内部。
- `interfaces` 拥有请求校验、身份主体提取和必要的 HTTP 映射。应用读模型与响应形状一致时直接序列化；只有字段变换、脱敏或协议差异存在时才维护独立传输 DTO。
- `infrastructure` 拥有 MyBatis、Redis、调度器和持久化映射。基础设施适配器不能依赖 `AuthContext`、`TenantGuard` 等运行时安全辅助类；租户范围必须通过应用端口显式传入。
- 组合根直接扫描各上下文的 Spring 组件和 MyBatis mapper；不再保留只转发配置的 runtime glue 类。
- `contracts/*` 拥有限界上下文之间共享的发布语言。契约模块表达稳定业务语义，不能复用 foundation 的安全或上下文参与者对象作为发布 API 语言。
- 限界上下文不能导入其他上下文的 `domain`、`application`、`infrastructure`、`interfaces` 或 `runtime` 包。

## 跳转正确性路径

跳转读取按以下顺序执行：

1. 使用 `host + code` 查询 Redis 跳转缓存（`LinkCachePort`）
2. 使用 `host + code` 查询权威短链读取 API
3. 对真实未命中写入负缓存

短链变更在业务事务内写缓存失效 outbox，事务提交后再通过 `PostCommitHookPort` 尝试快速驱逐；worker 会对 outbox 重试。事务回滚既不会执行快速路径，也不会留下 outbox。系统没有第二条 redirect 投影正确性通道，Redis 异常或缓存 miss 时始终同步调用 `ShortLinkReadPort`。

## 前端

前端是位于 `web/` 下的 Vue 3 + Vite SPA。

- `views/`：路由级编排
- `components/`：展示型 UI，包括共享的 `AppPageShell` 和 links 相关子组件
- `composables/`：从视图中抽取出的页面和会话行为
- `services/`：面向 HTTP 传输的辅助函数和共享类型
- `stores/`：认证等应用状态

SPA 同时暴露原有短链页面和自助控制平面控制台：

- 租户概览
- 应用和应用详情
- 域名
- API Key
- 审批
- 审计
- 链接、统计和标签

控制平面页面保持轻量：所有 HTTP 工作都经由 `services/*` 模块完成，视图状态由专门的 composable 驱动，例如 `useApplicationsPage`、`useApplicationDetailPage`、`useDomainsPage`、`useApprovalsPage`、`useAuditPage`。`LinksView.vue` 和 `StatsView.vue` 也已经升级为理解应用范围，同时不重复实现传输逻辑。

## 部署形态

仓库当前交付形态包括：

- 一个后端运行时（`server/app`）
- 一个前端应用（`web`）
- 一个 MySQL 和一个 Redis

后端使用 Spring 标准数据源连接 MySQL；需要写后读一致性的操作在同一数据库事务中完成。数据库结构由部署流程在空库上执行唯一的 `database/schema.sql`，应用运行时不创建或升级 schema。

这不是微服务部署。模块边界用于明确所有权和测试边界，但日常正确性是围绕单一部署单体设计的，而不是围绕未来服务拆分设计的。

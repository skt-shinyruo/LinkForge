# LinkForge 架构

LinkForge 是一个模块化单体：后端由 Maven Reactor 组织，前端是独立的 Vue 应用。当前架构优先保证单一部署运行时内部的模块边界清晰，而不是把系统拆成独立部署的服务。

## 后端

- `server/foundation`：拆分为纯共享库代码和显式运行时支持两类。`foundation.config`、`foundation.id`、`foundation.tx`、`foundation.util` 保持轻框架依赖的库代码定位；`RequestIdFilter`、启动检查、集成事件 MyBatis 装配等运行时 Bean 放在 `foundation.runtime..` 下。
- `server/contracts/*`：沉淀单体内部仍值得维护的共享语言。`contract-api` 保存通用 API 契约，`contract-shortlink` 承载短链集成事件载荷，`contract-redirect` 承载跳转读取和缓存契约，`contract-analytics` 承载统计契约，`contract-platform` 承载应用、域名、授权相关词汇，`contract-governance` 承载稳定的审批编排语言。
- `server/accounts`：负责账号、租户、认证和 API Key 管理，并拆成 `domain`、`application`、`infrastructure`、`interfaces`、`runtime` Maven 模块。它的应用层依赖端口和共享契约，不依赖基础设施类或运行时安全上下文辅助类。
- `server/platform`：负责租户应用、域名、配额和策略等控制平面能力。它建模 `tenant -> application -> domain` 关系，并暴露自助控制台使用的租户和平台管理 HTTP 接口。
- `server/governance`：负责审批和审计管理。它持久化敏感操作请求、审批决策和审计日志，并为链接目标变更、统计导出审批等场景暴露窄口径应用 API。
- `server/shortlink`：短链管理写侧，拆成 `domain`、`application`、`infrastructure`、`interfaces`、`runtime`。它拥有持久化短链状态，并向下游投递集成事件。公开应用入口聚焦具体用例接口，而不是一个大而全的聚合服务接口。
- `server/redirect`：基于缓存的跳转服务，拆成 `domain`、`application`、`infrastructure`、`interfaces`、`runtime`。跳转正确性由 Redis 和缓存未命中时的权威短链读取 API 共同保证；它不再维护独立的 redirect 投影模型。
- `server/analytics`：负责访问记录和读模型，拆成 `domain`、`application`、`infrastructure`、`interfaces`、`runtime`。
- `server/app`：Spring Boot 可执行组合根。`LinkForgeApplication` 显式导入各上下文拥有的运行时模块，包括 `FoundationRuntimeModule`、`AccountsRuntimeModule`、`ShortlinkRuntimeModule`、`RedirectRuntimeModule`、`AnalyticsRuntimeModule`、`PlatformRuntimeModule`、`GovernanceRuntimeModule`，而不是依赖包扫描或 app 自有包装层；各限界上下文的运行时模块放在各自上下文的 `runtime` Maven 模块中。安全装配按请求族拆分，不再集中在一个巨型安全配置类里。
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

拥有访问摄取、聚合、明细存储、统计读取和导出集成。Analytics 读模型保持私有；跨上下文链接补全使用 shortlink 发布的契约。

### Governance

拥有审批请求生命周期、审批决策、敏感操作记录和审计日志。需要跨上下文审批编排时，Governance 通过发布契约暴露相关能力。

## 战术 DDD 规则

- `domain` 拥有聚合行为、不变量、值对象、领域服务和内部领域事件。
- `application` 拥有用例编排、事务、仓储端口、授权输入处理和集成事件发布。应用服务可以接收显式的参与者和输入对象，但不能读取隐藏的运行时安全上下文。应用代码中的公开请求和结果模型应当是顶层类型，不应把 DTO、Result、Request 容器嵌套在服务类内部。
- `interfaces` 拥有 HTTP 映射、请求校验、身份主体提取和传输响应塑形。控制器把应用 DTO 映射为传输 DTO，而不是直接把应用 record 暴露为 HTTP 契约。
- `infrastructure` 拥有 MyBatis、Redis、调度器和持久化映射。基础设施适配器不能依赖 `AuthContext`、`TenantGuard` 等运行时安全辅助类；租户范围必须通过应用端口显式传入。
- `runtime` 拥有上下文本地的 Spring 组合装配，负责导入该上下文的 application、infrastructure、interfaces 配置。
- `contracts/*` 拥有限界上下文之间共享的发布语言。契约模块表达稳定业务语义，不能复用 foundation 的安全或上下文参与者对象作为发布 API 语言。
- 限界上下文不能导入其他上下文的 `domain`、`application`、`infrastructure`、`interfaces` 或 `runtime` 包。

## 跳转正确性路径

跳转读取按以下顺序执行：

1. 使用 `host + code` 查询 Redis 跳转缓存（`LinkCachePort`）
2. 使用 `host + code` 查询权威短链读取 API
3. 对真实未命中写入负缓存

短链变更只会在事务提交后通过 `AfterCommit` 驱逐跳转缓存条目，因此事务回滚不会导致 Redis 与持久化状态发生偏离。系统没有第二条 redirect 投影正确性通道。

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
- MySQL 主库/从库、Redis 等支撑基础设施

后端使用 Apache ShardingSphere-JDBC 作为逻辑应用数据源。`readwrite_ds` 把写入路由到 `write_ds`，把符合条件的非事务读取路由到 `read_ds_0`；事务内读取通过 `transactionalReadQueryStrategy: PRIMARY` 保持走主库。Flyway 显式绑定主库 MySQL 连接，不通过逻辑读写分离数据源执行迁移。

这不是微服务部署。模块边界用于明确所有权和测试边界，但日常正确性是围绕单一部署单体设计的，而不是围绕未来服务拆分设计的。

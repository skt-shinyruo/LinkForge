# LinkForge 核心逻辑地图

本文只回答三个问题：核心流程在哪里、哪个上下文拥有事实、详细规则应读哪份文档。状态表、配置默认值、错误与排障不在这里复制，以免产生多份事实源。

## 运行模型

LinkForge 是模块化单体。`server/app` 是 Spring Boot 组合根，各限界上下文通过 `server/contracts/*` 协作：

| 层/模块 | 核心职责 | 不应承担 |
| --- | --- | --- |
| `interfaces` | HTTP 校验、身份映射、响应塑形 | 业务状态机、隐式跨租户查询 |
| `application` | 用例编排、事务、授权输入、端口与副作用 | Servlet/SecurityContext 读取、基础设施细节 |
| `domain` | 聚合、值对象和不变量 | Spring/MyBatis/Redis |
| `infrastructure` | MyBatis、Redis、调度、outbox、事件适配 | 决定业务权限或可用性 |
| `runtime` | 上下文本地 Spring 装配 | 发布业务 API |
| `contracts/*` | 跨上下文稳定语言 | 复用其他上下文私有模型 |

横切运行时、配置和启动门禁见 [Foundation 与关键配置](foundation-and-configuration.md)，共享 Java/JSON/Redis 契约见 [发布契约](published-contracts.md)，模块依赖规则见 [架构总览](architecture.md)。

## 核心链路

### 用户控制台

1. Vue router 调用 auth store 完成一次并发收敛的会话初始化。
2. `/api/**` 安全链校验 Bearer 或 Cookie JWT，Accounts 每请求复核 tenant/user/tokenVersion。
3. HTTP adapter 把 `AuthPrincipal` 映射为显式 actor，应用服务按 tenant 和 role 授权。
4. Platform 管理 application/domain/quota；Shortlink 管理链接事实；Governance 管理敏感操作审批。
5. 前端统一通过 `apiContract.ts`、`http.ts` 和 services 处理端点、CSRF、`ApiResponse` 与业务错误。

权威说明：[认证与会话](auth-and-session.md)、[平台控制面](platform-control-plane.md)、[短链管理](shortlink-management.md)、[审批与审计](governance-approval-audit.md)、[前端控制台](frontend-console.md)。

### OpenAPI 发链

1. `/api/v1/open/**` 只读取 `X-API-Key`，不接受 JWT/Cookie。
2. Accounts 解析 `lfk_{id}_{secret}`、检查 disabled 缓存、回库校验 hash，并拒绝历史未绑定 application 的 key。
3. `applicationId` 通过 authentication details 显式传给 Shortlink actor scope。
4. Platform 校验 application/domain 授权；Shortlink 在同一事务内预留月发链额度并创建链接。

权威说明：[OpenAPI 与 API Key](openapi-api-key.md)、[短链管理](shortlink-management.md)。

### 短链写入与缓存一致性

1. Shortlink 聚合执行创建、更新、归档、恢复或删除不变量。
2. 写入数据库并在业务事务内追加集成事件。
3. 同一事务内写 Redirect 缓存失效 outbox。
4. 提交后钩子尝试快速驱逐；失败不回滚业务事务。
5. outbox worker 重试，驱逐允许重复。

缓存不是事实源。详细事务、锁、权限、clear 字段和事件规则见 [短链管理](shortlink-management.md)；一致性时序见 [跨上下文事件与一致性](cross-context-events.md)。

### 跳转

1. Redirect filter 解析可信客户端 IP 和访问上下文，并执行 allow/deny、bot 与限流。
2. `RedirectService` 按 `host + code` 查询三态 Redis 缓存。
3. `MISS` 时同步调用 `ShortLinkReadPort`；权威未命中才写负缓存。
4. Redirect 自己判定 lifecycle、enabled、expiry、preview、落地页与应用点击额度。
5. 只有真实跳转才记录访问事件并生成最终 query-forward URL。

权威说明：[跳转与缓存](redirect-and-cache.md)。

### 统计

1. Redirect 把真实访问交给 Analytics appender，直接执行 Redis Lua 原子聚合。
2. 脚本增加 PV、HLL UV、scope HLL 并推进 V2 generation marker。
3. `AnalyticsFlushJob` 按 marker 读取当前累计值、upsert MySQL 后做 generation CAS。
4. 报表读取 MySQL，并通过 Shortlink 发布读端口补全链接摘要。

统计是最终一致链路：requestId 保护单次脚本重试，但调用方重复生成事件仍可能重复 PV；HLL UV 是近似值。权威口径、
隐私与限制见 [统计采集与报表](analytics-ingestion-and-reporting.md)。

### 审批

1. 业务上下文用版本化 payload 向 Governance 提交 before/after snapshot。
2. Governance 保存待审批聚合并记录审计。
3. 审批时先选择并校验唯一执行器，再推进 CAS 状态。
4. 执行器重新校验租户、资源当前状态及操作定义的前置条件；存在 before snapshot 时还要校验陈旧状态，不能把批准当作绕过并发保护的凭据。
5. 执行结果进入终态和审计；目标地址变更是当前支持的敏感操作。

权威说明：[审批与审计](governance-approval-audit.md)、[发布契约](published-contracts.md)。

## 上下文所有权

### Accounts

拥有 tenant/user/role、JWT 会话撤销状态和 API Key。核心入口是 `AuthService`、`UserAdminService`、`AccountStatusService`、`ApiKeyService`。详细说明见 [认证与会话](auth-and-session.md) 和 [OpenAPI 与 API Key](openapi-api-key.md)。

### Platform

拥有 application、domain、domain authorization、application policy 和 quota 配置。核心入口是 `ApplicationProvisioningService`、`PlatformControlPlaneService`、`DomainAuthorizationPolicy`。详细说明见 [平台控制面](platform-control-plane.md)。

### Shortlink

拥有短链聚合、目标地址、生命周期、标签、query forwarding 覆盖值、发链额度预留和短链事件。唯一发布读入口是 `ShortLinkReadPort`。详细说明见 [短链管理](shortlink-management.md)。

### Redirect

拥有流量面解析、三态缓存、风险控制、预览/不可用响应、query 合并和跳转前点击额度检查；不拥有链接事实。详细说明见 [跳转与缓存](redirect-and-cache.md)。

### Analytics

拥有 Redis PV/UV 聚合、V2 marker flush、报表和统计目录。详细说明见 [统计采集与报表](analytics-ingestion-and-reporting.md)。

### Governance

拥有审批请求状态机、审批决策、执行编排和审计日志；具体敏感操作仍由原业务上下文执行。详细说明见 [审批与审计](governance-approval-audit.md)。

### Frontend

拥有交互状态、路由权限提示、transport 和页面异步编排；后端始终是业务授权和状态规则的最终裁决者。详细说明见 [前端控制台](frontend-console.md)。

## 跨上下文契约入口

| 发布契约 | 发布者 | 主要消费者 |
| --- | --- | --- |
| `ApplicationScopePort` | Platform | Accounts、Shortlink、Redirect |
| `DomainHostnameLookupPort` | Platform | Shortlink、Analytics |
| `ShortLinkReadPort` | Shortlink | Redirect、Analytics、Governance |
| `LinkCachePort` | Redirect | Redirect、Shortlink 缓存失效 |
| `VisitRecorderPort` | Analytics | Redirect |
| `ApplicationClickQuotaReservationPort` | Analytics | Redirect |
| `ApprovalSubmissionPort` | Governance | Shortlink、Analytics、Platform |
| `ApprovalExecutionPort` | 业务上下文 | Governance |

完整 null、UTC、错误、版本和 Redis wire 约定见 [发布契约](published-contracts.md)。

## 当前限制

- 点击额度 Redis adapter 内部故障固定 fail-open；外围 `quota.fail-open` 只覆盖仍外抛的查询/调用错误。
- requestId 只保护单次脚本重试；调用方重复生成事件仍可能重复 PV，HLL UV 是近似值且跨日不能直接求和。
- scope UV 在历史/缺失 scope 数据上存在 fallback，不能当作精确审计口径。
- legacy provisioning 和部分审批终态语义为兼容行为，本轮只记录，不做高风险改写。

## 维护入口

核心流程从本页进入各权威专题；专题直接列出源码类型名。新增或修改核心逻辑时，按 [文档规范](documentation-standards.md) 同步专题、源码契约和测试。

# LinkForge 核心逻辑

本文记录 LinkForge 当前代码中的核心业务逻辑。它补充 `docs/architecture.md`：架构文档说明模块边界，本文说明请求进入系统后，各模块实际做了什么、按什么规则协作。

## 总体运行模型

LinkForge 是一个模块化单体：

- 后端由 `server/app` 作为 Spring Boot 入口，显式导入 `foundation`、`accounts`、`platform`、`shortlink`、`redirect`、`analytics`、`governance` 的 runtime module。
- 各业务上下文通过 `server/contracts/*` 暴露跨上下文能力，不直接依赖其他上下文的 domain/application/infrastructure/interfaces/runtime 包。
- `interfaces` 负责 HTTP DTO、参数校验、认证主体提取和响应形状。
- `application` 负责编排用例、事务、端口调用、跨上下文契约调用和副作用触发。
- `domain` 负责聚合规则、值对象和领域状态变化。
- `infrastructure` 负责 MyBatis、Redis、定时任务、集成事件和外部存储。
- `runtime` 只做本上下文 Spring 组装。

核心链路可以概括为：

1. 管理端用户或 OpenAPI 客户端经安全过滤器认证为 `AuthPrincipal`。
2. Accounts 提供租户、用户、角色和 API Key 状态校验。
3. Platform 提供应用、域名和额度授权能力。
4. Shortlink 写入短链真相数据，发布短链集成事件，并在事务提交后驱逐跳转缓存。
5. Redirect 读取 Redis 缓存，未命中时回源 Shortlink 读端口，执行可用性、预览、额度和跳转策略。
6. Analytics 从 Redirect 接收访问事件，写 Redis Stream，再投影为聚合统计和可选访问明细。
7. Governance 管理敏感操作审批，审批通过后可回调具体上下文执行操作。
8. Vue 前端用 router、Pinia auth store、services 和 composables 组织控制台页面。

## App 与 Foundation 横切逻辑

`server/app/src/main/java/com/linkforge/LinkForgeApplication.java` 是唯一可执行入口。它只扫描 app 层通用包，并通过 `@Import` 导入各上下文 runtime module；配置属性由 `CoreProperties`、`IdProperties`、`SecurityProperties`、`CorsProperties`、`RedirectProperties`、`AnalyticsProperties`、`EdgeProperties` 承载。

### 安全过滤链

安全配置按请求空间拆分：

- `OpenApiSecurityConfig` 匹配 `/api/v1/open/**`，只接受 `X-API-Key`。它使用 `ApiKeyAuthenticationFilter` 调用 `ApiKeyAuthenticator`，通过后创建带 `ROLE_OPENAPI` 的 `AuthPrincipal`，并把 `ApiKeyAuthenticationDetails` 放进 `Authentication.details`。
- `ApiSecurityConfig` 匹配 `/api/**`，接受 JWT Bearer 或可选 Cookie JWT。它使用 `JwtAuthenticationFilter` 解析 JWT，随后调用 `AccountStatusVerifier.requireActiveUserAndTenant()` 做租户、用户状态和 tokenVersion 校验。
- `/r/**` 不进入 API 安全链，跳转流量由 Redirect 自己的风控和响应逻辑处理。
- `SecurityConfig` 启用方法级安全，控制器上的 `@PreAuthorize` 执行角色校验。

JWT 逻辑的关键点：

- Bearer token 无效时直接返回 401。
- Cookie token 无效或超长时清 cookie 后继续，让公开认证接口仍可访问。
- 用户或租户被禁用时返回业务错误，不简单清理 cookie。
- Cookie 模式开启时，非 GET 写请求使用双提交 CSRF cookie/header；Bearer header 路径忽略 CSRF。

API Key 逻辑的关键点：

- OpenAPI 只读 `X-API-Key`，不接受 JWT/Cookie。
- API Key 认证成功后只验证租户状态；API Key 自身状态由 Accounts 的 `ApiKeyService` 负责。
- API Key 绑定的 `applicationId` 不放入 `AuthPrincipal`，而是放入 `ApiKeyAuthenticationDetails`，再由 `PrincipalActorMapper.requireApiKey()` 转成 `ApiKeyActor`。

### 请求 ID、异常和启动校验

- `RequestIdFilter` 读取 `X-Request-Id`，只接受长度不超过 64 且字符安全的值；否则生成新的无横线 UUID。请求 ID 会进入 MDC、响应头和 `ApiResponse.requestId`。
- `GlobalExceptionHandler` 只覆盖业务 API 上下文，统一把 `BusinessException`、参数校验异常、权限异常和未处理异常映射为 `ApiResponse`。
- Redirect 有单独的 `RedirectGlobalExceptionHandler` 和响应 writer，用于返回跳转面向的错误响应。
- `AppStartupValidator` 在启动时校验 ID 配置、`app.base-url` 和各上下文提供的 `StartupCheck`。生产或 `app.strict-config=true` 时更严格。
- `AppSchedulingConfig` 由 `app.scheduling.enabled` 控制定时任务开关。

### ID、事务后副作用和集成事件

- `SnowflakeIdGenerator` 生成 64-bit ID，epoch 是 `2024-01-01T00:00:00Z`，包含 datacenterId、workerId 和序列号；同毫秒内序列溢出会等待下一毫秒，时钟小幅回拨会等待到上一次时间戳之后。
- `AfterCommit`/`SpringPostCommitHookAdapter` 把缓存驱逐等副作用延迟到事务提交后执行；没有活跃事务时直接执行。
- `MybatisIntegrationEventStore` 写入并读取集成事件表，供 Analytics 的短链目录投影等异步消费者使用。

## Accounts：租户、用户、登录和 API Key

Accounts 管理认证状态，不把用户表、角色表、API Key 表细节泄漏给其他上下文。

### 用户注册、登录、注销

核心文件：`AuthService`、`AuthController`、`AccountsJwtTokenIssuer`、`JwtService`。

`AuthService.register()` 的流程：

1. 检查邮箱是否已存在。
2. 生成 tenantId，创建启用状态租户。
3. 生成 userId，密码哈希后创建启用状态用户，初始 `tokenVersion=0`。
4. 赋予首个用户 `TENANT_ADMIN`。
5. 签发 JWT 并返回 `AuthPrincipal`。

`AuthService.login()` 的流程：

1. 按邮箱查用户和租户。
2. 租户必须是 `ACTIVE`，用户必须是 `ACTIVE`。
3. 校验密码哈希。
4. 读取用户角色，缺省为 `USER`。
5. 带当前 `tokenVersion` 签发 JWT。

`AuthService.logout()` 会递增用户 `tokenVersion`，并驱逐用户状态缓存，使旧 JWT 失效。

`AuthController` 暴露 `/api/v1/auth/register`、`/login`、`/csrf`、`/logout`。自助注册受 `securityProperties.registrationEnabled` 控制。Cookie JWT 模式开启时，登录和注册会写 HttpOnly cookie，响应 body 不再暴露 token。

### 用户管理和账号状态

核心文件：`UserAdminService`、`UserAdminController`、`AccountStatusService`。

`UserAdminService` 支持租户管理员列出用户、创建用户、启用/禁用用户、重置密码：

- 新用户角色只允许 `TENANT_ADMIN` 和 `USER`，空角色默认 `USER`。
- 禁用用户时禁止禁用当前管理员自己。
- 如果目标用户是启用中的租户管理员，则必须至少保留一个启用中的租户管理员。
- 重置密码会递增 `tokenVersion`，使旧 JWT 失效。
- 用户状态变化会驱逐状态缓存。

`AccountStatusService` 用短 TTL Redis 缓存减少认证链路 DB 压力：

- `requireActiveTenant()` 校验租户存在且启用。
- `requireActiveUserAndTenant()` 先校验租户，再校验用户归属、状态和可选 tokenVersion。
- Redis 不可用时走持久层；缓存命中 disabled 会直接拒绝。

### API Key 管理与认证

核心文件：`ApiKeyService`、`ApiKeyAdminController`、`ApiKeyAuthenticationFilter`。

API Key 规则：

- 创建 API Key 必须提供 `applicationId`，并通过 Platform 的 `ApplicationScopePort.requireApplicationExists()` 校验。
- 返回给客户端的 key 格式是 `lfk_{id}_{secret}`；数据库只保存 secret 的密码哈希。
- `authenticate()` 会解析 key、限制整体长度和 secret 长度、校验 prefix/id/secret、校验 API Key 状态。
- 禁用的 API Key 会写入短 TTL 认证缓存；重新启用或轮换后在事务提交后驱逐缓存。
- `lastUsedAt` 更新带节流逻辑，优先用 Redis token 控制写频率，避免每次 OpenAPI 请求都写库。

租户管理员可通过 `/api/v1/api-keys` 创建、列表、启用、禁用和轮换 API Key；列表可以按 `applicationId` 过滤。

## Platform：应用、域名、额度和授权

Platform 是控制面，管理 `tenant -> application -> domain`，并给 Shortlink、Redirect、Accounts、Analytics 提供跨上下文端口。

### 应用和域名创建

核心文件：`ApplicationProvisioningService`、`TenantAdminApplicationController`、`TenantAdminDomainController`。

`createApplication()`：

- 要求 actor 属于当前租户。
- 校验 `applicationKey` 和 `displayName` 非空。
- 创建启用状态应用。
- 同步创建默认 `ApplicationPolicy`：默认域名范围、默认跳转状态码、默认预览开关。
- 同步创建默认 `ApplicationQuota`：月发链额度和月点击额度。

`createTenantSharedDomain()`：

- 使用 `Hostname.parse()` 规范化和校验 hostname。
- 创建 `TENANT_SHARED`、`ACTIVE`、`FIRST_PARTY` 域名，不绑定具体应用。

`createApplicationDedicatedDomain()`：

- 先确认应用属于当前租户。
- 创建 `APPLICATION_DEDICATED` 域名，并绑定该应用。

`authorizeDomain()` 只允许把租户共享域名授权给应用使用。专属域名不能通过该接口授权。

### 域名合法性与应用域名授权

核心文件：`Hostname`、`DomainAuthorizationPolicy`、`PlatformControlPlaneService`。

`Hostname` 规则：

- trim、小写、IDN 转 ASCII。
- 禁止空值、空白、`:`、`/`、`\`、`@`、`*`。
- 禁止 `localhost`、`*.localhost`、IPv4 字面量、首尾点、连续点。
- 至少两个 label，每个 label 长度 1-63，不得以 `-` 开头或结尾，只允许 `[a-z0-9-]`。

`DomainAuthorizationPolicy.requireApplicationCanUseDomain()` 规则：

- 域名必须 `ACTIVE`。
- 专属域名必须绑定到当前应用。
- 共享域名必须存在授权关系。

`PlatformControlPlaneService.requireApplicationAndDomainAuthorized()` 组合应用存在性、域名存在性和 domain policy；失败时转成明确的业务错误。

### 跨上下文端口

`PlatformApplicationScopeAdapter` 实现：

- `ApplicationScopePort`：供 Shortlink 创建应用级短链、Accounts 创建 API Key、Redirect 校验点击额度。
- `DomainHostnameLookupPort`：供 Shortlink/Analytics 把 `domainId` 和 hostname 互查。
- `LegacyApplicationProvisioningPort`：供旧短链迁移补齐默认应用/域名绑定。

`LegacyApplicationBindingService.ensureLegacyDefaultBinding()` 会给旧租户创建 `legacy-default` 应用和基于 `app.base-url` 的 synthetic legacy hostname，用于兼容历史未应用化短链。

## Shortlink：短链真相写侧

Shortlink 拥有短链真相数据、生命周期、目标地址、标签、跳转策略和集成事件。

### 聚合和值对象

核心文件：`ShortLink`、`ShortCode`、`HttpUrl`、`QueryForwardMode`、`QueryForwardAllowlist`、`QueryParamPattern`。

`ShortCode`：

- trim 后不能为空。
- 长度 6-32。
- 仅允许 ASCII 字母数字。
- 短码大小写敏感。

`HttpUrl`：

- trim 后不能为空，最大 2048。
- 必须是绝对 URI。
- scheme 只支持 http/https。
- host 必须存在。

`QueryForwardMode`：

- 支持 `OFF`、`ALLOWLIST`、`ALL`。
- null 或空字符串表示不设置，由全局默认接管。

`QueryForwardAllowlist`：

- 去重并保留顺序，最多 50 项。
- 持久化为逗号分隔字符串，最大 1024。
- 每项由 `QueryParamPattern` 校验，支持精确名如 `utm_source` 或前缀通配如 `utm_*`；不能是单独 `*`。

`ShortLink` 聚合规则：

- `id`、`tenantId` 必须大于 0，`code` 和 `originalUrl` 必填。
- `enabled` 默认 true，`previewEnabled` 默认 false。
- `redirectStatusCode` 只允许 301/302 或 null。
- `note` 最大 512。
- 归档后不允许更新；删除前必须先归档。
- 创建、更新、归档、恢复、删除都会记录领域事件，供 application 层发布集成事件。
- `expiresAtUtc`、`archivedAtUtc`、`createdAtUtc`、`updatedAtUtc` 语义上都是 UTC `LocalDateTime`。

### 创建短链

核心文件：`ShortLinkApplicationService`、`ShortLinkActorScopeResolver`、`CreateShortLinkCommandHandler`、`ShortLinkController`、`OpenApiShortLinkController`。

HTTP 入口：

- 用户控制台：`POST /api/v1/links`。
- 应用级用户控制台：`POST /api/v1/applications/{applicationId}/links`，要求 `TENANT_ADMIN`。
- OpenAPI：`POST /api/v1/open/links` 或 `/api/v1/open/applications/{applicationId}/links`，使用 API Key。

作用域解析：

- 普通用户不带路径 applicationId 时，只能创建非应用级短链；如果请求体带 `applicationId` 或 `domainId`，必须是租户管理员。
- 用户路径带 applicationId 时，必须是租户管理员，并要求 body 中 applicationId 与路径一致。
- API Key 必须绑定 `applicationId`，认证成功后只能访问该应用。
- 历史遗留的未绑定 API Key 在认证阶段视为无效，不再允许通过路径或 body 临时指定应用范围。

`CreateShortLinkCommandHandler.handle()` 流程：

1. 校验 `createdBy` 和请求体。
2. 要求 `applicationId` 与 `domainId` 要么都为空，要么同时提供。
3. 应用级短链调用 Platform 校验应用和域名授权。
4. 若应用有月发链额度，按 UTC 月初/月末调用 `ApplicationLinkQuotaReservationPort.tryReserveMonthlyLink()` 预留额度。
5. 生成 ID；若未传 customCode，则用 ID 的 Base62 作为短码。
6. customCode 先检查唯一性：非应用级按 code 查，应用级按 domainId + code 查。
7. 构造 `ShortLink` 聚合，完成 URL、短码、跳转状态码、预览、不可用落地页、query 转发策略等校验。
8. 插入数据库；并发唯一冲突时 customCode 转成 `CODE_ALREADY_EXISTS`。
9. 设置标签。
10. 发布短链领域事件到集成事件表。
11. 事务提交后驱逐 redirect cache。
12. 返回带标签的 `LinkDto`。

应用发链额度由 `MybatisApplicationLinkQuotaReservationPort` 使用 MySQL named lock 保护，先确保月度使用记录存在，再在 limit 内自增。

### 更新、审批、归档、恢复和删除

`UpdateShortLinkCommandHandler.handle()`：

- 先按 tenantId + linkId 读取短链，不存在则 `LINK_NOT_FOUND`。
- 归档短链禁止更新。
- 应用级短链修改 `originalUrl` 时，需要提交 Governance 审批；此时必须由有效 `UserActor` 发起。
- 如果目标地址变更需要审批，则本次请求不能夹带其他有效变更；否则返回“请先单独提交目标地址变更”。
- 目标地址审批请求提交成功后，返回的短链仍是当前生效地址，同时带 `pendingApproval`、`approvalRequestId` 和 `requestedOriginalUrl` 供调用方提示“审批通过后生效”。
- 不需要审批时，按字段执行局部更新：生命周期、目标地址、备注、启用状态、过期时间、跳转状态码、预览、不可用落地页、query 转发模式、allowlist、标签。
- `clearExpiresAt`、`clearRedirectStatusCode`、`clearQueryForwardMode` 是显式清空语义，且不能与对应新值同时传。
- 更新使用乐观锁，失败返回 `LINK_STALE_WRITE`。
- 更新成功后递增版本、发布事件、事务提交后驱逐 redirect cache。

`LinkDestinationChangeApprovalExecutor` 是 Governance 审批通过后的执行器：

- 只支持 `PUBLIC_LINK_DESTINATION_CHANGE`。
- 校验审批 before/after snapshot 的 linkId 一致。
- 校验审批目标应用仍与短链一致、短链仍 active 且未归档、当前 originalUrl 仍等于 before snapshot。
- 更新目标地址，乐观锁写入，发布事件并提交后驱逐缓存。

生命周期命令：

- `ArchiveShortLinkCommandHandler`：设置 `archivedAtUtc`，发布归档事件，提交后驱逐缓存；重复归档是幂等返回。
- `RestoreShortLinkCommandHandler`：清空 `archivedAtUtc`，发布恢复事件，提交后驱逐缓存；未归档时幂等返回。
- `DeleteShortLinkCommandHandler`：要求已归档，先删除标签关系，再按版本删除短链，发布删除事件，提交后驱逐缓存。

`ShortLinkWriteGuard` 可用 `app.shortlink.write-enabled=false` 将写接口置为维护中。

### 标签、查询、导入导出

标签逻辑：

- `SetLinkTagsCommandHandler` 每次先删除 linkId 所有标签关系，再按请求重建。
- 单条短链最多 20 个标签。
- 标签名 trim 后去空、去重，最长 64。
- 标签不存在时创建；并发创建冲突会回读已创建标签。

查询逻辑：

- `SearchShortLinksQueryHandler` 支持 archived、enabled、keyword、tag、applicationId 过滤。
- Offset 最大 100000，避免大 offset 拖垮数据库。
- 查询结果批量加载标签后映射为 DTO。

CSV 导入：

- `ImportShortLinksCsvCommandHandler` 每行使用 `RequiresNewTransactionPort` 独立事务创建短链。
- 支持 scoped application/domain；应用级导入必须提供 domainId。
- CSV 行可用 `applicationId`、`domainId` 或 hostname 指定应用域名上下文。
- `expiresAt` 支持 ISO-8601 Instant/OffsetDateTime，也兼容 legacy LocalDateTime 并按 UTC 处理。
- 每行失败只记录错误，不影响其他行。

CSV 导出：

- `ExportShortLinksCsvQueryHandler` 复用搜索逻辑，导出 linkId、applicationId、domainId、hostname、code、originalUrl、note、enabled、expiresAt、tags。
- hostname 通过 `DomainHostnameLookupPort` 按 domainId 补齐。

### 读端口与集成事件

`ShortLinkReadApplicationService` 实现 `ShortLinkReadPort`：

- `findRedirectMetaByHostAndCode()` 供 Redirect 回源。
- `findOwnership()` 供 Analytics/Governance 校验链接归属。
- `listSummaries()` 供 Analytics Top 链接补齐 code、shortUrl、originalUrl。

`AuthoritativeLinkMetaSourceAdapter` 和 `MybatisShortLinkReadRepository` 的 host + code 查找规则：

1. host 为空时按 code 查活动短链。
2. host 非空时先按 hostname + code 查。
3. 如果 host 是 `app.base-url` 的 base host，则兼容 legacy base host 绑定。
4. 仍未命中且是 base host 时，兼容未应用化 unscoped code。

短链事件：

- `ShortLinkDomainEventDispatcher` 从聚合拉取领域事件。
- `ShortLinkEventPublisherAdapter` 转成 infrastructure appender。
- `ShortLinkEventAppender` 写集成事件表，producer 是 `shortlink`，aggregateType 是 `shortlink`。
- `ShortLinkEventFactory` 构造 `ShortLinkPublicSnapshot`，包含 tenantId、linkId、code、hostname、originalUrl、启用/过期/预览/query 策略、applicationId、domainId、archivedAt。

## Redirect：跳转流量面

Redirect 只负责跳转解析、缓存、风控、预览页、不可用页和访问事件记录；短链真相仍归 Shortlink。

### 边缘风控和 VisitInfo

核心文件：`RedirectRiskControlFilter`、`RedirectClientIpResolver`、`RedirectRiskControl`、`RedisFixedWindowRateLimiter`。

`RedirectRiskControlFilter` 只过滤 `/r/**`：

- 解析客户端 IP、User-Agent、Referer、Accept-Language 和营销 tracking 参数。
- 把 `VisitInfo` 放到 request attribute，供后续 request mapper 使用。
- 执行 IP 黑白名单、bot 策略、IP 限流、IP+code 限流。
- 拒绝时写 Redirect 错误响应，并可写 `Retry-After`。

客户端 IP 解析规则：

- 默认只信任 `request.remoteAddr`。
- 只有 remoteAddr 命中 `app.edge.trusted-proxies` 时，才采信 `X-Real-IP` 或 `X-Forwarded-For`。
- XFF 从右向左剔除可信代理，取第一个非可信代理。
- 超长 XFF 或过多 token 会回退 remoteAddr，避免 DoS。

风控规则：

- denylist 优先。
- allowlist 非空时，不在 allowlist 的 IP 被拒绝。
- bot 可配置为直接阻断，或使用更低的 bot IP 限流阈值。
- Redis 固定窗口限流失败时可按配置 fail-open。

Tracking 参数采集：

- 默认只采集 `utm_*`、`gclid`、`fbclid`。
- 参数名最长 128，最多采 20 个。
- 参数值清理控制字符，并按配置长度截断。

### 解析、缓存、预览和跳转

核心文件：`RedirectController`、`RedirectHttpRequestMapper`、`RedirectService`、`LinkCacheService`、`RedirectHttpResponseWriter`、`RedirectHtmlPageRenderer`、`RedirectUrlBuilder`。

`GET /r/{code}` 流程：

1. `RedirectHttpRequestMapper` 从 path、host、Accept、`__lf_confirm` 和 `VisitInfo` 构造 `ResolveRedirectRequest`。
2. `RedirectService.resolve()` 规范化 code：trim、最大 32、只允许字母数字；不合法直接 not found。
3. 先查 Redis `LinkCachePort.lookup(host, code)`。
4. 命中负缓存时直接 not found。
5. 命中正缓存时拿到 `LinkMeta`。
6. 未命中时调用 `ShortLinkReadPort.findRedirectMetaByHostAndCode(host, code)` 回源。
7. 回源命中后写 Redis 正缓存；回源未命中写短 TTL 负缓存。
8. 静态可用性校验：enabled 必须 true，lifecycle 必须 active，expiresAt 必须晚于当前 UTC。
9. HTML 请求且短链开启 preview 且未带 `__lf_confirm` 时返回预览页，不计访问。
10. 应用级短链执行月点击额度预留；额度耗尽返回 unavailable。
11. 记录访问事件。
12. 返回跳转响应。

`LinkCacheService` 规则：

- host 维度 key 是 `link:host:{host}:code:{code}`，无 host 时是 `link:code:{code}`。
- 正缓存 TTL 使用 `app.redirect.cache-ttl-seconds`。
- 负缓存 sentinel 是 `__lf_not_found__`，TTL 使用 `app.redirect.not-found-cache-ttl-seconds`。
- Redis 读写异常不影响主链路；读异常回源，写异常只 debug log。
- 反序列化失败会删除缓存并当作 miss。

`RedirectHttpResponseWriter`：

- `PREVIEW` 返回预览 HTML。
- `NOT_FOUND` 对 HTML 返回 not-found HTML 或全局 not-found landing URL；非 HTML 抛业务 404。
- `UNAVAILABLE` 对 HTML 返回禁用/过期/额度 HTML 或 landing URL；非 HTML 抛对应业务错误。
- `REDIRECT` 设置 Location，状态码来自短链级 `redirectStatusCode`，否则使用全局默认 301/302。

`RedirectConfirmHrefBuilder` 构造预览确认 URL：

- 保留原请求 query，但跳过 `__lf_confirm` 和 `__lf_preview`。
- 限制参数数、每参数值数、名称长度、值长度和最终 href 长度。
- 最后追加 `__lf_confirm=1`。

`RedirectUrlBuilder` 执行 query 转发：

- 模式优先用短链级配置，其次全局配置，默认 `OFF`。
- `ALLOWLIST` 会合并全局 allowlist 和短链 allowlist。
- 保留参数默认包含 `__lf_confirm`、`__lf_preview`，永不转发。
- 目标 URL 已有的 query key 优先，不被请求参数覆盖。
- 追加 query 最大 2048，最终 URL 最大 4096；超过或异常时回退 originalUrl。

### 点击额度

`RedirectQuotaGuard` 只对有 `applicationId` 的短链生效：

- 通过 Platform 的 `ApplicationScopePort.findApplicationQuota()` 读取应用额度。
- `monthlyClickLimit <= 0` 表示不限制。
- 按 UTC 月初/月末调用 `ApplicationClickQuotaReservationPort.tryReserveMonthlyClick()`。
- 额度端口或查询失败时根据 `app.analytics.quota.fail-open` 决定放行还是拒绝。

Analytics 的 `RedisApplicationClickQuotaReservationPort` 使用 Redis Lua 原子预留：

- 若月度 Redis counter 已存在，直接在 limit 内 INCR。
- 若 counter 缺失，先从 MySQL 聚合统计查询基线 PV，再 seed 到 Redis 后预留。
- Redis 或基线查询失败时默认允许跳转，避免统计系统故障打断跳转。

## Analytics：访问采集、聚合、明细和报表

Analytics 接收 Redirect 的访问记录，写 Redis Stream，再由定时任务分别投影为聚合统计和访问明细。

### 访问事件写入

核心文件：`AnalyticsVisitEventService`、`RedisAnalyticsVisitEventAppender`、`VisitorFingerprint`、`VisitDimensionNormalizer`。

`AnalyticsVisitEventService` 实现 `VisitRecorderPort`：

- 从 `RedirectVisitRecord` 转成 `RedirectVisitEvent`。
- 调用 `AnalyticsVisitEventAppender.append()`。
- 如果 `analytics.events.fail-open=true` 或未配置，写入失败只记录 debug，不影响跳转主链路。

`RedisAnalyticsVisitEventAppender`：

- 按 occurredAtMillis 计算 UTC day。
- 规范化 Referer、语言、User-Agent、设备、UTM 等维度。
- 计算 visitorKey：`day|ip|ua|salt` 的 SHA-256，用于日 UV。
- 计算 ipHash：`ip|salt` 的 SHA-256，用于明细排障关联，不落明文 IP。
- 写入 Redis Stream `AnalyticsKeys.visitEventStreamKey()`。
- 可按配置近似 trim stream 最大长度。

`VisitDimensionNormalizer`：

- 无 Referer 记为 `direct`，无法解析记为 `unknown`。
- Accept-Language 取第一个语言 token，转小写并清理字符。
- User-Agent 简单归类为 browser、os、device；bot 单独归类。
- Tracking 只取 utm_source、utm_medium、utm_campaign，并过滤全符号等高噪音值。
- 所有输入都有长度上限，避免攻击者通过 header 放大内存/CPU。

### 聚合投影

核心文件：`AnalyticsRedirectEventProjectorJob`、`AnalyticsRedisAggregateWriter`、`AnalyticsFlushJob`、`AnalyticsDimensionFlushJob`。

`AnalyticsRedirectEventProjectorJob`：

- 消费访问 Redis Stream。
- 只投影带 visitorKey 的记录。
- 对每条记录调用 `AnalyticsRedisAggregateWriter.write()`。
- 成功后 ack；失败时先 ack 已处理记录并停止本轮，等待下轮重试。

`AnalyticsRedisAggregateWriter`：

- 对 link 日 PV 做 Redis string INCR。
- 对 link 日 UV 做 Redis HyperLogLog。
- 写 active link set，并往 dirty stream 写 member，供 flush job 落库。
- 同时维护 tenant/application/domain scope 的 UV HLL 和 dirty stream。
- 如果维度统计开启，对配置的维度类型写 PV hash 和 UV HLL，并写维度 dirty stream。
- 统计 key 可按 `redisKeyTtlDays` 设置过期时间。

`AnalyticsFlushJob`：

- 定时回刷最近 N 天，N 由 `flushBackfillDays` 和 Redis key TTL 限制。
- 消费 link stats dirty stream，把 Redis PV 和 HLL UV 批量 upsert 到 `link_stats_daily`。
- 消费 scope dirty stream，把 tenant/application/domain UV upsert 到 scope stats 表。
- 使用 ShedLock 防止多实例重复执行。

`AnalyticsDimensionFlushJob`：

- 仅在 dimensions enabled 时运行。
- 消费维度 dirty stream。
- 对每个 active link 和维度类型扫描 Redis hash，读取各维度 PV。
- 用 HLL 计算各维度 UV，批量 upsert 到 `link_stats_dim_daily`。

### 访问明细与留存

核心文件：`AnalyticsEventIngestJob`、`VisitEventBatchAssembler`、`VisitEventDeadLetterWriter`、`AnalyticsEventRetentionJob`。

`AnalyticsEventIngestJob`：

- 仅在 `analytics.events.enabled=true` 时运行。
- 创建并消费 Redis Stream consumer group。
- 优先处理当前 consumer pending，再按配置接管闲置 pending，最后读取新消息。
- `VisitEventBatchAssembler` 将 stream 字段转成 MySQL insert row；tenantId/linkId/requestId 不合法的记录直接 ack。
- 按 `sampleRate` 决定是否持久化访问明细；未被采样的记录直接 ack。
- 批量 insert ignore 成功后 ack。
- 数据完整性异常时逐条隔离 poison record，写 dead letter 后 ack poison，避免 stream 卡死。
- 普通数据库异常时保留 pending 等待重试。

`AnalyticsEventRetentionJob`：

- 按 `events.retentionDays` 分批删除历史访问明细。
- 单轮最多循环 20 次，每次 mapper 删除一批，避免大事务。

### 查询与导出审批

核心文件：`AnalyticsQueryServiceImpl`、`AnalyticsReportingApplicationService`、`AnalyticsLinkSummaryEnricher`、`AnalyticsLinkEventsService`、`AnalyticsExportRequestService`、`StatsController`。

查询能力：

- 链接日 PV/UV：`/api/v1/stats/links/{id}/daily`。
- 租户概览：`/api/v1/stats/overview`。
- 应用概览：`/api/v1/applications/{id}/stats/overview`。
- 域名概览：`/api/v1/stats/domains/{id}/overview`。
- Top 链接：支持按 PV/UV 排序，租户级、应用级、域名级。
- 链接维度统计：支持 referer_domain、language、ua_family、os_family、device_type、utm_source、utm_medium、utm_campaign。
- 链接访问明细：管理员可查，默认最近 1 天，最大 7 天，limit 1-200。

`AnalyticsReportingApplicationService` 会用 `AnalyticsLinkSummaryEnricher` 调 Shortlink 读端口补齐 Top 链接的短码、shortUrl、originalUrl；如果短链已不存在，返回 `deleted=true`。

访问明细导出不是直接下载：

- `AnalyticsExportRequestService.requestLinkEventExport()` 先用 Shortlink 读端口校验链接归属和应用归属。
- 默认导出时间为最近 1 天。
- 提交 `ANALYTICS_DETAIL_EXPORT` 审批请求到 Governance。

## Governance：审批和审计

Governance 管理敏感操作审批请求、审批决策和审计日志。

### 审批聚合

核心文件：`ApprovalRequest`、`GovernanceService`。

`ApprovalRequest` 规则：

- `approve()` 只允许 `PENDING_APPROVAL` 状态。
- 申请人不能审批自己的请求。
- 审批后状态为 `APPROVED`，记录审批人、原因和 decidedAt。
- `markExecuted()` 只允许 `APPROVED` 状态，执行后状态为 `EXECUTED`。

### 提交、审批和执行

`GovernanceService.submitRequest()`：

- 校验 actor 属于当前租户。
- 创建 `PENDING_APPROVAL` 请求，保存 operationType、targetApplicationId、before/after snapshot、申请人信息。
- 写 `SUBMIT_REQUEST` 审计日志。

`GovernanceService.approveRequest()`：

1. 校验 actor 和请求存在。
2. 调用领域 `approve()`，防止非 pending 和自审批。
3. 执行审批矩阵。
4. 使用 `markApprovedIfPending()` 抢占 pending 状态，避免并发审批。
5. 如果存在支持该 operation 的 `ApprovalExecutionPort`，立即执行具体业务操作。
6. 执行成功后用 `markExecutedIfApproved()` 更新状态。
7. 写 `APPROVE_REQUEST` 审计日志。
8. 回读最新审批结果返回。

审批矩阵：

- `TENANT_ADMIN` 和 `PLATFORM_ADMIN` 可审批一般请求。
- `EXTERNAL_DOMAIN_BINDING` 必须平台管理员审批。
- `APPLICATION_QUOTA_INCREASE` 如果请求的月发链额度超过 100000，必须平台管理员审批。

`GovernanceApprovalApplicationService` 实现跨上下文 `ApprovalSubmissionPort`：

- `requestLinkDestinationChangeApproval()` 提交公开短链目标地址变更审批，before/after snapshot 使用 `LinkDestinationChangeApprovalPayload.v1`。
- `requestAnalyticsDetailExportApproval()` 提交访问明细导出审批，after snapshot 使用 `AnalyticsDetailExportApprovalPayload.v1`。

当前已存在的执行器是 Shortlink 的 `LinkDestinationChangeApprovalExecutor`。访问明细导出审批目前只提交和记录审批，不在 Governance 内直接生成文件。

### HTTP 和审计

- `ApprovalController` 暴露 `/api/v1/approvals` 列表和 `/{requestId}/approve` 审批，要求租户管理员或平台管理员。
- `AuditController` 暴露 `/api/v1/audit-logs`，同样要求管理员角色。
- 审计日志记录 actor、actionType、resourceType、resourceId、requestId、beforeSnapshot、afterSnapshot、createdAt。

## Frontend：Vue 控制台核心逻辑

前端是 Vue 3 + Vite + Pinia，核心逻辑集中在 router、auth store、HTTP service、领域 services 和页面 composables。

### 路由与认证状态

核心文件：`web/src/router/index.ts`、`web/src/stores/auth.ts`、`web/src/services/http.ts`、`web/src/main.ts`。

路由：

- `/` 重定向 `/links`。
- `/overview`、`/applications`、`/domains`、`/api-keys` 需要 `TENANT_ADMIN`。
- `/approvals`、`/audit` 需要 `TENANT_ADMIN` 或 `PLATFORM_ADMIN`。
- `/links`、`/tags`、`/stats` 只需要登录。

路由守卫：

1. 每次跳转先 `auth.init()`。
2. 未登录访问需要认证的页面，跳到 `/login?redirect=...`。
3. 已登录访问 `/login`，租户管理员跳 `/overview`，普通用户跳 `/links`。
4. 权限不足访问管理员页面，回退 `/links`。

`auth` store：

- 支持 `VITE_AUTH_MODE=bearer|cookie`。
- Bearer 模式 token 来自 local/session storage，默认 session。
- Cookie 模式不保存 token，登录后靠服务端 HttpOnly cookie。
- `init()` 会调用 `/api/v1/me` 补齐用户、租户和角色，并用 `initInFlight` 避免并发初始化。
- `login()` 调 `/auth/login`，Bearer 模式保存 token，Cookie 模式清本地 token。
- `logout()` best-effort 调后端并总是清本地状态。

`http.ts`：

- `authFetch()` 自动加 Bearer token 或 Cookie credentials。
- Cookie 模式下，POST/PUT/DELETE/PATCH 会自动获取 `XSRF-TOKEN` cookie 并写 `X-XSRF-TOKEN` header。
- 401 会清 token 并通知 `main.ts` 注册的 unauthorized handler，跳回登录页。
- `apiFetch()` 统一解析 `ApiResponse`；非 2xx 抛异常并保留后端业务 message。

### 前端 services

services 只做 HTTP transport，不持有页面状态：

- `links.ts`：列表、创建、更新、归档、恢复、删除、CSV 导入/导出；应用级短链自动使用 `/applications/{id}/links` 路径。
- `applications.ts`：应用列表和创建。
- `domains.ts`：域名列表、应用可用域名、共享域名创建、应用专属域名创建、共享域名授权。
- `apiKeys.ts`：API Key 列表、创建、启用、禁用、轮换。
- `stats.ts`：概览、Top 链接、单链接日统计；应用级查询使用应用别名路径。
- `approvals.ts`：审批列表和审批动作。
- `audit.ts`：审计日志列表。

### 短链页面组合逻辑

核心文件：`useLinksPage`、`useLinkMutations`、`useLinkImportExport`、`linkFormCodec`、`LinksView.vue`。

`useLinksPage`：

- 管理 loading/error/items/page/size/total、筛选条件、应用和域名选择、创建和编辑表单。
- 租户管理员加载应用列表，并在选择应用后加载该应用可用域名。
- 列表查询会把 selectedApplicationId 转成应用级路径。
- 导入时如果选择了应用，必须选择域名。

`useLinkMutations`：

- 创建时从表单构造 payload，若当前选择应用，则注入 applicationId/domainId。
- 启用/禁用前检查短链未归档。
- 编辑前检查未归档。
- 保存编辑要求 originalUrl 非空。
- 删除前要求短链已归档，并做浏览器确认。

`linkFormCodec`：

- 标签和 query allowlist 支持逗号或换行分隔，去重。
- 标签最多 20 个。
- `datetime-local` 转 ISO instant 传给后端。
- 空过期时间转 `clearExpiresAt=true`。
- 空 redirect status 转 `clearRedirectStatusCode=true`。
- 空 query forward mode 转 `clearQueryForwardMode=true`。
- `policySummary()` 汇总跳转状态码、query 转发模式和 preview 状态。

CSV 导入导出：

- 导入使用 `FormData` 和 `authFetch()`，不强制 JSON content-type。
- 导出拿 Blob，创建临时 a 标签下载 `links.csv`。

### 报表页面组合逻辑

核心文件：`useStatsPage`、`StatsView.vue`。

`useStatsPage`：

- 默认 7 天范围，也支持 30 天；日期按 UTC 生成 `yyyy-mm-dd`。
- 租户管理员可选择应用范围；选择后刷新链接候选、概览和 Top 链接。
- 链接候选会分页拉取直到取完。
- 概览、Top 链接、单链接日统计并行加载。
- Top 链接支持按 PV/UV 切换。
- 支持复制 shortUrl，失败静默。

### 控制面页面组合逻辑

- `useApplicationsPage`：加载和创建应用；创建成功清空表单并刷新。
- `useApplicationDetailPage`：按 route applicationId 并行加载应用列表、API Keys、应用概览、Top 链接、应用域名，计算最近 7 天 PV。
- `useDomainsPage`：加载应用和域名，创建共享/专属域名，授权共享域名给应用；共享域名通过 computed 过滤。
- `useApiKeysPage`：围绕 API Key 列表、创建、启用、禁用、轮换组织页面状态；新建和轮换返回的明文 key 只在响应时展示。
- `useApprovalsPage`：加载审批列表，按 requestId 保存审批原因，审批成功后清理原因并刷新。
- `useAuditPage`：加载审计日志。
- `useAppSessionNavigation`：控制页面 shell 的登出和导航状态。

## 关键跨上下文业务链路

### 用户登录后管理短链

1. 前端登录调用 `/api/v1/auth/login`。
2. Accounts 校验用户、租户、密码和角色，签发 JWT。
3. 前端保存 token 或使用 Cookie 会话。
4. 用户进入 `/links`，router guard 调 `/api/v1/me` 补齐角色。
5. `useLinksPage` 调 `listLinks()` 查询短链。
6. 创建短链时，Shortlink 根据 actor 和路径/body 解析作用域。
7. 应用级短链调用 Platform 校验应用和域名授权，并预留发链额度。
8. Shortlink 写库、写标签、发布事件、提交后驱逐 Redirect 缓存。

### OpenAPI 创建和查询短链

1. 客户端请求 `/api/v1/open/**`，带 `X-API-Key`。
2. `ApiKeyAuthenticationFilter` 调 Accounts 校验 key 格式、secret、状态。
3. 认证成功后建立 `OPENAPI` principal 和 `ApiKeyAuthenticationDetails`。
4. `OpenApiShortLinkController` 用 `PrincipalActorMapper.requireApiKey()` 生成 `ApiKeyActor`。
5. `ShortLinkActorScopeResolver` 限制 API Key 只能访问其绑定应用；未绑定应用的历史 key 会被拒绝。
6. 后续创建/查询复用 Shortlink application 用例。

### 跳转与统计

1. 浏览器请求 `/r/{code}`。
2. `RequestIdFilter` 设置 requestId。
3. `RedirectRiskControlFilter` 解析 IP 和 VisitInfo，执行风控。
4. `RedirectService` 按 host + code 查 Redis，未命中回源 Shortlink 读端口。
5. 如果短链禁用、非 active、过期或应用点击额度耗尽，返回不可用响应。
6. 如果 HTML 且 preview enabled，先返回预览页，确认后才继续。
7. 真正跳转前写 `VisitRecorderPort.recordVisit()`。
8. Analytics 把访问事件写入 Redis Stream。
9. 聚合作业写 Redis PV/UV/维度，再定时 flush 到 MySQL。
10. 可选明细入库作业按采样率写 `link_visit_events`。

### 目标地址变更审批

1. 用户更新应用级短链 originalUrl。
2. Shortlink 判断这是公开短链目标地址变更，且不能同时包含其他有效变更。
3. Shortlink 通过 `ApprovalSubmissionPort` 向 Governance 提交 `PUBLIC_LINK_DESTINATION_CHANGE` 审批。
4. 审批人通过 `/api/v1/approvals/{id}/approve` 审批。
5. Governance 防止自审批，执行审批矩阵，并查找支持该操作的 `ApprovalExecutionPort`。
6. `LinkDestinationChangeApprovalExecutor` 校验短链当前状态和 before snapshot 仍匹配。
7. 执行目标地址更新，发布事件并提交后驱逐跳转缓存。
8. Governance 标记请求 executed 并写审计日志。

### 访问明细导出审批

1. 用户在统计接口发起链接事件导出请求。
2. Analytics 校验 link 属于当前租户，并可选校验属于路径 applicationId。
3. 默认时间范围是最近 1 天；from 不能晚于 to。
4. Analytics 向 Governance 提交 `ANALYTICS_DETAIL_EXPORT` 审批。
5. Governance 记录审批请求和审计。当前代码只完成审批编排，未实现审批通过后的文件导出执行器。

### 短链事件投影到 Analytics 目录

1. Shortlink 创建/更新/归档/恢复/删除短链时，聚合记录领域事件。
2. Application handler 发布事件，`ShortLinkEventAppender` 写集成事件表。
3. `ShortLinkCatalogProjectorJob` 读取 `shortlink` producer 的事件。
4. Projector 将 snapshot upsert 到 analytics link catalog；删除事件标记 deleted。
5. 消费 checkpoint 更新；不可解析事件写 dead letter 并跳过，数据库瞬时异常则停止本轮等待重试。

## 代码入口索引

后端入口和横切：

- `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- `server/app/src/main/java/com/linkforge/app/security/*SecurityConfig.java`
- `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- `server/foundation/runtime-web/src/main/java/com/linkforge/foundation/runtime/web/RequestIdFilter.java`
- `server/foundation/runtime-tx/src/main/java/com/linkforge/foundation/runtime/tx/AfterCommit.java`
- `server/foundation/runtime-persistence/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationEventStore.java`

业务上下文：

- Accounts: `server/accounts/application/src/main/java/com/linkforge/accounts/application/*.java`
- Platform: `server/platform/application/src/main/java/com/linkforge/platform/application/*.java` and `server/platform/domain/src/main/java/com/linkforge/platform/domain/*.java`
- Shortlink: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/*.java`, `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/**/*.java`
- Redirect: `server/redirect/application/src/main/java/com/linkforge/redirect/application/**/*.java`, `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/*.java`
- Analytics: `server/analytics/application/src/main/java/com/linkforge/analytics/application/*.java`, `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/**/*.java`
- Governance: `server/governance/domain/src/main/java/com/linkforge/governance/domain/*.java`, `server/governance/application/src/main/java/com/linkforge/governance/application/*.java`

前端入口：

- `web/src/router/index.ts`
- `web/src/stores/auth.ts`
- `web/src/services/*.ts`
- `web/src/composables/*.ts`
- `web/src/composables/links/*.ts`
- `web/src/views/*.vue`

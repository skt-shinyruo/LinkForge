# Foundation 与关键配置

本文是 LinkForge 横切运行时能力和关键配置的权威说明。具体业务规则仍以各上下文专题为准；本页只维护 ID、请求关联、认证主体、事务后动作、集成事件、启动门禁以及配置默认值。

## 组合根与配置来源

`server/app` 是唯一可执行组合根。`LinkForgeApplication` 显式导入各上下文 runtime module，配置默认值集中在 `server/app/src/main/resources/application.yml`，Java 配置模型位于 `server/foundation/core`。

配置优先级遵循 Spring Boot：命令行和系统属性高于环境变量，环境变量高于 YAML 默认值。本文表格中的环境变量是仓库默认部署接口；测试也可能直接设置对应的点分属性。

`prod` profile 或 `app.strict-config=true` 会启用严格启动校验。启动器聚合所有 `StartupCheck` 的错误后一次性失败，避免只修复第一项后反复重启。

## Snowflake ID

`SnowflakeIdGenerator` 生成有符号 Java `long` 内的 64-bit ID：

- epoch 固定为 `2024-01-01T00:00:00Z`。
- 时间戳之后依次是 5-bit `datacenterId`、5-bit `workerId` 和 12-bit 毫秒内序列。
- 两个节点值都必须在 `0..31`；同一毫秒序列用尽时同步等待下一毫秒。
- 发现时钟回拨时会同步等待超过上次时间戳。较大回拨会阻塞请求，应同时排查宿主机时钟。
- 节点号来自 `app.id.*` 配置。无参构造器的 `1/1` 仅用于直接构造或兼容代码，不是生产节点发现机制。

| 配置 | 环境变量 | 默认值 | 约束与语义 |
| --- | --- | ---: | --- |
| `app.id.worker-id` | `ID_WORKER_ID` | `1` | `0..31`；多实例必须唯一组合 |
| `app.id.datacenter-id` | `ID_DATACENTER_ID` | `1` | `0..31`；多实例必须唯一组合 |

严格模式拒绝同时使用默认 `1/1`，因为两个实例使用相同节点组合会产生主键冲突或错写。

## 请求 ID 与错误关联

`RequestIdFilter` 在所有 Servlet 请求最前部处理 `X-Request-Id`：

- 调用方值会先 trim，只接受 1 到 64 个 ASCII 字母、数字、`-`、`_`、`.`。
- 缺失或非法时生成无连字符的 UUID；不会把不可信头原样写入日志。
- 有效值进入 MDC、线程内 `RequestId`、响应 `X-Request-Id`，API 错误还会写入 `ApiResponse.requestId`。
- filter 在 `finally` 中清理线程状态，避免线程池复用造成跨请求污染。

`GlobalExceptionHandler` 负责 `/api/**` 的统一 JSON 错误；Redirect 使用独立异常处理和响应 writer。未处理异常只向客户端返回安全消息和 requestId，完整堆栈留在服务日志。

## 认证主体边界

`AuthPrincipal` 是应用内认证主体快照。`AuthContext.requirePrincipal()` 同时要求：

1. `SecurityContext` 中存在 `Authentication`；
2. `Authentication.isAuthenticated()` 为 `true`；
3. principal 的运行时类型是 `AuthPrincipal`。

任一条件不满足都抛出 `UNAUTHORIZED`。应用服务不应主动读取 `AuthContext`；HTTP adapter 应先把 tenant、user、role 或 API Key actor 显式映射成用例输入。

JWT 用户主体携带 `tokenVersion`，每次受保护请求由 Accounts 复核租户、用户状态和当前版本。API Key 的 `applicationId` 保存在 `Authentication.details`，不会混入用户主体字段。

## 事务后动作

`PostCommitHookPort` 的 Spring 实现委托给 `AfterCommit`：

- 存在活跃事务时，动作只在事务成功提交后运行。
- 回滚时动作不运行。
- 没有事务同步上下文时立即运行，因此调用方不能假定一定异步。
- 动作通常是 Redis 驱逐或其他可重试快速路径，必须允许重复执行，并自行处理异常。

需要可靠恢复的副作用不能只依赖 after-commit 回调。Shortlink 缓存失效同时在业务事务内写 durable outbox，再由 after-commit 尝试快速驱逐；worker 会重试 outbox。

## 集成事件存储

`IntegrationEventStore` 是单体内的 append-only 发布日志接口：

- `append` 保存稳定 `eventId`、producer、eventType、tenant/aggregate 元数据、UTC `occurredAt` 和 JSON payload。
- `listAfterSeq*` 以数据库递增 `seq` 做游标，参数语义是 `lastSeqExclusive`。
- producer 过滤用于消费者只读取拥有的事件族；limit 必须由调用方设置有界值。
- 事件随业务事务一起提交时，回滚不会留下事件；store 本身不提供消息代理级 exactly-once。
- 消费者可能在提交消费位点前重放，必须按事件 ID 或业务幂等键处理；未知版本应显式跳过、告警或停住游标，不能猜测字段。

不要修改已应用 Flyway migration 来“补注释”；历史 schema 与迁移语义在文档中说明，新的结构变化使用新 migration。

## 基础与安全配置

| 配置 | 环境变量 | 默认值 | 单位与行为 |
| --- | --- | --- | --- |
| `app.base-url` | `APP_BASE_URL` | `http://localhost:8080` | 拼接公开 shortUrl；启动时不能为空 |
| `app.strict-config` | `APP_STRICT_CONFIG` | `false` | `true` 启用生产级启动门禁 |
| `app.scheduling.enabled` | 无固定变量名 | `true` | 关闭后不注册统一调度能力，常用于测试 |
| `app.shortlink.write-enabled` | 无固定变量名 | `true` | `false` 时写接口返回维护中；读取不受影响 |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | 空 | 不携带凭据时空值允许开发期 origin pattern；生产应显式列举 |
| `app.cors.allow-credentials` | `CORS_ALLOW_CREDENTIALS` | `false` | `true` 时必须显式白名单且禁止 `*` |
| `app.security.registration-enabled` | `AUTH_REGISTRATION_ENABLED` | `false` | 是否开放自助注册 |
| `app.security.jwt.secret` | `JWT_SECRET` | 开发示例值 | 至少 32 bytes；strict 拒绝示例值 |
| `app.security.jwt.issuer` | `JWT_ISSUER` | `linkforge` | 签发和验证必须一致 |
| `app.security.jwt.ttl-seconds` | `JWT_TTL_SECONDS` | `43200` | JWT 有效期，秒 |
| `app.security.jwt.cookie-enabled` | `JWT_COOKIE_ENABLED` | `false` | 启用 HttpOnly Cookie 与 CSRF 双提交模式 |
| `app.security.jwt.cookie-name` | `JWT_COOKIE_NAME` | `lf_token` | Cookie 名；启用 cookie 时不能为空 |
| `app.security.jwt.cookie-secure` | `JWT_COOKIE_SECURE` | `false` | strict cookie 模式必须为 `true` |
| `app.security.jwt.cookie-same-site` | `JWT_COOKIE_SAMESITE` | `Lax` | 仅 `Lax/Strict/None`；`None` 要求 Secure |
| `app.security.api-key.last-used-update-interval-seconds` | `API_KEY_LAST_USED_UPDATE_INTERVAL_SECONDS` | `300` | 秒；`0` 关闭 `lastUsedAt` 写回，负值按实现的非正分支处理 |
| `app.security.api-key.auth-cache-ttl-seconds` | `API_KEY_AUTH_CACHE_TTL_SECONDS` | `60` | 秒；只缓存 disabled 短路状态，`0` 关闭 |

API Key active 请求仍会回库并校验 secret hash；缓存不是 active 凭据的授权事实源。

## Redirect 与 Edge 配置

| 配置 | 环境变量 | 默认值 | 单位与行为 |
| --- | --- | ---: | --- |
| `app.redirect.cache-ttl-seconds` | `REDIRECT_CACHE_TTL_SECONDS` | `3600` | 正缓存秒数；必须 `>0` |
| `app.redirect.not-found-cache-ttl-seconds` | `REDIRECT_NOT_FOUND_CACHE_TTL_SECONDS` | `60` | 负缓存秒数；`0` 关闭，不能为负 |
| `app.redirect.default-status-code` | `REDIRECT_DEFAULT_STATUS_CODE` | `302` | 只允许 `301/302` |
| `app.redirect.not-found-landing-url` | `REDIRECT_NOT_FOUND_LANDING_URL` | 空 | 可选 http(s) 404 落地页 |
| `app.redirect.gone-landing-url` | `REDIRECT_GONE_LANDING_URL` | 空 | 可选 http(s) 410 落地页 |
| `app.redirect.query-forward-mode` | `REDIRECT_QUERY_FORWARD_MODE` | 空，即 `OFF` | `OFF/ALLOWLIST/ALL` |
| `app.redirect.query-forward-allowlist` | `REDIRECT_QUERY_FORWARD_ALLOWLIST` | 空 | 逗号列表，允许前缀模式如 `utm_*` |
| `app.redirect.query-forward-reserved-params` | `REDIRECT_QUERY_FORWARD_RESERVED_PARAMS` | 空 | 永不转发的内部参数 |
| `app.edge.trusted-proxies` | `EDGE_TRUSTED_PROXIES` | 空 | CIDR 列表；仅远端命中时读取转发头 |
| `app.edge.risk-control.enabled` | `EDGE_RISK_ENABLED` | `false` | 风控总开关 |
| `...rate-limit.window-seconds` | `EDGE_RISK_RATE_LIMIT_WINDOW_SECONDS` | `60` | 窗口秒数，启用时必须 `>0` |
| `...rate-limit.enabled` | `EDGE_RISK_RATE_LIMIT_ENABLED` | `false` | 风控总开关开启后才生效；`false` 时不执行 Redis 限流 |
| `...rate-limit.ip-max-requests` | `EDGE_RISK_RATE_LIMIT_IP_MAX` | `120` | 每 IP 上限；`0` 表示任何命中请求都会超限 |
| `...rate-limit.ip-code-enabled` | `EDGE_RISK_RATE_LIMIT_IP_CODE_ENABLED` | `false` | `true` 时再叠加 IP + short code 固定窗口；`false` 时只检查 IP 窗口 |
| `...rate-limit.ip-code-max-requests` | `EDGE_RISK_RATE_LIMIT_IP_CODE_MAX` | `60` | IP+code 上限 |
| `...rate-limit.fail-open` | `EDGE_RISK_RATE_LIMIT_FAIL_OPEN` | `true` | Redis 限流故障是否放行 |
| `...ip-allowlist` | `EDGE_RISK_IP_ALLOWLIST` | 空 | CIDR 逗号列表；非空时，未命中请求拒绝，且 denylist 仍优先 |
| `...ip-denylist` | `EDGE_RISK_IP_DENYLIST` | 空 | CIDR 逗号列表；命中即拒绝，优先于 allowlist |
| `...bot.enabled` | `EDGE_RISK_BOT_ENABLED` | `false` | 关闭后不按 User-Agent 识别 bot；仅风险总开关也开启时参与判定 |
| `...bot.user-agent-keywords` | `EDGE_RISK_BOT_UA_KEYWORDS` | `bot,crawler,spider,curl,wget,python-requests,httpclient` | 大小写不敏感关键词列表；空值也回退此安全默认集合 |
| `...bot.ip-max-requests` | `EDGE_RISK_BOT_IP_MAX` | `30` | bot 单 IP 上限 |
| `...bot.block` | `EDGE_RISK_BOT_BLOCK` | `false` | 识别 bot 后是否直接拒绝 |

以上 `...` 都是 `app.edge.risk-control` 前缀。风险控制总开关关闭时名单、bot 和限流子项均不执行；启用后决策顺序固定为 denylist、非空 allowlist、bot 直接阻断、IP 限流、IP+code 限流。CIDR 格式在启动时校验。

可信代理不是“开启后信任所有头”。当 `remoteAddr` 不在列表中，`X-Real-IP`、`X-Forwarded-For` 等值不会参与客户端 IP 选择。

## Analytics 配置

| 配置 | 环境变量 | 默认值 | 单位与行为 |
| --- | --- | ---: | --- |
| `app.analytics.salt` | `ANALYTICS_SALT` | 开发示例值 | 访客指纹/IP hash 盐；不能为空，strict 拒绝示例值 |
| `app.analytics.redis-key-ttl-days` | `ANALYTICS_REDIS_TTL_DAYS` | `45` | 聚合 key TTL 天数，必须 `>0` |
| `app.analytics.flush-backfill-days` | `ANALYTICS_FLUSH_BACKFILL_DAYS` | `7` | 含今天的回补天数，非正值运行时收敛为 1，并受 TTL 截断 |
| `app.analytics.tracking-param-allowlist` | `ANALYTICS_TRACKING_PARAM_ALLOWLIST` | 空 | 仅控制统计采集，不改变 Redirect query 透传；空值回退安全集合 `utm_*`,`gclid`,`fbclid`，避免把任意 query 送入统计链路 |
| `app.analytics.visit-stream.max-len` | `ANALYTICS_VISIT_STREAM_MAX_LEN` | 空 | 空时回退 events stream max len；`<=0` 不 trim |
| `app.analytics.dimensions.enabled` | `ANALYTICS_DIMENSIONS_ENABLED` | `false` | 维度 Redis 写入和落库开关 |
| `app.analytics.dimensions.types` | `ANALYTICS_DIMENSIONS_TYPES` | 空 | 空时使用实现内推荐集合 |
| `app.analytics.events.enabled` | `ANALYTICS_EVENTS_ENABLED` | `false` | 仅控制访问明细落库，不关闭基础 visit stream/PV/UV/额度 |
| `app.analytics.events.sample-rate` | `ANALYTICS_EVENTS_SAMPLE_RATE` | `0.1` | 仅明细采样，启用时要求 `0..1` |
| `app.analytics.events.stream-max-len` | `ANALYTICS_EVENTS_STREAM_MAX_LEN` | `200000` | 兼容 fallback；`<=0` 不 trim |
| `app.analytics.events.retention-days` | `ANALYTICS_EVENTS_RETENTION_DAYS` | `14` | 明细保留天数；`<=0` 不清理 |
| `app.analytics.events.consumer-name` | `ANALYTICS_EVENTS_CONSUMER_NAME` | 空 | 多实例应配置稳定且不同的 consumer 名 |
| `app.analytics.events.pending-reclaim-enabled` | `ANALYTICS_EVENTS_PENDING_RECLAIM_ENABLED` | `true` | 是否接管闲置 pending |
| `app.analytics.events.pending-reclaim-min-idle-ms` | `ANALYTICS_EVENTS_PENDING_RECLAIM_MIN_IDLE_MS` | `60000` | reclaim 最小闲置毫秒 |
| `app.analytics.events.pending-reclaim-count` | `ANALYTICS_EVENTS_PENDING_RECLAIM_COUNT` | `200` | 单批接管上限 |
| `app.analytics.events.max-user-agent-length` | `APP_ANALYTICS_EVENTS_MAX_USER_AGENT_LENGTH` | `512` | UA 写入 Stream/明细前截断；非正值回退 `512`，最大强制收敛为 `2048` |
| `app.analytics.events.max-tracking-value-length` | `APP_ANALYTICS_EVENTS_MAX_TRACKING_VALUE_LENGTH` | `128` | 单个已允许 tracking 值写入前截断；非正值回退 `128`，最大强制收敛为 `512` |
| `app.analytics.events.fail-open` | 无 YAML 显式项 | `true` | 明细写入异常是否向访问链路外抛 |
| `app.analytics.quota.fail-open` | `ANALYTICS_QUOTA_FAIL_OPEN` | `false` | Redirect 外层在 quota 查询/调用异常时是否放行 |
| `app.analytics.quota.lookup-cache-ttl-seconds` | 无 YAML 显式项 | `30` | Platform quota 查询缓存秒数 |

点击额度 Redis adapter 内部基础设施故障固定 fail-open；`quota.fail-open` 只影响 adapter 之外仍被抛出的 Platform 查询或调用异常，不能把它理解为控制所有 Redis 错误。

## 调度间隔

以下是实现直接读取的毫秒环境变量，未配置时使用括号内默认值：

- `APP_SHORTLINK_REDIRECT_CACHE_INVALIDATION_OUTBOX_DELAY_MS`（`1000`）
- `APP_ANALYTICS_REDIRECT_EVENT_PROJECTOR_DELAY_MS`（`2000`）
- `APP_ANALYTICS_SHORTLINK_CATALOG_PROJECTOR_DELAY_MS`（`2000`）
- `APP_ANALYTICS_EVENT_INGEST_DELAY_MS`（`2000`）
- `APP_ANALYTICS_FLUSH_DELAY_MS`（`60000`）
- `APP_ANALYTICS_DIM_FLUSH_DELAY_MS`（`60000`）
- `APP_ANALYTICS_EVENT_RETENTION_DELAY_MS`（`3600000`）

调度间隔不是交付时限保证。ShedLock、consumer group pending、数据库或 Redis 故障都会增加延迟；排障时应同时检查锁、stream lag/pending、outbox attempt 和应用日志。

## 启动门禁清单

启动至少校验：

- Snowflake 节点范围与 strict 默认节点冲突风险；
- `app.base-url` 非空；
- JWT secret、Cookie `SameSite/Secure`、credentialed CORS 白名单；
- Redirect TTL、状态码、落地页 URL、query 参数模式和 Edge CIDR；
- Analytics salt、Redis TTL、追踪参数、维度类型和明细采样边界。

启动校验通过只代表静态配置组合合法，不代表 Redis/MySQL 可用、代理链部署正确或外部 URL 可达。

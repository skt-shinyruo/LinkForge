# Foundation 与关键配置

`server/app` 是唯一可执行组合根，默认配置在 `server/app/src/main/resources/application.yml`，横切配置模型在
`server/foundation/core`。Spring Boot 的命令行/系统属性优先于环境变量，环境变量优先于 YAML 默认值。

`prod` profile 或 `app.strict-config=true` 会聚合所有启动检查并一次性拒绝非法配置。启动检查只验证静态边界，
不代表 Redis、MySQL、代理链或外部 URL 可用。

## 基础与安全

| 配置 | 环境变量 | 默认值 | 约束 |
| --- | --- | --- | --- |
| `app.base-url` | `APP_BASE_URL` | `http://localhost:8080` | 公开 short URL 基址，不能为空 |
| `app.strict-config` | `APP_STRICT_CONFIG` | `false` | 开启生产级门禁 |
| `app.scheduling.enabled` | - | `true` | 关闭所有 `@Scheduled` 作业，常用于测试 |
| `app.id.worker-id` | `ID_WORKER_ID` | `1` | Snowflake `0..31` |
| `app.id.datacenter-id` | `ID_DATACENTER_ID` | `1` | Snowflake `0..31` |
| `app.security.jwt.secret` | `JWT_SECRET` | 开发示例值 | 至少 32 bytes；strict 拒绝示例值 |
| `app.security.jwt.issuer` | `JWT_ISSUER` | `linkforge` | 签发和验证一致 |
| `app.security.jwt.ttl-seconds` | `JWT_TTL_SECONDS` | `43200` | JWT 有效期（秒） |
| `app.security.registration-enabled` | `AUTH_REGISTRATION_ENABLED` | `false` | 是否开放自助注册 |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | 空 | 生产应显式列举 |
| `app.cors.allow-credentials` | `CORS_ALLOW_CREDENTIALS` | `false` | 凭据模式禁止 `*` |

API Key 使用 current/previous/legacy pepper 轮换，摘要不会进入日志或响应；具体滚动步骤见
[部署 runbook](../../deploy/README.md)。认证缓存不是 active 凭据的事实源，每次 active 请求仍需验证摘要。

## Redirect 与 Edge

| 配置 | 环境变量 | 默认值 | 约束 |
| --- | --- | --- | --- |
| `app.redirect.cache-ttl-seconds` | `REDIRECT_CACHE_TTL_SECONDS` | `3600` | `>0` |
| `app.redirect.not-found-cache-ttl-seconds` | `REDIRECT_NOT_FOUND_CACHE_TTL_SECONDS` | `60` | `>=0` |
| `app.redirect.default-status-code` | `REDIRECT_DEFAULT_STATUS_CODE` | `302` | 仅 `301/302` |
| `app.redirect.not-found-landing-url` | `REDIRECT_NOT_FOUND_LANDING_URL` | 空 | 可选 http(s) URL |
| `app.redirect.gone-landing-url` | `REDIRECT_GONE_LANDING_URL` | 空 | 可选 http(s) URL |
| `app.redirect.query-forward-mode` | `REDIRECT_QUERY_FORWARD_MODE` | `OFF` | `OFF/ALLOWLIST/ALL` |
| `app.redirect.query-forward-allowlist` | `REDIRECT_QUERY_FORWARD_ALLOWLIST` | 空 | 字母数字下划线或末尾 `*` |
| `app.redirect.query-forward-reserved-params` | `REDIRECT_QUERY_FORWARD_RESERVED_PARAMS` | 空 | 永不转发的参数 |
| `app.edge.trusted-proxies` | `EDGE_TRUSTED_PROXIES` | 空 | CIDR；仅可信代理可提供转发头 |
| `app.edge.risk-control.enabled` | `EDGE_RISK_ENABLED` | `false` | 风控总开关 |
| `app.edge.risk-control.rate-limit.fail-open` | `EDGE_RISK_RATE_LIMIT_FAIL_OPEN` | `true` | 限流 Redis 故障是否放行 |

风险控制顺序固定为 denylist、非空 allowlist、bot、IP 限流和 IP+code 限流；名单与 CIDR 在启动时校验。

## Analytics

基础统计是 Redirect 成功后的同步 Redis 原子聚合，只有下面四项配置：

| 配置 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `app.analytics.salt` | `ANALYTICS_SALT` | 开发示例值 | 日访客指纹盐；生产必填 |
| `app.analytics.redis-key-ttl-days` | `ANALYTICS_REDIS_TTL_DAYS` | `45` | Redis PV/UV/marker TTL，必须 `>0` |
| `app.analytics.flush-backfill-days` | `ANALYTICS_FLUSH_BACKFILL_DAYS` | `7` | flush 包含今天的回补天数 |
| `app.analytics.quota.fail-open` | `ANALYTICS_QUOTA_FAIL_OPEN` | `false` | Platform quota 查询异常是否放行 |
| `app.analytics.quota.lookup-cache-ttl-seconds` | `ANALYTICS_QUOTA_LOOKUP_CACHE_TTL_SECONDS` | `30` | Redirect quota 查询缓存秒数 |
| `app.analytics.quota.lookup-cache-max-entries` | `ANALYTICS_QUOTA_LOOKUP_CACHE_MAX_ENTRIES` | `10000` | Redirect quota 查询缓存上限 |
| `app.analytics.events.fail-open` | `ANALYTICS_EVENTS_FAIL_OPEN` | `true` | Redis 聚合失败是否放行跳转 |

`AnalyticsFlushJob` 读取 V2 generation marker，把 PV/UV upsert 到链接和 scope 日表；短链目录投影仍保留，因为链接
可能物理删除。

## 调度

实现直接读取的间隔环境变量：

- `APP_SHORTLINK_REDIRECT_CACHE_INVALIDATION_OUTBOX_DELAY_MS`（`1000`）
- `APP_ANALYTICS_SHORTLINK_CATALOG_PROJECTOR_DELAY_MS`（`2000`）
- `APP_ANALYTICS_FLUSH_DELAY_MS`（`60000`）

ShedLock、数据库或 Redis 故障会增加最终一致延迟；间隔不是交付时限保证。

## 启动门禁

- Snowflake 节点范围及 strict 默认节点冲突；
- base URL、JWT secret、Cookie/CORS 组合；
- Redirect 状态码、TTL、landing URL、query 模式和 Edge CIDR；
- Analytics salt 与 Redis TTL。

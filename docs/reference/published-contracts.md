# 发布契约

本文是 `server/contracts/*` 以及少量稳定 wire format 的权威说明。契约模块用于同一部署中的限界上下文协作，不等同于承诺对外发布 Maven API；修改时仍需考虑数据库历史消息、Redis 存量数据和滚动升级兼容。

## 通用约定

- 标识符使用 Java `long`，有效业务 ID 通常必须大于 0。
- 带 `Utc` 后缀的 `Instant` 或 `LocalDateTime` 按 UTC 解释。`LocalDateTime` 本身不携带时区，调用方不得按 JVM 本地时区重解释。
- `null` 只有在类型 Javadoc 明确允许时才表示缺省、历史数据或“不改变”；不能用空字符串代替 null 协议。
- `BusinessException` 携带稳定数字错误码和安全消息，由 HTTP adapter 映射状态码。跨上下文调用方应优先按错误码分支，不解析消息文本。
- `Optional.empty()`、缺失 Map entry 和 `null` 的语义必须以各端口的 Javadoc 为准。它们常用于隐藏跨租户资源，但额度缺行、未实现的可选反查等也有专门含义。
- 集成事件由 `integration_events.event_type`（例如 `shortlink.ShortLinkCreated.v1`）识别 schema；审批的结构化 JSON payload 才在内容内携带 `type/version`。新增字段和版本时必须考虑旧读者。
- 除明确说明外，跨上下文调用不是 exactly-once。命令重试、consumer reclaim 和事务提交前崩溃都可能造成重复观察。

## API 响应与错误

`ApiResponse<T>` 的字段名稳定为：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "..."
}
```

`code` 和 `message` 是响应信封字段；`data` 与 `requestId` 在值为 `null` 时会因 `@JsonInclude(NON_NULL)` 省略，
不能把四个字段都存在当作 wire 前提。成功时 `code=0`，失败时 `data` 通常省略。`requestId` 出现时与响应头
`X-Request-Id` 对应。错误消息不得包含密钥、token、SQL、堆栈或内部主机信息。

`ErrorCode`、`AccountsErrorCode`、`OpenApiErrorCode` 和 `ShortLinkErrorCode` 是稳定的业务分支语言。枚举名便于代码阅读，客户端契约以数字 code 和 HTTP 状态组合为准。

## Platform 发布端口

### `ApplicationScopePort`

- `requireApplicationExists(tenantId, applicationId)` 要求租户内应用存在且为 ACTIVE。
- `requireApplicationAndDomainAuthorized(...)` 同时要求应用、域名 ACTIVE 且满足专属/共享授权规则。
- `findApplicationQuota(...)` 先要求目标应用在当前租户内存在且 ACTIVE；它抛出不存在、禁用或跨租户错误。仅 ACTIVE
  应用缺少 quota 行时返回空，调用方按“不限制”处理；`ApplicationQuotaView` 的两个 primitive limit 非正数也表示对应维度不限制。

### `DomainHostnameLookupPort`

`findDomainHostname` 在租户范围内把 domainId 映射到规范化 hostname。`findDomainIdByHostname` 的默认实现可以不支持反查并返回空，调用方必须处理这一能力差异。

### `LegacyApplicationProvisioningPort`

为历史未分应用数据返回默认 application/domain 绑定。实现目标是幂等，但并发唯一键冲突仍可能以业务异常暴露；新业务流程不应依赖 legacy provisioning。

## Shortlink 发布端口

`ShortLinkReadPort` 是短链事实的唯一跨上下文权威读入口：

- `findRedirectMetaByHostAndCode(host, code)` 按规范化 host 和大小写敏感 code 查询跳转元数据。host 为空时进入 legacy/unscoped 兼容路径。
- `findOwnership(tenantId, linkId)` 只返回当前租户可见的 application/domain 归属。
- `listSummaries(tenantId, linkIds)` 只返回存在的条目；结果 Map 中缺失 ID 不应补造默认链接。

`ShortLinkReadPort.RedirectLinkView.expiresAtUtc` 使用 `Instant`。read port 返回事实快照，不替 Redirect 判定 enabled、生命周期、过期、预览、额度或风险策略。

## Redirect 缓存契约

`LinkCachePort.LookupResult` 有三态：

| 状态 | 含义 | Redirect 行为 |
| --- | --- | --- |
| `HIT` | 正缓存，包含 `LinkMeta` | 直接进入可用性决策 |
| `NEGATIVE` | 已确认权威不存在 | 不回源，返回未找到 |
| `MISS` | 无缓存或缓存不可用/坏值 | 调用 `ShortLinkReadPort` 回源 |

缓存故障必须降级为 `MISS`，不能把 Redis 故障伪装成 `NEGATIVE`。正/负写入和驱逐都是优化副作用，应允许失败与重复执行。Shortlink 事务内 durable outbox 才提供最终重试路径。

`LookupResult` 工厂方法表达三态；公开 canonical constructor 为 Java 兼容保留，直接调用它的生产者也必须维持
`meta` 与 `notFound` 不同时出现的三态不变量。
缓存 key 分为 legacy code 和 host-scoped code 两族；code 保持大小写。`LinkCachePort` 的 host 重载默认实现为了旧实现兼容而忽略 `host` 并委派无 host 方法，不能承诺固定使用 legacy key；尤其 `tryPut(host, meta)` 的默认委派可由实现根据 `meta.hostname` 选择 key。生产 host-aware 缓存必须覆盖这些重载，调用方不能从接口默认行为推断 host 隔离。修改前缀、分隔符或序列化形状必须考虑滚动升级时新旧实例同时读写。

## Analytics 发布契约

### 访问记录

`VisitRecorderPort.recordVisit` 接收 Redirect 已确认发生的真实跳转。`RedirectVisitRecord` 包含租户、链接、可选 application/domain、UTC 时间和经过限制的访问上下文。

端口实现把基础访问流写入 Redis Stream；`events.enabled` 只决定后续是否采样并落访问明细，不能关闭基础 PV/UV 或应用点击额度统计。根据配置，部分外围异常可能 fail-open，调用方不得据此推断记录已经持久化。

### 点击额度

`ApplicationClickQuotaReservationPort.tryReserveMonthlyClick` 对 UTC 月窗口尝试原子预留。`false` 表示实现已明确判定达到上限；`true` 只表示 Redirect 可以继续，不能等价为“已获得名额”或“计数已递增”。无上限、非法输入，以及 Redis、MySQL 基线查询或 Lua 脚本异常都会固定 fail-open 返回 `true`。因此监控和计费必须区分真正的 Redis 预留与基础设施降级放行。

`ApplicationClickUsagePort` 查询 `[fromInclusiveUtc,toExclusiveUtc)` 的已持久化点击量，主要用于兼容或初始化，不包含尚未 flush 的精确实时保证。

### Redis key 和 member

`AnalyticsKeys` 是跨模块 SSOT。关键格式如下：

| 用途 | 格式 |
| --- | --- |
| 基础 dirty stream | `stats:dirty:flush:{yyyyMMdd}` |
| 维度 dirty stream | `stats:dirty:dim:{yyyyMMdd}` |
| scope dirty stream | `stats:dirty:scope:{yyyyMMdd}` |
| dirty link member | `{tenantId}:{linkId}` |
| link PV | `stats:pv:{tenantId}:{linkId}:{yyyyMMdd}` |
| link UV HLL | `stats:uv:{tenantId}:{linkId}:{yyyyMMdd}` |
| tenant scope member | `tenant:{tenantId}:0` |
| application scope member | `application:{tenantId}:{applicationId}` |
| domain scope member | `domain:{tenantId}:{domainId}` |
| 点击额度 | `quota:click:application:{tenantId}:{applicationId}:{yyyyMM}` |
| 访问流 | `stats:visit:events` |

dirty member 的 `{tenantId}:{linkId}` wire shape 保持不变；它现在只表示“需要刷新”，不表示 active set membership。flush 消费当前累计 PV/HLL 值，所以重复 dirty 消息通常只重复 upsert；上游访问事件重放仍可能重复增加 PV。

## Shortlink 集成事件

事件类型由 `ShortLinkEventTypes` 固定，并写入 `integration_events.event_type`；当前这些类型对应 V1 payload：

- `ShortLinkCreatedV1`
- `ShortLinkUpdatedV1`
- `ShortLinkArchivedV1`
- `ShortLinkRestoredV1`
- `ShortLinkDeletedV1`

共同字段包含唯一 `eventId`、UTC `occurredAtUtc` 和 `ShortLinkPublicSnapshot`。payload record 本身没有通用的内嵌 `type/version` 字段，消费者必须先按 event_type 选择 V1 record 再反序列化。snapshot 是事件发生后可发布事实；删除事件保留删除前快照供历史报表展示。

`ShortLinkPublicSnapshot` 包含 code、hostname、originalUrl、enabled、expires、preview、query forwarding、application/domain 和 archived 状态。消费者必须按事件版本反序列化，并容忍历史可空字段。`eventId` 可用于消费者去重，但基础设施不承诺全链路 exactly-once。

事件通过业务事务内 `IntegrationEventStore.append` 写入。消费者读取递增 seq 并维护自己的 checkpoint；处理完成但 checkpoint 未提交时会重放。

## Governance payload 与执行端口

`ApprovalPayloadTypes` 定义稳定 `type/version`，当前覆盖目标地址变更、统计明细导出和应用额度提升。`ApprovalPayloadCodec`
对非法 JSON 和未知字段严格失败，但 `read` 只按目标 Java 类型反序列化，不自行校验 `type/version`；调用方必须显式
检查类型和版本，不能静默忽略审批意图。

`ApprovalSubmissionPort` 接收已经认证的 `ApprovalRequester` 和结构化输入。当前由该端口提交的目标地址变更和访问明细导出使用版本化 JSON；导出没有可比较旧状态，因此 `beforeSnapshot` 为 null。时间范围均按 UTC。Governance 的通用审批实体仍兼容按操作类型解释的历史纯文本快照，不能把所有持久化审批记录都当作同一 JSON DTO。

审批通过时，Governance 的固定顺序是：

1. 按 `SensitiveOperation` 选择执行器；
2. 要求恰好一个执行器支持该操作；
3. 校验 payload 和执行前置条件；
4. CAS 把请求从待审批推进到处理中/终态；
5. 调用执行器并记录审计结果。

先验证执行器再 CAS，避免缺失或重复执行器把审批卡在半推进状态。`ApprovalExecutionPort.execute` 必须校验 tenant、目标资源当前状态及该操作定义的前置条件；操作提供 `beforeSnapshot` 时还必须做陈旧状态校验，不能仅凭“已批准”跳过业务并发保护。

## Null、时间与集合

- 应用额度提升 V1 中 `monthlyLinkLimit` 必填；只有可选的 `monthlyClickLimit` 为 null 时表示本次不携带点击额度变更，二者都不表示清零。
- legacy 链接的 applicationId/domainId 可以同时为空；新应用级链路要求两者成对。
- 公开集合视图应视为快照，不依赖实现是否返回可变集合。
- 日期 key 使用 UTC `LocalDate`；月额度使用 UTC 月初。跨日请求可能在不同日 HLL 中出现，跨日 UV 不能简单相加得到精确去重人数。

## 兼容性变更流程

修改发布契约时至少执行：

1. 判断是 Java source compatibility、JSON wire、Redis key/member 还是数据库历史数据变化；
2. 对破坏性 payload 变化新增版本，不覆盖 V1；
3. 为旧数据和滚动升级提供双读或显式迁移窗口；
4. 更新本页、对应专题和 `core-logic-catalog.json`；
5. 增加序列化/key 格式契约测试；
6. 不修改已应用 Flyway migration。

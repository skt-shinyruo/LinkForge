# 跨上下文事件与一致性链路

本文说明跨上下文同步端口、集成事件、缓存失效 outbox 和消费者恢复语义。稳定字段与 Redis 格式见 [发布契约](published-contracts.md)。

## 一致性目标

LinkForge 是模块化单体，但模块边界仍按服务边界对待：

1. 跳转正确性必须能同步回源 Shortlink，不能依赖异步投影。
2. 业务事务回滚时不能留下集成事件或缓存失效完成标记。
3. 缓存驱逐、目录和统计消费者必须允许重复执行。
4. 系统不承诺 exactly-once；文档和监控必须暴露重放、pending、DLQ 与延迟。

## 同步发布端口

| 契约 | 拥有者 | 用途 | 失败边界 |
| --- | --- | --- | --- |
| `ShortLinkReadPort` | Shortlink | Redirect 回源、Analytics/Governance 归属和摘要 | 空结果表示在给定范围不可见或不存在 |
| `LinkCachePort` | Redirect | 三态缓存查询、正负写入、驱逐 | Redis 故障必须表现为 MISS/best-effort，不能表现为不存在 |
| `ApplicationScopePort` | Platform | application/domain 授权和 quota | 不存在、禁用或跨租户应用使用稳定异常；ACTIVE 应用缺 quota 行时为空，按不限制处理 |
| `DomainHostnameLookupPort` | Platform | domainId/hostname 映射 | 反查可以不支持并返回空 |
| `VisitRecorderPort` | Analytics | 记录真实跳转 | 可能按配置 fail-open，成功返回不等于已落 MySQL |
| `ApplicationClickQuotaReservationPort` | Analytics | 原子月点击额度预留 | 当前 Redis adapter 内部故障固定放行 |
| `ApprovalSubmissionPort` | Governance | 提交结构化审批 | payload 编码或业务校验失败时不创建请求 |
| `ApprovalExecutionPort` | 业务上下文 | 执行已批准操作 | 必须重新校验资源状态；操作定义 before snapshot 时还要校验陈旧状态 |

已删除的 `LinkMetaSourcePort` 不再是第二条权威读取契约。Redirect 缓存未命中只调用 `ShortLinkReadPort`，可用性只由 `RedirectService` 判定。Shortlink 发布实现仅把跳转元数据查询包在只读事务中，按生产 ShardingSphere 的 transactional read strategy 路由 primary；其他控制面读不随之切换。

## 短链事务写入

创建、更新、归档、恢复和删除的核心提交顺序是：

1. Shortlink 聚合执行不变量并记录领域事件。
2. repository 写短链、标签等业务状态。
3. dispatcher 拉取聚合领域事件，转换为 V1 集成事件并追加 `integration_events`。
4. 同一事务内 upsert Redirect 缓存失效 outbox。
5. 事务提交；任何前置步骤失败会一起回滚。
6. after-commit 快路径立即尝试驱逐缓存。
7. outbox worker 异步领取未完成记录，重复驱逐并标记完成；失败按退避重试。

集成事件和 outbox 是 durable 部分，after-commit 只是降低旧缓存窗口的优化。不能只监控 after-commit 日志来判断最终一致性。

## 缓存失效语义

缓存键可能包含 unscoped code、domain hostname + code 和 legacy base-host 兼容键。outbox 保存足以重建这些驱逐目标的信息。

- 驱逐必须幂等；after-commit 与 worker 正常情况下会重复。
- 事务回滚不运行 after-commit，也不会留下 outbox。
- 快路径失败不改变业务成功响应；worker 继续恢复。
- outbox 用 `tenantId + domainScope + code` 合并同一失效 identity。首次入队的 generation 为 `1`；
  后续入队原子递增 generation、恢复 `PENDING` 并清空上一代重试状态。
- worker 读取 identity、status 和 generation。驱逐成功后的完成更新与驱逐失败后的退避更新都使用
  `id + generation + PENDING` CAS；如果驱逐期间出现新一代入队，旧 worker 允许完成幂等驱逐，但不能消费或
  延迟新一代 intent。该语义是 at-least-once，不是 exactly-once。
- Redis 长时间不可用时，Redirect 读异常降级为 MISS，并同步回源 Shortlink，因此正确性仍由数据库事实保证，代价是性能下降。
- 负缓存和正缓存都必须驱逐，否则新建链接或状态变化会被旧 sentinel/快照遮蔽。

generation 字段通过 additive migration 引入，默认值 `1` 让历史行和旧实例的 INSERT 在滚动发布中继续工作。
应先发布 migration 与 generation producer，再在所有旧 producer/worker 退出后依赖 generation CAS 的完整竞态保证；
混部窗口保持向后兼容，但旧 worker 不理解 generation，仍只具备升级前的一致性保证。

排障应检查 outbox 的 status、generation、attempts、next retry 和 last error，同时检查 Redis 与 Shortlink 权威读取，而不是手工修改数据库后只清一个 key。

## 领域事件到集成事件

`ShortLinkDomainEventDispatcher` 使用 destructive pull：从聚合取出当前积累事件后清空，再逐个发布。它隐含以下调用约束：

- 每次状态变化后应在同一用例内及时 dispatch；不要在一个聚合中积累多轮变更后再依赖“当前快照”猜测每一步历史。
- publisher 抛错会使业务事务失败，但已从内存聚合拉出的事件不会自动回到列表；调用方依赖事务回滚和整个用例重试，而不是复用同一个聚合实例。
- ownership 变化使用内部 `ShortLinkOwnershipChanged` 捕获前后 scope，dispatcher 将其映射为现有 UPDATED 集成事件，不扩展跨上下文 wire contract。
- 未识别的内部领域事件当前会被忽略，这是兼容限制，应通过日志和测试避免悄然丢失新增事件族。

`ShortLinkEventFactory` 创建的 `ShortLinkPublicSnapshot` 是事件时刻的发布事实。事件由 `IntegrationEventStore.append` 与业务数据同事务落库，producer 为 `shortlink`，aggregateType 为 `shortlink`。

## IntegrationEventStore 与 checkpoint

数据库事件表使用递增 `seq` 作为消费游标，同时保留唯一 `eventId`。典型消费者循环：

1. 读取自己的 checkpoint。
2. 调 `listAfterSeqByProducer` 有界拉取。
3. 按 eventType/version 解析并执行幂等 upsert。
4. 成功后推进 checkpoint。

处理完成后、checkpoint 提交前崩溃会重放。消费者不得用“seq 只增”推导 exactly-once；需要防重复时使用 eventId、唯一键或当前状态 upsert。

未知 payload version 不能按最新类型强行反序列化。根据消费者重要性，应选择停住 checkpoint 并告警，或写兼容 dead letter 后推进；选择必须在该消费者专题中说明。

## Analytics 短链目录

`ShortLinkCatalogProjectorJob` 消费 Shortlink 集成事件，维护 Analytics 的链接目录：

- create/update/archive/restore upsert 当前公开快照。
- delete 保留历史展示所需字段并标记 `deleted=true`。
- Top links 仍通过 `ShortLinkReadPort` 补齐当前摘要；目录不是跳转权威来源。
- 坏 payload 的 dead-letter 写入是隔离措施，不具备消息代理事务保证。

目录延迟只影响报表展示补全，不能改变 Redirect 结果。

## Analytics 访问流

Redirect 发生真实跳转时把记录交给 Analytics：

1. appender 写 `stats:visit:events`。
2. projector 消费访问记录，增加 Redis PV/HLL 并原子推进 V2 generation marker。
3. flush consumer 读取 marker member 对应的当前累计值，upsert MySQL 后按 generation compare-and-delete；兼容期同时 ACK legacy dirty Stream。
4. 可选明细 consumer 采样、`insert ignore` 并 ACK。

这里存在两类重放边界：

- 标准访问记录携带 requestId，projector 重放由 Redis Lua 幂等投影吸收；历史无 requestId 消息仍可能重复增加 PV。
- V2 marker CAS 冲突会保留新 generation；legacy message 重放只会再次读取当前累计值并 upsert，不会由 flush 增加计数。

因此报表是最终一致且非 exactly-once。dirty member 固定为 `{tenantId}:{linkId}`，只表示需要刷新，不是
active-set membership。当前 producer 默认只写 V2，consumer 在退役门禁完成前保持 V2/legacy dual-read。

## ACK、pending 与 DLQ

- Redis consumer group 只有在对应写入成功后才应 ACK。
- 普通 Redis/DB 故障保留 pending，后续由同 consumer 或 reclaim 处理。
- poison record 只有在 DLQ 写入成功后才 ACK；DLQ 写入失败会保留原消息 pending，避免隔离记录丢失。
- DLQ 写成功后的近似裁剪和容量采样是 best-effort 维护，不改变该次持久化结果。
- DLQ 写入与原 Stream ACK 不在同一事务中；ACK 失败会保留 pending 并允许安全重放，也可能再次写入同一隔离记录。因此 DLQ 不是事务审计日志。
- consumer name 应在多实例中稳定且唯一；频繁变化会增加 pending reclaim 压力。

## 一致性等级

| 链路 | 一致性 | 正确性来源 |
| --- | --- | --- |
| Shortlink 数据与集成事件 | 同一数据库事务 | MySQL 事务 |
| Shortlink 与缓存失效 outbox | 同一数据库事务 | MySQL 事务 |
| Redirect 读取 | 同步权威回源 | `ShortLinkReadPort` |
| Redirect Redis 缓存 | 最终一致/best-effort | outbox 重试 + MISS 回源 |
| Analytics 目录 | 最终一致 | integration event + checkpoint |
| PV/UV Redis 到 MySQL | 最终一致 | V2 generation CAS + 当前快照 upsert；兼容期 dual-read legacy Stream |
| 访问明细 | 可选、采样、最终一致 | visit stream consumer |
| 审批业务执行 | 状态机 + 业务幂等/CAS | Governance 与执行上下文事务 |

## 排障顺序

### 跳转仍读到旧状态

1. 查询 Shortlink 权威数据和版本。
2. 检查失效 outbox 是否存在、是否重试、目标 host/code 是否完整。
3. 检查正缓存与负缓存两族 key。
4. 验证 Redis 异常时是否真的走到 `ShortLinkReadPort`。

### Analytics 目录落后

1. 检查 `integration_events` 是否与业务事务一起写入。
2. 检查 projector checkpoint、最后 seq 和 dead letter。
3. 验证 payload type/version 与消费者支持列表。
4. 对展示缺失再检查 Shortlink 摘要补全。

### PV/UV 落后或偏大

1. 检查 visit stream lag/pending/reclaim。
2. 检查 V2 marker cardinality/age/CAS 冲突、legacy Stream pending 和 flush 日志。
3. 对比 Redis 当前累计值与 MySQL upsert 值。
4. 检查 requestId 去重指标和历史无 requestId 消息；调用方重复生成多个 requestId 仍可能使 PV 偏大。

## 源码入口

- `ShortLinkDomainEventDispatcher`、`ShortLinkEventFactory`、`ShortLinkEventAppender`
- `RedirectCacheInvalidations`、`RedirectCacheInvalidationOutboxJob`、`RedirectCacheSyncAdapter`
- `MybatisIntegrationEventStore`、`ShortLinkCatalogProjectorJob`
- `AnalyticsRedirectEventProjectorJob`、`AnalyticsFlushJob`、`AnalyticsEventIngestJob`
- `ShortLinkReadApplicationService`、`RedirectService`

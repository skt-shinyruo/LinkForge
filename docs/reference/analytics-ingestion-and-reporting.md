# 统计采集与报表链路

## 业务目标

Analytics 只维护访问聚合和报表读模型。Redirect 只有在真实跳转前才记录一次访问；预览、未找到、不可用和额度拒绝都不会进入统计。

## 当前链路

```text
Redirect
  -> VisitRecorderPort
  -> AnalyticsVisitEventService
  -> RedisAnalyticsVisitEventAppender
  -> AnalyticsRedisAggregateWriter（一次 Redis Lua）
  -> AnalyticsFlushJob
  -> MySQL 日统计表
  -> StatsController
```

写入路径没有中间队列。`AnalyticsVisitEventService` 把 `RedirectVisitRecord` 转成内部记录；appender 按 UTC 日期计算访客指纹，然后调用聚合 writer。Redis 故障是否影响跳转由 `app.analytics.events.fail-open` 控制，默认允许跳转并记录降级指标。

## Redis 原子聚合

`AnalyticsRedisAggregateWriter` 用一段 Lua 脚本完成以下操作，成功返回后才视为访问已进入统计：

1. 使用请求 ID 的去重 key；重复请求直接结束，不再次计数。
2. 对 `stats:pv:{tenantId}:{linkId}:{yyyyMMdd}` 执行 `INCR`。
3. 对链接、租户、应用和域名的 UV key 执行 `PFADD`；访客指纹按 `sha256(day|ip|userAgent|salt)` 计算，HLL 结果是近似值。
4. 对 link/scope V2 marker 和 first-seen Hash 原子推进 generation，记录待刷新的 member。
5. 为本次脚本触及的 key 设置统一 TTL。

member 格式由 `AnalyticsKeys` 固定：链接为 `{tenantId}:{linkId}`，scope 为 `tenant:{tenantId}:0`、`application:{tenantId}:{applicationId}` 或 `domain:{tenantId}:{domainId}`。日期和所有报表范围均按 UTC 解释。

## MySQL 刷新

`AnalyticsFlushJob` 按配置回补最近 N 天（默认 7 天），从 V2 marker 公平领取 link 和 scope member：

- 读取 Redis 当前 PV 和 HLL UV，批量 upsert `link_stats_daily`。
- 读取租户、应用、域名 HLL UV，批量 upsert `analytics_scope_stats_daily`。
- MySQL 写入成功后，只在 generation 未变化时删除 marker field；并发新访问会留下下一代 marker。
- marker 的 cursor、overflow 和 TTL 只服务于有界轮转，不改变统计口径。

因此 Redis 是实时累计层，MySQL 是报表事实快照；两者之间是最终一致。flush 失败会保留 marker，下一次调度继续处理。

## 链接目录

`ShortLinkCatalogProjectorJob` 消费 Shortlink 的集成事件，维护 `analytics_link_catalog`：

- 创建、更新、归档和恢复事件 upsert 当前公开快照。
- 删除事件保留 code、目标地址、application/domain 归属并标记 `deleted=true`。
- 该表故意不随 `short_links` 物理删除，用于历史 Top 展示、应用/域名筛选和点击额度基线。
- 目录延迟只影响展示和筛选，不改变 Redirect 的权威读取。

## 报表接口

HTTP 报表只有三类：

- `GET /api/v1/stats/links/{id}/daily`：单条链接按 UTC 日返回 PV/UV。
- `GET /api/v1/stats/overview`、`/stats/applications/{id}/overview`、`/stats/domains/{id}/overview`：租户、应用或域名总览；应用总览也保留 `/applications/{id}/stats/overview` 兼容路径。
- `GET /api/v1/stats/top-links`、`/stats/applications/{id}/top-links`、`/stats/domains/{id}/top-links`：按 PV 或 UV 排序的 Top 链接；应用和域名也保留对应兼容路径。

查询始终使用认证主体的 tenantId，日期首尾包含且最多 366 天。Top 查询先按 MySQL 数值排序，再由 `AnalyticsLinkSummaryEnricher` 补齐链接摘要；目录中的删除标记会保留历史统计。跨日 UV 是每日 HLL 的累加，不能解释为区间精确去重人数。

## 失败边界

- Redis Lua 失败时，`AnalyticsVisitEventService` 按 fail-open 配置放行或抛出异常；放行不代表本次统计已保存。
- Redis 聚合成功后，MySQL 尚未刷新前只会影响报表延迟，不影响 Redirect。
- HLL UV 始终是近似口径；scope 快照缺失时，总览会回退到链接 UV 求和。
- 目录或摘要补全失败不会改变 PV/UV 数值，也不会改变 Redirect 结果。

## 源码入口

- `AnalyticsVisitEventService`：接收真实跳转记录和 fail-open 边界。
- `RedisAnalyticsVisitEventAppender`：计算 UTC 日和访客指纹。
- `AnalyticsRedisAggregateWriter`：Redis Lua 原子聚合。
- `AnalyticsFlushJob`：V2 marker 扫描和 MySQL upsert。
- `ShortLinkCatalogProjectorJob`：维护删除后仍保留的 Analytics 链接目录。
- `AnalyticsQueryRepository`、`AnalyticsReportingApplicationService`：报表查询和摘要补全。
- `StatsController`：daily、overview、top HTTP 入口。

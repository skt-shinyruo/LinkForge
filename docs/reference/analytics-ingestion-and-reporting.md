# 统计采集与报表链路

## 业务目标

Analytics 接收 Redirect 的访问事件，异步构建 PV、UV、维度统计、访问明细和报表。基础访问流始终存在；`events.enabled` 只控制访问明细消费和落库，不关闭 PV/UV、scope 统计或点击额度。访问 appender 是否向 Redirect 外抛由 `events.fail-open` 决定，默认放行跳转。

## 流程图

<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="540" viewBox="0 0 1080 540" role="img" aria-label="统计采集与报表链路">
  <defs>
    <marker id="arrow-analytics" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .stream{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .job{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .db{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-analytics)}
    </style>
  </defs>
  <rect class="box" x="30" y="60" width="160" height="74"/>
  <text class="text" x="110" y="90" text-anchor="middle">RedirectService</text>
  <text class="small" x="110" y="112" text-anchor="middle">真实跳转前记录访问</text>

  <rect class="box" x="250" y="50" width="180" height="94"/>
  <text class="text" x="340" y="80" text-anchor="middle">AnalyticsVisitEventService</text>
  <text class="small" x="340" y="102" text-anchor="middle">RedirectVisitRecord</text>
  <text class="small" x="340" y="122" text-anchor="middle">转换事件</text>

  <rect class="stream" x="500" y="45" width="190" height="104"/>
  <text class="text" x="595" y="75" text-anchor="middle">Redis Stream</text>
  <text class="small" x="595" y="98" text-anchor="middle">visitorKey / ipHash</text>
  <text class="small" x="595" y="118" text-anchor="middle">维度规范化</text>
  <text class="small" x="595" y="138" text-anchor="middle">近似 trim</text>

  <rect class="job" x="760" y="35" width="230" height="125"/>
  <text class="text" x="875" y="65" text-anchor="middle">AnalyticsRedirectEventProjectorJob</text>
  <text class="small" x="875" y="88" text-anchor="middle">消费访问 stream</text>
  <text class="small" x="875" y="108" text-anchor="middle">写 Redis PV / UV / 维度</text>
  <text class="small" x="875" y="128" text-anchor="middle">推进 V2 marker</text>
  <text class="small" x="875" y="148" text-anchor="middle">成功 ack</text>

  <rect class="job" x="250" y="245" width="180" height="100"/>
  <text class="text" x="340" y="275" text-anchor="middle">AnalyticsFlushJob</text>
  <text class="small" x="340" y="298" text-anchor="middle">刷新 V2 + 兼容读</text>
  <text class="small" x="340" y="318" text-anchor="middle">PV/HLL UV upsert</text>

  <rect class="job" x="500" y="245" width="190" height="100"/>
  <text class="text" x="595" y="275" text-anchor="middle">DimensionFlushJob</text>
  <text class="small" x="595" y="298" text-anchor="middle">维度 hash PV</text>
  <text class="small" x="595" y="318" text-anchor="middle">维度 HLL UV</text>

  <rect class="job" x="760" y="245" width="230" height="100"/>
  <text class="text" x="875" y="275" text-anchor="middle">AnalyticsEventIngestJob</text>
  <text class="small" x="875" y="298" text-anchor="middle">访问明细采样入库</text>
  <text class="small" x="875" y="318" text-anchor="middle">poison record 隔离</text>

  <rect class="db" x="250" y="420" width="740" height="78"/>
  <text class="text" x="620" y="450" text-anchor="middle">MySQL 读模型</text>
  <text class="small" x="620" y="472" text-anchor="middle">link_stats_daily / scope stats / link_stats_dim_daily / link_visit_events</text>

  <rect class="box" x="30" y="420" width="160" height="78"/>
  <text class="text" x="110" y="450" text-anchor="middle">StatsController</text>
  <text class="small" x="110" y="472" text-anchor="middle">报表查询 / 导出审批</text>

  <path class="line" d="M190 97 H250"/>
  <path class="line" d="M430 97 H500"/>
  <path class="line" d="M690 97 H760"/>
  <path class="line" d="M875 160 V245"/>
  <path class="line" d="M595 149 V245"/>
  <path class="line" d="M340 345 V420"/>
  <path class="line" d="M595 345 V420"/>
  <path class="line" d="M875 345 V420"/>
  <path class="line" d="M250 459 H190"/>
</svg>

## 访问事件写入

Redirect 只在真实跳转前调用 `VisitRecorderPort.recordVisit()`。preview、not found、disabled、expired 和 quota 拒绝都不记录。Analytics 的实现是 `AnalyticsVisitEventService`：

- 从 `RedirectVisitRecord` 读取 tenantId、linkId、applicationId、domainId、code、originalUrl 和访问上下文。
- 转为 `RedirectVisitEvent`。
- 调用 `AnalyticsVisitEventAppender.append()`。
- `events.fail-open=true` 时写入失败只记录 debug，不影响跳转；`false` 时异常继续外抛。

`events.enabled` 和 `sampleRate` 不参与这一步。先写完整基础 stream，再由独立明细 consumer 决定是否采样落库。

`RedisAnalyticsVisitEventAppender` 写 Redis Stream 前会：

- 按 occurredAtMillis 计算 UTC day。
- 规范化 Referer、语言、User-Agent、设备、UTM。
- 计算 `visitorKey = sha256(day|ip|ua|salt)`，用于日 UV；同一访客跨 UTC 日会产生不同 key。
- 计算 `ipHash = sha256(ip|salt)`，用于明细排障关联，不落明文 IP。
- 用单条 `XADD MAXLEN ~` 原子追加并近似 trim，避免 `XADD` 与独立 `XTRIM` 之间的竞态。

访问流最低容量按 `peak-events-per-second * recovery-window-seconds * (1 + safety-margin-percent/100)`
向上取整。`visit-stream.max-len` 为空时兼容回退到 `events.stream-max-len`；启动校验拒绝低于容量预算的配置。

`ipHash` 不包含日期，能跨日关联相同 IP，因此属于假名化标识而不是匿名数据。salt 必须在生产覆盖并限制访问；更换 salt 会切断新旧指纹连续性。User-Agent、tracking value 和维度值会截断，但仍可能包含隐私信息。

## 聚合投影

`AnalyticsRedirectEventProjectorJob` 消费访问 stream：

- 只投影带 visitorKey 的记录。
- 调用 `AnalyticsRedisAggregateWriter.write()` 写 Redis 聚合。
- 成功后 ack。
- 如果某条失败，会先 ack 已处理记录并停止本轮，让失败记录留在 pending，等待后续重试。
- 缺少 `visitorKey` 的不可投影记录会 ACK 丢弃，不增加 PV/UV。

标准访问消息携带唯一 `requestId`。writer 用一个 Lua 脚本原子检查幂等标记，并更新 PV、UV、维度和 dirty signal；
因此“聚合成功但 ACK 失败”导致的同一 Stream 消息重放不会重复增加 PV。历史上缺少 `requestId` 的兼容消息仍走
至少一次增量路径；Redirect 调用方若把一次业务访问重复调用成多个新 requestId，也不在该幂等边界内。

`AnalyticsRedisAggregateWriter` 维护：

- link 日 PV：Redis string `INCR`。
- link 日 UV：Redis HyperLogLog。
- tenant/application/domain scope UV。
- link、scope 和可选维度 V2 generation marker Hash，以及同 field 的 first-seen Hash，供 flush job 找到需要落库的统计键。
- 可选维度统计：维度 PV hash 和维度 UV HLL。

不存在 active set。dirty link member 固定为 `{tenantId}:{linkId}`，只表达“该累计值需要刷新”；删除 marker
不会删除 Redis PV/HLL。每次聚合变化都在同一 Lua 脚本中 `HINCRBY` generation，首次变脏时间用
`HSETNX` 保持，因此高频点击不会无限增加 marker cardinality，也不会刷新最老等待年龄。

新 producer 默认只写 V2。`dirty-marker.legacy-write-enabled=true` 仅在回滚窗口 additive 追加旧 Stream，不能
代替 V2；consumer 默认同时刷新 V2 marker 和 legacy Stream，以支持滚动升级中的旧实例与历史消息。

flush 的 V2 扫描使用 `AnalyticsKeys` 生成的 `:claim:cursor` 和 `:claim:overflow` 状态键。两者只保存跨调度、
跨实例的公平轮转进度，并跟随 marker TTL；marker 清空或过期时一并清理，不改变已发布的 marker/member wire shape。

## 落库与明细

`AnalyticsFlushJob`：

- 定时回刷最近 N 天。
- 扫描 link V2 marker，把 Redis 当前 PV 和 HLL UV upsert 到 `link_stats_daily`，随后按 claimed generation 做 compare-and-delete。
- 扫描 scope V2 marker，把租户、应用、域名当前统计 upsert 到 scope stats 表，再完成相同的 generation CAS。
- V2 marker 使用 Redis 持久化的 HSCAN cursor；紧凑 Hash 一次返回但超过批次上限的成员进入同 marker 的有界 overflow 队列。
  cursor、overflow 都跟随 marker TTL，供多实例和重启共享轮转进度；每次 claim 返回严格不超过 `BATCH_SIZE`，避免持续
  generation 冲突的前部成员让冷尾成员饥饿。实现只使用有界 HSCAN，不使用 `KEYS` 或 `HGETALL`。
- 兼容读开启时继续通过 consumer group 排空 link/scope legacy dirty Stream。
- 使用 ShedLock 防止多实例重复执行。
- MySQL 写入成功后才完成 V2 CAS 或 ACK legacy 消息。写库期间 generation 前进时 CAS 冲突会保留 marker；
  legacy 失败记录留在 pending。两者重放都只读取当前累计值和 upsert，不会由 flush 自身增加 PV。

`AnalyticsDimensionFlushJob`：

- 仅在 dimensions enabled 时运行。
- 扫描维度 V2 marker；兼容读开启时继续排空 legacy 维度 Stream。
- 按 marker 指定的 link member 扫描配置维度 Hash/HLL，写入 `link_stats_dim_daily`，不扫描 active set。

`AnalyticsEventIngestJob`：

- 仅在 `analytics.events.enabled=true` 时运行。
- 创建并消费 Redis Stream consumer group。
- 优先处理本 consumer pending，再接管闲置 pending，最后读取新消息。
- 按 sampleRate 决定是否保存访问明细。
- 在 `ingest-batch-size`、`ingest-max-batches` 和 `ingest-time-budget-ms` 三重边界内连续恢复 backlog。
- insert ignore 成功后 ack；普通数据库失败会停止本轮，避免在同一次调度内紧循环重试。
- 数据完整性异常时逐条隔离 poison record；只有 dead letter 已写入后才 ACK poison。
- 普通 DB 异常或 DLQ 写入失败都保留当前消息 pending，等待重试。

DLQ writer 不向调度线程传播 Redis 异常，但会返回持久化结果并记录有界失败指标。DLQ 写入失败时原消息不 ACK；
写入成功后的近似裁剪和容量采样是 best-effort 维护，不会改变已持久化结果。采样未命中的消息直接 ACK，
不会进入明细表或 DLQ。

## 报表查询

主要 API：

- `GET /api/v1/stats/links/{id}/daily`
- `GET /api/v1/stats/overview`
- `GET /api/v1/stats/applications/{id}/overview`
- `GET /api/v1/applications/{id}/stats/overview`
- `GET /api/v1/stats/domains/{id}/overview`
- `GET /api/v1/stats/top-links`
- `GET /api/v1/stats/applications/{id}/top-links`
- `GET /api/v1/applications/{id}/stats/top-links`
- `GET /api/v1/stats/domains/{id}/top-links`
- `GET /api/v1/stats/links/{id}/dimensions`
- `GET /api/v1/stats/links/{id}/events`
- `POST /api/v1/stats/links/{id}/events/export-requests`
- `POST /api/v1/applications/{applicationId}/links/{id}/events/export-requests`

`AnalyticsReportingApplicationService` 会通过 `AnalyticsLinkSummaryEnricher` 调 Shortlink 读端口补齐 Top 链接的 code、shortUrl、originalUrl。如果短链已删除，返回 `deleted=true`。

所有报表、访问明细和导出申请共用 `ReportRange`：日期按 UTC 自然日解释、首尾都包含，单日为 1 天，最多
366 天。`from > to`、367 天及更长范围在查询或审批副作用之前返回 `BAD_REQUEST`；闰年完整 366 天合法。

统计口径限制：

- 日 UV 是 HLL 近似值。
- Top links 和维度查询当前对多日 `uv` 使用 `SUM(日 UV)`，同一访客跨日会重复计数，不能解释为区间精确 UV。
- tenant/application/domain 日报优先使用 scope HLL；scope 行缺失时回退为链接日 UV 之和，同一访客访问多条链接会重复计数。
- catalog 投影延迟会影响 application/domain 过滤与链接展示补全，但不影响 Redirect 权威跳转。

## 访问明细导出审批

访问明细导出不是直接下载。`AnalyticsExportRequestService.requestLinkEventExport()` 会：

1. 调 `ShortLinkReadPort.findOwnership()` 校验短链属于当前租户。
2. 如果路径带 applicationId，校验短链属于该应用。
3. 默认时间范围是最近 1 天。
4. 用统一 `ReportRange` 校验 `from <= to` 且最多包含 366 个 UTC 自然日。
5. 调 `ApprovalSubmissionPort.requestAnalyticsDetailExportApproval()` 提交审批。

当前代码只完成审批请求创建和审计，尚未实现审批通过后的文件生成执行器。

## 源码分析

- `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`
  - 实现 `VisitRecorderPort`。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java`
  - 访问事件规范化、hash、写 Redis Stream。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java`
  - 访问 stream 到 Redis 聚合的投影。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedisAggregateWriter.java`
  - PV/UV/维度 Redis 写入。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJob.java`
  - link 和 scope 聚合落 MySQL。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJob.java`
  - 维度统计落 MySQL。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java`
  - 访问明细消费、采样、poison 隔离。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/AnalyticsQueryServiceImpl.java`
  - MySQL 读模型查询实现。
- `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`
  - 报表应用服务，补齐 Top 链接摘要。
- `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
  - 明细导出审批入口。
- `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
  - 报表和导出审批 HTTP 入口。

## 一致性与降级

统计链路是最终一致的，不宣称端到端 exactly-once。标准事件的 Stream 重投由 requestId 幂等投影保护，但调用端重复
生成事件、历史无 requestId 消息以及跨 Redis/MySQL 的异步延迟仍需单独处理。基础 PV/UV 依赖 projector 和
V2 marker flush；兼容期还会排空 legacy dirty Stream。访问明细还受 `events.enabled` 和 `sampleRate` 影响。

legacy 读退役必须经过外部 rollout 证据，而不是由一次本地测试推断：确认所有旧 producer 停写并记录
`legacy-write-stopped-at`，持续观察 legacy remaining、last-write age 和 drained 指标确认排空并记录
`legacy-drained-at`，再从两个时间中的较晚者起等待完整 `compatibility-ttl-days`。只有同时关闭 legacy write、
设置 retirement confirmed 且两个时间均已越过 TTL，启动门禁才允许关闭 legacy read。默认配置保持 dual-read。

legacy dirty Stream 的 `XLEN` 包含已经 ACK、但尚未被裁剪的历史记录，不能作为剩余工作量。
`linkforge.analytics.dirty.legacy.retained_entries` 明确只表示保留记录；实际剩余工作由同一 consumer group 的
`lag + pending` 组成，分别通过 `.lag`、`.pending` 和汇总的 `.remaining` 暴露。link、scope、dimension 各用固定
`marker` 标签，并在一次调度内汇总全部回填日期；stats job 的健康状态再合并 link 与 scope，避免日期或 stream key
形成高基数标签。任一 consumer-group 观测不完整时 remaining 为 `-1`；retained、last-write 或 group 任一观测
不完整都会令 `.observation_degraded=1`。`.drained` 只累计 Redis 实际 ACK 的数量，ACK 异常或部分成功不会把
请求批量误报为已排空；ingest、projector 和 legacy flush 都会在 ACK 不完整时停止本轮，让剩余 pending 留待重试。

排障按层定位：

1. visit stream 的 lag、pending、reclaim、最老未处理年龄、DLQ 和 job health 四态。
2. Redis PV/HLL 当前值、V2 marker cardinality/最老年龄/generation 冲突，以及 legacy retained entries、remaining、last-write age。
3. flush 日志、ShedLock 和 MySQL upsert 时间。
4. 明细 consumer name、pending reclaim、采样率与 `stats:visit:events:dlq`。
5. catalog checkpoint 与 Shortlink 摘要补全。

关键有界指标包括 `linkforge.analytics.fail_open` / `linkforge.analytics.degraded`（component、稳定 reason），
`linkforge.stream.lag` / `pending` / `remaining` / `oldest_unprocessed_age_millis`，以及
`linkforge.analytics.dirty.marker.cardinality` / `oldest_age_millis` / `generation_conflicts`。job health 固定使用
`no_traffic`、`draining`、`backlog`、`degraded` 四个有界 state：已 ACK 但仍被 Stream 保留的历史长度不算
backlog；已知剩余量为零是 no traffic，首次非零或持平/下降是 draining，连续观测增长才是 backlog，pending 或
XINFO 无法观测时是 degraded。观测恢复后的首个非零样本重新从 draining 开始，避免用故障前陈旧基线误报增长。

点击额度另有故障分层：Redis reservation adapter 的 Redis、返回 null、baseline 查询和 seed 异常全部固定 fail-open；`app.analytics.quota.fail-open` 只控制 Redirect 外层仍收到的 Platform quota 查询或端口异常。额度 key 首次建立以已落库 PV 为 baseline，因此尚未 flush 的访问和 fail-open 窗口可能造成低估。

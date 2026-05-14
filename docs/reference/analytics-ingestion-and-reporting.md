# 统计采集与报表链路

## 业务目标

Analytics 接收 Redirect 的访问事件，异步构建 PV、UV、维度统计、访问明细和报表。跳转链路优先保证可用性，因此访问事件写入默认 fail-open：统计失败不应阻断用户跳转。

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
  <text class="small" x="875" y="128" text-anchor="middle">写 dirty stream</text>
  <text class="small" x="875" y="148" text-anchor="middle">成功 ack</text>

  <rect class="job" x="250" y="245" width="180" height="100"/>
  <text class="text" x="340" y="275" text-anchor="middle">AnalyticsFlushJob</text>
  <text class="small" x="340" y="298" text-anchor="middle">消费 dirty stream</text>
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

Redirect 在真实跳转前调用 `VisitRecorderPort.recordVisit()`。Analytics 的实现是 `AnalyticsVisitEventService`：

- 从 `RedirectVisitRecord` 读取 tenantId、linkId、applicationId、domainId、code、originalUrl 和访问上下文。
- 转为 `RedirectVisitEvent`。
- 调用 `AnalyticsVisitEventAppender.append()`。
- 如果配置为 fail-open，写入失败只记录 debug，不影响跳转。

`RedisAnalyticsVisitEventAppender` 写 Redis Stream 前会：

- 按 occurredAtMillis 计算 UTC day。
- 规范化 Referer、语言、User-Agent、设备、UTM。
- 计算 `visitorKey = sha256(day|ip|ua|salt)`，用于日 UV。
- 计算 `ipHash = sha256(ip|salt)`，用于明细排障关联，不落明文 IP。
- 对 stream 做近似 trim，限制长度。

## 聚合投影

`AnalyticsRedirectEventProjectorJob` 消费访问 stream：

- 只投影带 visitorKey 的记录。
- 调用 `AnalyticsRedisAggregateWriter.write()` 写 Redis 聚合。
- 成功后 ack。
- 如果某条失败，会先 ack 已处理记录并停止本轮，让失败记录留待后续重试。

`AnalyticsRedisAggregateWriter` 维护：

- link 日 PV：Redis string `INCR`。
- link 日 UV：Redis HyperLogLog。
- tenant/application/domain scope UV。
- active link set。
- dirty stream，供 flush job 找到需要落库的统计键。
- 可选维度统计：维度 PV hash 和维度 UV HLL。

## 落库与明细

`AnalyticsFlushJob`：

- 定时回刷最近 N 天。
- 消费 link stats dirty stream，把 Redis PV 和 HLL UV upsert 到 `link_stats_daily`。
- 消费 scope dirty stream，把租户、应用、域名统计 upsert 到 scope stats 表。
- 使用 ShedLock 防止多实例重复执行。

`AnalyticsDimensionFlushJob`：

- 仅在 dimensions enabled 时运行。
- 消费维度 dirty stream。
- 扫描 active link 和维度类型，把 Redis hash/HLL 写入 `link_stats_dim_daily`。

`AnalyticsEventIngestJob`：

- 仅在 `analytics.events.enabled=true` 时运行。
- 创建并消费 Redis Stream consumer group。
- 优先处理本 consumer pending，再接管闲置 pending，最后读取新消息。
- 按 sampleRate 决定是否保存访问明细。
- insert ignore 成功后 ack。
- 数据完整性异常时逐条隔离 poison record，写 dead letter 后 ack poison。
- 普通 DB 异常保留 pending，等待重试。

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

## 访问明细导出审批

访问明细导出不是直接下载。`AnalyticsExportRequestService.requestLinkEventExport()` 会：

1. 调 `ShortLinkReadPort.findOwnership()` 校验短链属于当前租户。
2. 如果路径带 applicationId，校验短链属于该应用。
3. 默认时间范围是最近 1 天。
4. 校验 `from <= to`。
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

统计链路是最终一致。Redirect 写访问事件后，Redis Stream、Redis 聚合、MySQL 报表之间存在异步延迟。基础 PV/UV 依赖投影和 flush job；访问明细还受 `events.enabled` 和 `sampleRate` 影响。统计系统故障默认不阻断跳转，但会造成报表延迟或缺失，需要通过 stream pending、dead letter 和 dirty stream 状态排查。

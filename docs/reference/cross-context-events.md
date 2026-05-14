# 跨上下文事件与一致性链路

## 业务目标

LinkForge 是模块化单体，不是微服务。各上下文在同一进程内运行，但仍通过 contracts 和集成事件保持边界。跨上下文一致性设计有两个核心目标：

1. 跳转正确性不能依赖异步投影。
2. 缓存驱逐、统计目录、审计等副作用不能破坏主事务。

## 总览图

<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="530" viewBox="0 0 1080 530" role="img" aria-label="跨上下文事件与一致性链路">
  <defs>
    <marker id="arrow-event" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .contract{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .event{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .ok{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-event)}
    </style>
  </defs>
  <rect class="box" x="40" y="55" width="170" height="92"/>
  <text class="text" x="125" y="85" text-anchor="middle">Shortlink 写侧</text>
  <text class="small" x="125" y="108" text-anchor="middle">创建 / 更新 / 归档</text>
  <text class="small" x="125" y="128" text-anchor="middle">短链事实数据</text>

  <rect class="event" x="280" y="45" width="190" height="112"/>
  <text class="text" x="375" y="75" text-anchor="middle">领域事件</text>
  <text class="small" x="375" y="98" text-anchor="middle">ShortLinkDomainEventDispatcher</text>
  <text class="small" x="375" y="118" text-anchor="middle">ShortLinkEventFactory</text>
  <text class="small" x="375" y="138" text-anchor="middle">IntegrationEventStore</text>

  <rect class="event" x="540" y="45" width="190" height="112"/>
  <text class="text" x="635" y="75" text-anchor="middle">AfterCommit</text>
  <text class="small" x="635" y="98" text-anchor="middle">PostCommitHookPort</text>
  <text class="small" x="635" y="118" text-anchor="middle">事务提交后</text>
  <text class="small" x="635" y="138" text-anchor="middle">驱逐 Redirect cache</text>

  <rect class="ok" x="800" y="55" width="190" height="92"/>
  <text class="text" x="895" y="85" text-anchor="middle">Redirect 缓存</text>
  <text class="small" x="895" y="108" text-anchor="middle">正缓存 / 负缓存</text>
  <text class="small" x="895" y="128" text-anchor="middle">不是事实来源</text>

  <rect class="contract" x="40" y="245" width="170" height="92"/>
  <text class="text" x="125" y="275" text-anchor="middle">contracts/*</text>
  <text class="small" x="125" y="298" text-anchor="middle">ShortLinkReadPort</text>
  <text class="small" x="125" y="318" text-anchor="middle">ApplicationScopePort</text>

  <rect class="box" x="280" y="235" width="190" height="112"/>
  <text class="text" x="375" y="265" text-anchor="middle">Redirect 回源</text>
  <text class="small" x="375" y="288" text-anchor="middle">host + code</text>
  <text class="small" x="375" y="308" text-anchor="middle">Shortlink 权威读端口</text>
  <text class="small" x="375" y="328" text-anchor="middle">缓存 miss 时同步读取</text>

  <rect class="event" x="540" y="235" width="190" height="112"/>
  <text class="text" x="635" y="265" text-anchor="middle">Analytics Catalog Projector</text>
  <text class="small" x="635" y="288" text-anchor="middle">消费 integration_events</text>
  <text class="small" x="635" y="308" text-anchor="middle">维护统计目录</text>
  <text class="small" x="635" y="328" text-anchor="middle">失败可重试 / dead letter</text>

  <rect class="box" x="800" y="245" width="190" height="92"/>
  <text class="text" x="895" y="275" text-anchor="middle">Analytics 查询补全</text>
  <text class="small" x="895" y="298" text-anchor="middle">Top links 调 Shortlink</text>
  <text class="small" x="895" y="318" text-anchor="middle">删除后 deleted=true</text>

  <path class="line" d="M210 101 H280"/>
  <path class="line" d="M470 101 H540"/>
  <path class="line" d="M730 101 H800"/>
  <path class="line" d="M210 291 H280"/>
  <path class="line" d="M470 291 H540"/>
  <path class="line" d="M730 291 H800"/>
</svg>

## contracts 边界

跨上下文共享语言放在 `server/contracts/*`：

- `contract-shortlink`
  - `ShortLinkReadPort`：Redirect 回源、Analytics/Governance 校验归属、Top 链接补全。
- `contract-redirect`
  - `LinkCachePort`、`LinkMetaSourcePort`：Redirect 缓存和元数据读取契约。
- `contract-analytics`
  - `VisitRecorderPort`：Redirect 记录访问事件。
  - `ApplicationClickQuotaReservationPort`：Redirect 预留点击额度。
  - `ApplicationClickUsagePort`：点击额度 seed 基线。
- `contract-platform`
  - `ApplicationScopePort`：应用存在、应用域名授权、应用额度。
  - `DomainHostnameLookupPort`：domainId 和 hostname 互查。
  - `LegacyApplicationProvisioningPort`：历史短链默认应用/域名补齐。
- `contract-governance`
  - `ApprovalSubmissionPort`：提交敏感操作审批。
  - `ApprovalExecutionPort`：审批通过后的业务执行器。

这些 contract 模块表达跨上下文稳定语义，不复用其他上下文的 domain/application/infrastructure 类型。

## 短链事件

Shortlink 聚合记录领域事件，应用层发布到集成事件表：

1. `ShortLink` 聚合在创建、更新、归档、恢复、删除时记录事件。
2. `ShortLinkDomainEventDispatcher` 从聚合取出事件。
3. `ShortLinkEventPublisherAdapter` 转到基础设施发布器。
4. `ShortLinkEventFactory` 构造 `ShortLinkPublicSnapshot`。
5. `ShortLinkEventAppender` 写 `IntegrationEventStore`。
6. `MybatisIntegrationEventStore` 落库。

事件 payload 包含 tenantId、linkId、code、hostname、originalUrl、enabled、expiresAt、preview、query 策略、applicationId、domainId、archivedAt 等公开快照。

## 事务后缓存驱逐

短链写操作不能在事务提交前驱逐 Redirect 缓存，否则可能出现：

- 数据库事务回滚。
- Redis 缓存已被驱逐。
- 下一次跳转回源读到旧数据或状态不稳定。

因此 Shortlink 使用：

- `server/foundation/core/src/main/java/com/linkforge/foundation/tx/PostCommitHookPort.java`
- `server/foundation/runtime-tx/src/main/java/com/linkforge/foundation/runtime/tx/SpringPostCommitHookAdapter.java`
- `server/foundation/runtime-tx/src/main/java/com/linkforge/foundation/runtime/tx/AfterCommit.java`

写操作调用 `postCommitHookPort.run(() -> redirectCacheSync.evict(...))`。有活跃事务时注册 after-commit hook；没有事务时直接执行。

## 跳转正确性路径

Redirect 的正确性路径是同步的：

1. 查 Redis 正/负缓存。
2. 缓存 miss 时调用 Shortlink 权威读端口。
3. 回源命中后写正缓存。
4. 回源未命中后写负缓存。

系统没有依赖 “Shortlink 事件投影到 Redirect 读模型” 的正确性通道。事件投影可以用于目录、统计、恢复和辅助读模型，但不能替代权威回源。

## Analytics 目录投影

`ShortLinkCatalogProjectorJob` 消费集成事件：

- 从 `IntegrationEventStore.listAfterSeq()` 读取新事件。
- 解析 Shortlink 事件 payload。
- 维护 Analytics 的短链目录或辅助读模型。
- 失败时记录 dead letter，避免一个坏事件永久阻塞全部投影。

Analytics 查询 Top 链接时仍会通过 `AnalyticsLinkSummaryEnricher` 调 Shortlink 读端口补齐摘要；如果短链已不存在，返回 `deleted=true`。这说明统计报表对短链展示信息的补全仍尊重 Shortlink 权威来源。

## 源码分析

- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java`
  - 从聚合取出领域事件并发布。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventPublisherAdapter.java`
  - 应用层事件发布端口到基础设施的适配。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactory.java`
  - 构造公开事件快照。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventAppender.java`
  - 写集成事件表。
- `server/foundation/runtime-persistence/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationEventStore.java`
  - 集成事件存储实现。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java`
  - 实现 `ShortLinkReadPort`。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java`
  - Redirect 回源使用的权威元数据读取适配。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/redirect/RedirectCacheSyncAdapter.java`
  - Shortlink 写侧调用 Redirect cache port 的适配器。
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java`
  - Analytics 目录投影作业。

## 排障建议

- 跳转结果不符合预期：优先查 Shortlink 数据和 Redirect 缓存，确认写操作后是否执行 after-commit 驱逐。
- 报表 Top 链接缺少摘要：查 Shortlink 读端口补全和 Analytics 目录投影，不要先怀疑 Redirect。
- 集成事件堆积：查 `integration_events`、consumer checkpoint、dead letter。
- Redis 故障：跳转可回源 Shortlink，但性能会下降；统计和点击额度可能进入 fail-open 或延迟。

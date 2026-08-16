# 短链管理链路

## 业务目标

Shortlink 是短链真相写侧，拥有短链生命周期、目标地址、短码、标签、跳转策略、预览开关、不可用落地页、query 转发策略和变更事件。Redirect、Analytics、Governance 都不能直接写短链表，只能通过 Shortlink 暴露的用例或契约读取必要信息。

## 入口 API

控制台入口：

- `POST /api/v1/links`
- `POST /api/v1/applications/{applicationId}/links`
- `GET /api/v1/links`
- `GET /api/v1/applications/{applicationId}/links`
- `GET /api/v1/links/{id}`
- `PUT /api/v1/links/{id}`
- `POST /api/v1/links/{id}/archive`
- `POST /api/v1/links/{id}/restore`
- `DELETE /api/v1/links/{id}`
- `POST /api/v1/links/import`
- `POST /api/v1/applications/{applicationId}/links/import`
- `GET /api/v1/links/export`
- `GET /api/v1/applications/{applicationId}/links/export`

OpenAPI 入口见 [OpenAPI 与 API Key 链路](openapi-api-key.md)。

## 创建流程图

<svg xmlns="http://www.w3.org/2000/svg" width="1040" height="500" viewBox="0 0 1040 500" role="img" aria-label="短链创建链路">
  <defs>
    <marker id="arrow-shortlink" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .domain{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .warn{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .ok{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-shortlink)}
    </style>
  </defs>
  <rect class="box" x="30" y="55" width="150" height="70"/>
  <text class="text" x="105" y="84" text-anchor="middle">Controller</text>
  <text class="small" x="105" y="106" text-anchor="middle">用户 / OpenAPI</text>

  <rect class="box" x="235" y="45" width="190" height="90"/>
  <text class="text" x="330" y="75" text-anchor="middle">ShortLinkActorScopeResolver</text>
  <text class="small" x="330" y="98" text-anchor="middle">解析 actor 范围</text>
  <text class="small" x="330" y="118" text-anchor="middle">路径 applicationId 约束</text>

  <rect class="box" x="480" y="45" width="205" height="90"/>
  <text class="text" x="582" y="75" text-anchor="middle">CreateShortLinkCommandHandler</text>
  <text class="small" x="582" y="98" text-anchor="middle">校验请求 / 预留额度</text>
  <text class="small" x="582" y="118" text-anchor="middle">生成 id 和 code</text>

  <rect class="domain" x="755" y="35" width="220" height="110"/>
  <text class="text" x="865" y="65" text-anchor="middle">ShortLink 聚合</text>
  <text class="small" x="865" y="88" text-anchor="middle">ShortCode / HttpUrl</text>
  <text class="small" x="865" y="108" text-anchor="middle">redirectStatus / preview</text>
  <text class="small" x="865" y="128" text-anchor="middle">query forward / lifecycle</text>

  <rect class="warn" x="235" y="210" width="190" height="95"/>
  <text class="text" x="330" y="240" text-anchor="middle">Platform 端口</text>
  <text class="small" x="330" y="262" text-anchor="middle">应用和域名授权</text>
  <text class="small" x="330" y="282" text-anchor="middle">月发链额度预留</text>

  <rect class="box" x="480" y="200" width="205" height="115"/>
  <text class="text" x="582" y="232" text-anchor="middle">Repository + Tags</text>
  <text class="small" x="582" y="254" text-anchor="middle">insert short_link</text>
  <text class="small" x="582" y="274" text-anchor="middle">customCode 唯一冲突转换</text>
  <text class="small" x="582" y="294" text-anchor="middle">重建标签关系</text>

  <rect class="ok" x="755" y="205" width="220" height="105"/>
  <text class="text" x="865" y="236" text-anchor="middle">事件与缓存</text>
  <text class="small" x="865" y="258" text-anchor="middle">发布短链集成事件</text>
  <text class="small" x="865" y="278" text-anchor="middle">outbox + 提交后快速驱逐</text>
  <text class="small" x="865" y="298" text-anchor="middle">返回 LinkDto</text>

  <rect class="box" x="235" y="380" width="740" height="64"/>
  <text class="text" x="605" y="407" text-anchor="middle">更新 / 归档 / 恢复 / 删除复用同一原则</text>
  <text class="small" x="605" y="429" text-anchor="middle">读取聚合 -> 执行业务规则 -> 乐观锁写入 -> 发布事件 -> 提交后驱逐缓存</text>

  <path class="line" d="M180 90 H235"/>
  <path class="line" d="M425 90 H480"/>
  <path class="line" d="M685 90 H755"/>
  <path class="line" d="M330 135 V210"/>
  <path class="line" d="M582 135 V200"/>
  <path class="line" d="M685 257 H755"/>
  <path class="line" d="M865 310 V380"/>
</svg>

## 创建短链规则

- `createdBy` 必须有效，用户或 API Key 都会转换成显式 actor。
- `applicationId` 和 `domainId` 必须同时为空或同时提供。
- 应用级短链必须通过 Platform 校验应用和域名授权。
- 如果应用设置月发链额度，先按 UTC 月度窗口预留额度，额度不足直接失败。
- 未传 `customCode` 时使用 Snowflake ID 的 Base62 形式生成短码。
- 自定义短码唯一性规则：
  - 非应用级短链按 `code` 唯一。
  - 应用级短链按 `domainId + code` 唯一。
- `ShortCode` 长度 6-32，只允许 ASCII 字母数字，大小写敏感。
- `HttpUrl` 只允许绝对 `http` 或 `https` URL，host 必须存在。
- `redirectStatusCode` 只允许 301、302 或空。
- 标签最多 20 个，标签名最长 64。

## 更新、审批、归档和删除

### 普通更新

`UpdateShortLinkCommandHandler.handle()` 先按 `tenantId + linkId` 读聚合，不存在返回 `LINK_NOT_FOUND`。普通更新统一调用 `link.applyUpdate(...)`；聚合在一个命名行为中完成归档守卫、字段校验、版本推进、更新时间和单条更新事件。局部更新支持：

- lifecycleState
- originalUrl
- note
- enabled
- expiresAt / clearExpiresAt
- redirectStatusCode / clearRedirectStatusCode
- previewEnabled
- unavailableLandingUrl
- queryForwardMode / clearQueryForwardMode
- queryForwardAllowlist
- tags

写入使用乐观锁，失败返回 `LINK_STALE_WRITE`。更新成功后发布事件、在同一事务写缓存失效 outbox，并在提交后尝试快速驱逐 Redirect 缓存。

clear 字段是显式协议，而不是“空字符串自动清除”：

| 字段 | 设置值 | 清除标志 | 同时传值与 clear |
| --- | --- | --- | --- |
| expiresAt | `expiresAt` | `clearExpiresAt` | 参数错误 |
| redirect status | `redirectStatusCode` | `clearRedirectStatusCode` | 参数错误 |
| query mode | `queryForwardMode` | `clearQueryForwardMode` | 参数错误 |

冲突会在产生审批、标签、事件或 outbox 副作用前拒绝。空 `unavailableLandingUrl` 清空该字段，空 allowlist 清空当前覆盖值，`note` 的空字符串是有效业务值；字段和标签都没有实际变化时幂等返回，不写库、不发事件，也不登记缓存失效。

### 目标地址变更审批

应用级短链修改 `originalUrl` 时会进入审批分支：

- 必须由有效 `UserActor` 发起，API Key 不能发起该审批。
- actor 租户必须匹配短链租户。
- 只允许单独提交目标地址变更，不能夹带标签、启用状态、过期时间等其他有效修改。
- 通过 `ApprovalSubmissionPort.requestLinkDestinationChangeApproval()` 提交 `PUBLIC_LINK_DESTINATION_CHANGE`。
- 当前短链不会立即修改，接口返回当前生效的 LinkDto，并附带 `pendingApproval`、`approvalRequestId` 和 `requestedOriginalUrl`。

审批执行见 [审批与审计链路](governance-approval-audit.md)。

### 生命周期命令

- 归档：聚合 `archive()` 设置 `archivedAtUtc`、推进一次版本并产生 `ShortLinkArchived`；重复归档幂等返回。
- 恢复：聚合 `restore()` 清空 `archivedAtUtc`、推进一次版本并产生 `ShortLinkRestored`；未归档时幂等返回。
- 删除：聚合 `delete()` 要求短链已归档，记录删除意图、推进一次版本并产生 `ShortLinkDeleted`；应用层再删除标签关系并按变化前版本 CAS 删除短链。

审批执行调用 `approveDestinationChange()`，由聚合校验未归档、已绑定 domain 且发布阶段为 `ACTIVE`。ownership reconciliation 使用 `reconcileOwnership()`，只允许把 application/domain 双空的 legacy link 绑定到一对正数 ID，不允许清空或换绑，并在事件中同时保留变化前后 scope。聚合不再公开 guard、字段 setter、`applyPatch()`、`markUpdated()`、`markDeleted()` 或直接版本推进；所有生产写入只能通过命名 mutation。

所有持久化更新都接收已经由一个命名行为推进一次的新版本，SQL 使用 `newVersion - 1` 做乐观锁条件并保存 `newVersion`。单个用例不得在一次 repository write 前连续执行多个命名 mutation。

### 历史所有权对账

`ShortLinkOwnershipReconciliationService.reconcile()` 是单链接 ownership 迁移的唯一公共写入口。它先调用 `ApplicationScopePort.requireApplicationAndDomainAuthorized()` 校验 ACTIVE 应用、ACTIVE 域名、租户归属和授权，再按 `tenantId + linkId` 读取聚合并调用 `reconcileOwnership()`。成功的仓储更新在同一条 CAS SQL 中保存 application/domain 和新版本；版本竞争返回 `RETRYABLE_CONFLICT`，相同目标返回 `ALREADY_RECONCILED`，已有其他 ownership 和不存在记录分别返回可观察终态。

CAS 成功后的当月 quota 校准、`ShortLinkUpdated` 集成事件、legacy unscoped identity 与新 domain identity 的缓存失效 outbox 都加入同一事务。quota 校准与创建路径使用同一个 MySQL 月度 named lock：月份基线首次建立时已包含刚迁移的链接，已有基线时才递增一次。只有一个版本 CAS 可以成功，因此并发、重试或事务回滚不会重复 quota、事件和 outbox；after-commit 驱逐仍只是降低旧缓存可见窗口的快速路径。

`LegacyShortLinkBackfillService.reconcileNextBatch()` 只负责有界调度：先通过 `LegacyApplicationProvisioningPort` 获取稳定 legacy binding，再在 `shortlink_ownership_backfill_checkpoints` 上锁定租户 checkpoint，以 `id` keyset 把最多 `batchSize` 条双空 ownership 记录写入 `shortlink_ownership_backfill_items`。每条 work item 都调用上述公共 reconciliation 用例，不存在 bulk ownership SQL。checkpoint、单链接事务和结果记录分别提交；若进程在单链接提交后、结果记录前崩溃，work item 仍为 `PENDING`，重启会得到 `ALREADY_RECONCILED` 并补记终态，不重复业务副作用。

work item 状态区分 `PENDING`、`RETRYABLE`、`PERMANENT_FAILURE`、`RECONCILED`、`ALREADY_RECONCILED` 和 `NOT_FOUND`。新 PENDING 项优先，重复失败项按更新时间轮转，避免单条重试阻塞后续发现；`progress().converged()` 表示本轮扫描已耗尽且没有自动重试项，永久失败仍保留在计数和 `last_error` 中供人工处理。扫描耗尽时会从零检查尚未入队的双空记录，覆盖并发提交但 ID 落在旧 cursor 之前的记录。

### 权限和作用域

| 主体 | 非应用级链接 | 应用级链接 | 生命周期命令 |
| --- | --- | --- | --- |
| 普通用户 | 只能浏览/修改自己创建的 USER 链接 | 不可经 body 或路径扩大范围 | 不可归档、恢复、删除 |
| 租户管理员 | 可管理本租户链接 | 可在路径与 body applicationId 一致时管理 | 可归档、恢复、删除 |
| 已绑定 API Key | 仅绑定 application 的范围 | 仅绑定 application | 不走控制台生命周期入口 |
| 未绑定历史 API Key | 认证阶段拒绝 | 认证阶段拒绝 | 不适用 |

越权和跨租户链接统一以 `LINK_NOT_FOUND` 隐藏资源存在性。`archive/restore/delete(long tenantId, ...)` 是受 Controller `TENANT_ADMIN` 保护的可信内部入口，直接调用者必须自行维持该前提。

## CSV 导入导出

- 导入由 `ImportShortLinksCsvCommandHandler` 处理，每行使用 `RequiresNewTransactionPort` 独立事务。
- 某一行失败只记录行级错误，不影响其他行。
- 应用级导入必须提供 `domainId`，也支持 CSV 行里通过 hostname 找域名。
- `expiresAt` 支持 ISO-8601 Instant/OffsetDateTime，也兼容 legacy `LocalDateTime` 并按 UTC 处理。
- 导出由 `ExportShortLinksCsvQueryHandler` 复用搜索逻辑，输出 linkId、applicationId、domainId、hostname、code、originalUrl、note、enabled、expiresAt、tags。
- 所有用户可控文本列统一经过 `CsvFormulaNeutralizer`。保留原始空白和 UTF-8 内容做输出，但若第一个非空白/BOM 字符是 `=`、`+`、`-` 或 `@`，会在原单元格前添加单引号；数值、布尔和时间字段不改写。随后仍由 Commons CSV 处理逗号、双引号和 CR/LF quoting。

标签和 query allowlist 的上限是规范化行为：标签最多 20，allowlist 最多 50；超出项会被截断而不是稳定拒绝。allowlist 逗号序列化上限 1024，匹配大小写敏感。`HttpUrl` 的验证只是 URI/http(s)/host/2048 长度校验，不意味着 DNS 可达、目标非私网或跳转目标已通过安全审查。

## 源码分析

### HTTP 层

- `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
  - 控制台短链 CRUD、导入导出入口。
  - 应用级路径会把 path applicationId 包进 scoped request。
- `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/TagController.java`
  - 标签列表和创建入口。
- `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
  - OpenAPI 发链和查询入口，使用 API Key actor。

### 应用层编排

- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`
  - 聚合多个用例 handler。
  - `createForUser()`、`createForApiKey()` 先走 `ShortLinkActorScopeResolver`。
  - `archive()`、`restore()`、`delete()`、`update()` 分别委派命令 handler。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolver.java`
  - 用户普通路径不自动进入应用范围；如 body 带应用信息，需要管理员权限。
  - 用户应用路径要求 path applicationId 和 body applicationId 一致。
  - API Key 绑定应用时只能访问绑定应用。
  - API Key 未绑定应用时会被拒绝，不再允许通过路径或 body 临时指定应用范围。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
  - 校验应用/域名成对出现。
  - 调 `ApplicationScopePort.requireApplicationAndDomainAuthorized()`。
  - 调 `ApplicationLinkQuotaReservationPort.tryReserveMonthlyLink()`。
  - 构造 `ShortLink` 聚合并写库。
  - 调 `SetLinkTagsCommandHandler`，发布领域事件，在同一事务写缓存失效 outbox，并在提交后快速驱逐缓存。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
  - 处理审批分支、局部更新、乐观锁、事件和缓存驱逐。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/migration/ShortLinkOwnershipReconciliationService.java`
  - 单链接 ownership 权威写用例，协调 Platform scope、聚合 CAS、quota、事件和双 identity 缓存失效。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/migration/LegacyShortLinkBackfillService.java`
  - 有界取得 durable work item，每条只委派公共 reconciliation 用例，并返回可恢复进度。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/SearchShortLinksQueryHandler.java`
  - 支持 archived、enabled、keyword、tag、applicationId 过滤。
  - 限制 offset 最大 100000。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/SetLinkTagsCommandHandler.java`
  - 先删除 linkId 全部标签关系，再按请求重建。

### 领域层

- `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
  - 聚合根，保护 id、tenantId、code、URL、生命周期、归档状态、跳转策略等不变量。
  - 创建、更新、归档、恢复、删除都会记录领域事件。
- `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortCode.java`
  - 短码格式和值语义。
- `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/HttpUrl.java`
  - 目标 URL 校验。
- `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/QueryForwardMode.java`
  - query 转发模式：`OFF`、`ALLOWLIST`、`ALL`。
- `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/QueryForwardAllowlist.java`
  - allowlist 去重、限制数量和序列化。

### 基础设施

- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repository/MybatisShortLinkRepository.java`
  - 短链写侧 repository。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/quota/MybatisApplicationLinkQuotaReservationPort.java`
  - 使用 MySQL named lock 保护月发链额度预留。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repository/MybatisLegacyShortLinkBackfillStore.java`
  - 在租户 checkpoint 行锁内做 keyset 发现、持久化 work item 和聚合状态计数；每批至少推进一次发现，避免持续 RETRYABLE 项阻塞后续 legacy link。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/redirect/RedirectCacheSyncAdapter.java`
  - 调 Redirect 缓存端口执行驱逐。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventAppender.java`
  - 把短链事件写入集成事件表。

## 一致性设计

Shortlink 的写操作在业务事务内追加集成事件和缓存失效 outbox。提交后 `PostCommitHookPort` 会尝试 `redirectCacheSync.evict(...)` 快速路径；该动作失败不回滚已提交业务，outbox worker 负责后续重试。同一缓存 identity 重复入队会推进 generation，worker 只按自己读取到的 generation CAS 完成或保存失败，因此持有旧 generation 的 worker 不能覆盖并发产生的新失效意图。驱逐允许重复，事务回滚不会留下 outbox 或运行 after-commit。Redirect 缓存不是事实来源，缓存未命中时总会回源 `ShortLinkReadPort`。

发链额度预留与插入短链处于同一事务，后续标签/事件失败会一起回滚；MySQL named lock 获取失败和真正额度耗尽都返回未获得名额。领域事件 dispatcher 会 destructive pull 聚合事件并马上发布，因此一个聚合不应积累多轮状态变化后再延迟 dispatch；事件消费者必须接受重放，不存在 exactly-once 承诺。

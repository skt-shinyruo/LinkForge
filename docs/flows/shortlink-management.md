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
- `PATCH /api/v1/links/{id}`
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
  <text class="small" x="865" y="278" text-anchor="middle">事务提交后驱逐 Redirect cache</text>
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

`UpdateShortLinkCommandHandler.handle()` 先按 `tenantId + linkId` 读聚合，不存在返回 `LINK_NOT_FOUND`。归档短链调用 `link.requireNotArchivedForUpdate()` 拒绝更新。局部更新支持：

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

写入使用乐观锁，失败返回 `LINK_STALE_WRITE`。更新成功后发布事件，事务提交后驱逐 Redirect 缓存。

### 目标地址变更审批

应用级短链修改 `originalUrl` 时会进入审批分支：

- 必须由有效 `UserActor` 发起，API Key 不能发起该审批。
- actor 租户必须匹配短链租户。
- 只允许单独提交目标地址变更，不能夹带标签、启用状态、过期时间等其他有效修改。
- 通过 `ApprovalSubmissionPort.requestLinkDestinationChangeApproval()` 提交 `PUBLIC_LINK_DESTINATION_CHANGE`。
- 当前短链不会立即修改，接口返回旧 LinkDto。

审批执行见 [审批与审计链路](governance-approval-audit.md)。

### 生命周期命令

- 归档：`ArchiveShortLinkCommandHandler` 设置 `archivedAtUtc`；重复归档是幂等返回。
- 恢复：`RestoreShortLinkCommandHandler` 清空 `archivedAtUtc`；未归档时幂等返回。
- 删除：`DeleteShortLinkCommandHandler` 要求短链已归档，先删除标签关系，再按版本删除短链。

## CSV 导入导出

- 导入由 `ImportShortLinksCsvCommandHandler` 处理，每行使用 `RequiresNewTransactionPort` 独立事务。
- 某一行失败只记录行级错误，不影响其他行。
- 应用级导入必须提供 `domainId`，也支持 CSV 行里通过 hostname 找域名。
- `expiresAt` 支持 ISO-8601 Instant/OffsetDateTime，也兼容 legacy `LocalDateTime` 并按 UTC 处理。
- 导出由 `ExportShortLinksCsvQueryHandler` 复用搜索逻辑，输出 linkId、applicationId、domainId、hostname、code、originalUrl、note、enabled、expiresAt、tags。

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
  - API Key 未绑定应用时，应用级操作必须显式提供 applicationId。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
  - 校验应用/域名成对出现。
  - 调 `ApplicationScopePort.requireApplicationAndDomainAuthorized()`。
  - 调 `ApplicationLinkQuotaReservationPort.tryReserveMonthlyLink()`。
  - 构造 `ShortLink` 聚合并写库。
  - 调 `SetLinkTagsCommandHandler`，发布领域事件，事务提交后驱逐缓存。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
  - 处理审批分支、局部更新、乐观锁、事件和缓存驱逐。
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
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/redirect/RedirectCacheSyncAdapter.java`
  - 调 Redirect 缓存端口执行驱逐。
- `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventAppender.java`
  - 把短链事件写入集成事件表。

## 一致性设计

Shortlink 的写操作把缓存驱逐延迟到事务提交后执行。代码通过 `PostCommitHookPort` 调用 `redirectCacheSync.evict(...)`，避免数据库回滚但 Redis 已被驱逐或写入错误状态。Redirect 缓存不是事实来源，缓存未命中时总会回源 Shortlink 读端口。

# 平台控制面链路

## 业务目标

Platform 是 LinkForge 的控制面，负责租户下的应用、域名、授权、默认策略和配额。Shortlink 创建应用级短链、Accounts 创建 API Key、Redirect 校验点击额度时都不直接读取 Platform 表，而是通过 `contract-platform` 暴露的端口进入 Platform。

核心模型是：

- `tenant`：租户边界。
- `application`：内部系统或业务应用。
- `domain`：短链访问域名，可以是租户共享域名或应用专属域名。
- `application_policy`：应用默认跳转策略。
- `application_quota`：月发链额度和月点击额度。

## 流程图

<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="455" viewBox="0 0 1000 455" role="img" aria-label="平台控制面链路">
  <defs>
    <marker id="arrow-platform" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .domain{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .rule{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .port{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-platform)}
    </style>
  </defs>
  <rect class="box" x="35" y="60" width="155" height="72"/>
  <text class="text" x="112" y="90" text-anchor="middle">租户管理员</text>
  <text class="small" x="112" y="112" text-anchor="middle">控制台 / API</text>

  <rect class="box" x="255" y="45" width="190" height="102"/>
  <text class="text" x="350" y="76" text-anchor="middle">Platform Controllers</text>
  <text class="small" x="350" y="98" text-anchor="middle">/applications</text>
  <text class="small" x="350" y="118" text-anchor="middle">/domains</text>
  <text class="small" x="350" y="138" text-anchor="middle">/domain-authorizations</text>

  <rect class="box" x="515" y="45" width="205" height="102"/>
  <text class="text" x="618" y="76" text-anchor="middle">PlatformControlPlaneService</text>
  <text class="small" x="618" y="98" text-anchor="middle">校验租户 actor</text>
  <text class="small" x="618" y="118" text-anchor="middle">委派 provisioning</text>
  <text class="small" x="618" y="138" text-anchor="middle">封装授权错误</text>

  <rect class="domain" x="785" y="35" width="170" height="122"/>
  <text class="text" x="870" y="66" text-anchor="middle">Platform Domain</text>
  <text class="small" x="870" y="88" text-anchor="middle">Application</text>
  <text class="small" x="870" y="108" text-anchor="middle">Domain</text>
  <text class="small" x="870" y="128" text-anchor="middle">Policy / Quota</text>

  <rect class="rule" x="255" y="225" width="190" height="100"/>
  <text class="text" x="350" y="254" text-anchor="middle">DomainAuthorizationPolicy</text>
  <text class="small" x="350" y="276" text-anchor="middle">ACTIVE 域名</text>
  <text class="small" x="350" y="296" text-anchor="middle">专属域名归属</text>
  <text class="small" x="350" y="316" text-anchor="middle">共享域名授权关系</text>

  <rect class="port" x="515" y="220" width="205" height="112"/>
  <text class="text" x="618" y="250" text-anchor="middle">PlatformApplicationScopeAdapter</text>
  <text class="small" x="618" y="272" text-anchor="middle">ApplicationScopePort</text>
  <text class="small" x="618" y="292" text-anchor="middle">DomainHostnameLookupPort</text>
  <text class="small" x="618" y="312" text-anchor="middle">LegacyProvisioningPort</text>

  <rect class="box" x="785" y="210" width="170" height="134"/>
  <text class="text" x="870" y="240" text-anchor="middle">其他上下文</text>
  <text class="small" x="870" y="264" text-anchor="middle">Shortlink 发链授权</text>
  <text class="small" x="870" y="284" text-anchor="middle">Accounts API Key</text>
  <text class="small" x="870" y="304" text-anchor="middle">Redirect 点击额度</text>
  <text class="small" x="870" y="324" text-anchor="middle">Analytics hostname</text>

  <rect class="box" x="515" y="370" width="205" height="55"/>
  <text class="text" x="618" y="392" text-anchor="middle">MyBatis Repositories</text>
  <text class="small" x="618" y="412" text-anchor="middle">applications / domains / quotas</text>

  <path class="line" d="M190 96 H255"/>
  <path class="line" d="M445 96 H515"/>
  <path class="line" d="M720 96 H785"/>
  <path class="line" d="M618 147 V220"/>
  <path class="line" d="M515 275 H445"/>
  <path class="line" d="M720 276 H785"/>
  <path class="line" d="M618 332 V370"/>
</svg>

## 入口 API

- `GET /api/v1/applications`：租户应用列表。
- `POST /api/v1/applications`：创建租户应用。
- `GET /api/v1/domains`：租户域名列表。
- `POST /api/v1/domains/tenant-shared`：创建租户共享域名。
- `GET /api/v1/applications/{applicationId}/domains`：应用可用域名。
- `POST /api/v1/applications/{applicationId}/domains`：创建应用专属域名。
- `POST /api/v1/applications/{applicationId}/domain-authorizations/{domainId}`：把共享域名授权给应用。
- `GET /api/v1/platform/applications`、`GET /api/v1/platform/domains`：平台管理员全局查询。

## 关键业务规则

### 应用创建

- actor 必须属于当前租户。
- `applicationKey` 和 `displayName` 必填。
- `applicationKey` 最大 64 字符，`displayName` 最大 128 字符；HTTP 与应用层校验都以数据库列宽为界，64 可接受、65 稳定返回 400。
- 创建应用时同步创建默认 `ApplicationPolicy` 和 `ApplicationQuota`。
- 默认策略和额度来自 `PlatformDefaults`。

创建在一个事务中写 application、policy 和 quota；`applicationKey` 的唯一约束仍是最终并发裁决，预查不能取代数据库冲突处理。

### 域名创建

- `Hostname.parse()` 会 trim、小写并做 IDN ASCII 转换。
- 禁止 `localhost`、IPv4 字面量、通配符、带端口或路径的字符串。
- 至少两个 label，每个 label 只允许 `[a-z0-9-]`，不能以 `-` 开头或结尾。
- 租户共享域名：`DomainScope.TENANT_SHARED`，不绑定具体应用。
- 应用专属域名：`DomainScope.APPLICATION_DEDICATED`，绑定一个 applicationId。

### 授权判断

`DomainAuthorizationPolicy.requireApplicationCanUseDomain()` 是应用使用域名的核心规则：

- 域名必须是 `ACTIVE`。
- 应用专属域名必须绑定当前应用。
- 租户共享域名必须存在应用授权关系。
- 专属域名不能通过共享授权接口授权给其他应用。

共享域名授权当前是“确保关系存在”的写操作，调用方应按应用层返回的业务结果重试；它不是借由 HTTP 方法名自动获得的分布式幂等保证。`application_policy` 目前作为控制面事实保存，Shortlink 不会自动把它的默认跳转策略覆盖到每一条链接。

## 源码分析

### HTTP 层

- `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminApplicationController.java`
  - `list()`：读取当前租户应用。
  - `create()`：把 HTTP request 转为 `CreateApplicationCommand`，调用控制面服务。
- `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminDomainController.java`
  - `list()`：租户域名列表。
  - `createTenantSharedDomain()`：创建共享域名。
  - `listForApplication()`：查询应用可用域名。
  - `createApplicationDedicatedDomain()`：创建应用专属域名。
  - `authorizeDomain()`：授权共享域名给应用。
- `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/PlatformAdminController.java`
  - 平台管理员全局查询应用和域名。

### 应用层

- `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
  - 对外暴露控制面用例。
  - `requireApplicationExists()` 和 `requireApplicationAndDomainAuthorized()` 给其他上下文复用。
  - 把领域授权异常转换成稳定业务错误。
- `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
  - `createApplication()`：创建应用、默认策略、默认额度。
  - `createTenantSharedDomain()`：创建共享域名。
  - `createApplicationDedicatedDomain()`：先确认应用属于租户，再创建绑定应用的域名。
  - `authorizeDomain()`：只允许共享域名授权给应用。
- `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformApplicationScopeAdapter.java`
  - 实现 `ApplicationScopePort`，供 Shortlink、Accounts、Redirect 调用。
  - 实现 `DomainHostnameLookupPort`，供 Shortlink/Analytics 做 `domainId <-> hostname` 转换。
  - 实现 `LegacyApplicationProvisioningPort`，为历史未应用化短链补齐默认应用和域名绑定。
- `server/platform/application/src/main/java/com/linkforge/platform/application/LegacyApplicationBindingService.java`
  - 在 Platform 自有租户锁行内执行幂等 get-or-reconcile。
  - 创建或复用 ACTIVE 的 `legacy-default` 应用和正确绑定的 ACTIVE 专属域名。
  - 每次把 legacy policy/quota 补齐或收敛到当前默认值；停用、跨租户、错误绑定和错误信任配置明确拒绝。
  - 从 `app.base-url` 派生 synthetic legacy hostname，例如 `legacy-{tenantId}.{baseHost}`。

### 领域对象

- `server/platform/domain/src/main/java/com/linkforge/platform/domain/Application.java`
  - 应用主体，包含 `id`、`tenantId`、`applicationKey`、`displayName`、`status`。
- `server/platform/domain/src/main/java/com/linkforge/platform/domain/Domain.java`
  - 域名主体，包含 `applicationId`、`hostname`、`scope`、`status`、`trustClass`。
- `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationPolicy.java`
  - 应用默认域名范围、默认跳转状态码、默认预览开关。
- `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationQuota.java`
  - 应用月发链额度和月点击额度。
- `server/platform/domain/src/main/java/com/linkforge/platform/domain/Hostname.java`
  - 域名规范化和格式校验。
- `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainAuthorizationPolicy.java`
  - 应用使用域名的授权规则。

### 持久化

- `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/persistence/ApplicationRepositoryMybatisAdapter.java`
  - 应用读写。
- `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/persistence/DomainRepositoryMybatisAdapter.java`
  - 域名读写、共享域名授权关系、应用可用域名查询。
- `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/persistence/ApplicationPolicyRepositoryMybatisAdapter.java`
  - 应用策略写入。
- `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/persistence/ApplicationQuotaRepositoryMybatisAdapter.java`
  - 应用额度写入和查询。

## 对其他链路的影响

- Shortlink 创建应用级短链前调用 `ApplicationScopePort.requireApplicationAndDomainAuthorized()`。
- Accounts 创建 API Key 前调用 `ApplicationScopePort.requireApplicationExists()`。
- Redirect 校验点击额度前调用 `ApplicationScopePort.findApplicationQuota()`。
- Analytics 和 Shortlink 需要展示 hostname 时调用 `DomainHostnameLookupPort`。

Platform 因此是控制面事实来源，但不直接参与跳转 URL 解析；跳转正确性仍由 Shortlink 权威读端口和 Redirect 缓存保证。

## 配额与兼容限制

- `ApplicationQuota` 的月发链和月点击限制按 UTC 月解释；非正数表示不限制。
- Shortlink 发链预留使用 MySQL named lock，锁获取失败与真实额度耗尽当前都会表现为未获得名额，调用方不能从同一个结果推断根因。
- Redirect 点击额度的实时计数由 Analytics Redis adapter 管理，Platform 只发布 quota 配置/视图；Redis adapter 的内部故障固定 fail-open，详见[统计采集与报表](analytics-ingestion-and-reporting.md)。
- `LegacyApplicationBindingService` 是历史未分应用数据的兼容路径，不应成为新业务的默认开通方式。同一租户的首次调用通过 `platform_legacy_binding_locks` 唯一键写锁串行化；返回前必须满足 ACTIVE application、ACTIVE `APPLICATION_DEDICATED` domain、同租户/同应用归属和 `FIRST_PARTY` 信任分类。缺失的资源、policy 或 quota 会在同一事务中补齐，过期 policy/quota 会更新为当前 legacy 默认值；已有 DISABLED 或错误归属资源不会被静默启用或改绑。
- Platform 只发布已验证的 legacy binding，不直接修改 `short_links`。历史 ownership 的 checkpoint、逐链接 CAS、quota 校准、事件和缓存失效全部由 Shortlink 的公共 reconciliation 用例拥有。

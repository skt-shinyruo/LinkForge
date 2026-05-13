# OpenAPI 与 API Key 链路

## 业务目标

OpenAPI 链路让内部系统通过 API Key 创建和查询短链。它和控制台用户链路共用 Shortlink 用例，但认证方式、权限边界和应用范围不同。API Key 的核心作用不是替代用户登录，而是把某个系统限制在其被授权的租户和应用范围内。

## 流程图

<svg xmlns="http://www.w3.org/2000/svg" width="980" height="420" viewBox="0 0 980 420" role="img" aria-label="OpenAPI API Key 链路">
  <defs>
    <marker id="arrow-openapi" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .secure{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .ok{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .warn{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-openapi)}
    </style>
  </defs>
  <rect class="box" x="30" y="45" width="160" height="70"/>
  <text class="text" x="110" y="74" text-anchor="middle">租户管理员</text>
  <text class="small" x="110" y="96" text-anchor="middle">创建 / 轮换 API Key</text>

  <rect class="secure" x="245" y="35" width="185" height="90"/>
  <text class="text" x="338" y="65" text-anchor="middle">ApiKeyService</text>
  <text class="small" x="338" y="88" text-anchor="middle">生成 lfk_id_secret</text>
  <text class="small" x="338" y="108" text-anchor="middle">只保存 secret hash</text>

  <rect class="ok" x="500" y="45" width="180" height="70"/>
  <text class="text" x="590" y="74" text-anchor="middle">客户端保存明文 key</text>
  <text class="small" x="590" y="96" text-anchor="middle">仅创建/轮换时返回</text>

  <rect class="box" x="30" y="210" width="160" height="70"/>
  <text class="text" x="110" y="239" text-anchor="middle">OpenAPI 请求</text>
  <text class="small" x="110" y="261" text-anchor="middle">X-API-Key</text>

  <rect class="secure" x="245" y="195" width="185" height="100"/>
  <text class="text" x="338" y="226" text-anchor="middle">ApiKeyAuthenticationFilter</text>
  <text class="small" x="338" y="249" text-anchor="middle">解析 key</text>
  <text class="small" x="338" y="269" text-anchor="middle">校验租户 ACTIVE</text>

  <rect class="secure" x="500" y="195" width="180" height="100"/>
  <text class="text" x="590" y="226" text-anchor="middle">ApiKeyService.authenticate</text>
  <text class="small" x="590" y="249" text-anchor="middle">prefix / id / secret</text>
  <text class="small" x="590" y="269" text-anchor="middle">状态 / lastUsedAt 节流</text>

  <rect class="warn" x="745" y="195" width="190" height="100"/>
  <text class="text" x="840" y="226" text-anchor="middle">ShortLinkActorScopeResolver</text>
  <text class="small" x="840" y="249" text-anchor="middle">绑定应用只能访问绑定应用</text>
  <text class="small" x="840" y="269" text-anchor="middle">未绑定需显式应用上下文</text>

  <rect class="ok" x="745" y="335" width="190" height="55"/>
  <text class="text" x="840" y="357" text-anchor="middle">复用 Shortlink 用例</text>
  <text class="small" x="840" y="377" text-anchor="middle">创建 / 查询短链</text>

  <path class="line" d="M190 80 H245"/>
  <path class="line" d="M430 80 H500"/>
  <path class="line" d="M190 245 H245"/>
  <path class="line" d="M430 245 H500"/>
  <path class="line" d="M680 245 H745"/>
  <path class="line" d="M840 295 V335"/>
</svg>

## API Key 生命周期

- 创建：`POST /api/v1/api-keys`
- 列表：`GET /api/v1/api-keys`
- 禁用：`POST /api/v1/api-keys/{id}/disable`
- 启用：`POST /api/v1/api-keys/{id}/enable`
- 轮换：`POST /api/v1/api-keys/{id}/rotate`

创建 API Key 必须提供 `applicationId`，Accounts 会通过 Platform 的 `ApplicationScopePort.requireApplicationExists()` 确认应用属于当前租户。返回给客户端的明文 key 格式为 `lfk_{id}_{secret}`，数据库只保存 secret 的密码哈希。明文 key 只在创建和轮换响应中出现。

## OpenAPI 入口

- `POST /api/v1/open/links`
- `POST /api/v1/open/applications/{applicationId}/links`
- `GET /api/v1/open/links`
- `GET /api/v1/open/applications/{applicationId}/links`

这些路径由 `OpenApiSecurityConfig` 单独匹配，只接受 `X-API-Key`。普通 JWT/Cookie 不会在这里生效。

## 关键业务规则

- API Key 认证成功后只验证租户状态；用户状态不参与，因为 API Key 表自身管理启用/禁用。
- API Key 的 `applicationId` 不放入 `AuthPrincipal`，而是放入 `Authentication.details`。
- 控制器用 `PrincipalActorMapper.requireApiKey()` 把 principal + details 转成 `ApiKeyActor`。
- 绑定应用的 API Key 只能访问该应用。
- 未绑定应用的 API Key 创建应用级短链时，必须通过路径或 body 明确 applicationId。
- 禁用的 API Key 会写短 TTL 认证缓存，重新启用或轮换后在事务提交后驱逐缓存。
- `lastUsedAt` 更新带节流，优先用 Redis token 控制写库频率，避免每次 OpenAPI 请求都更新数据库。

## 源码分析

### 管理 API Key

- `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/ApiKeyAdminController.java`
  - API Key 管理 HTTP 入口。
  - 创建、列表、启用、禁用、轮换都要求租户管理员。
- `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
  - `create()`：校验应用存在，生成 secret，保存 hash。
  - `authenticate()`：解析 `lfk_{id}_{secret}`，校验长度、prefix、id、secret hash 和状态。
  - `disable()`、`enable()`、`rotate()`：更新状态或 secret，并处理认证缓存驱逐。

### OpenAPI 安全链

- `server/app/src/main/java/com/linkforge/app/security/OpenApiSecurityConfig.java`
  - `securityMatcher("/api/v1/open/**")`。
  - 禁用 CSRF。
  - 添加 `ApiKeyAuthenticationFilter`。
- `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
  - 读取 `X-API-Key`。
  - 调用 `ApiKeyAuthenticator`，实际实现是 `ApiKeyService`。
  - 调用 `AccountStatusVerifier.requireActiveTenant()`。
  - 创建带 `ROLE_OPENAPI` 的认证结果，并附加 `ApiKeyAuthenticationDetails`。
- `server/foundation/runtime-security/src/main/java/com/linkforge/foundation/runtime/security/PrincipalActorMapper.java`
  - `requireApiKey()` 把 Spring Security 当前认证转换成 `ApiKeyActor`。

### OpenAPI 短链控制器

- `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
  - `create()` 和 `createForApplication()` 最终都调用 `ShortLinkApplicationService.createForApiKey()`。
  - `list()` 和 `listForApplication()` 复用 Shortlink 查询用例。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolver.java`
  - `resolveCreateForApiKey()` 和 `resolveBrowseForApiKey()` 是 API Key 范围控制核心。

## 与用户控制台链路的差异

控制台用户链路强调用户角色，例如 `TENANT_ADMIN` 是否可以创建应用级短链。OpenAPI 链路强调 API Key 绑定范围，即这个 key 能访问哪个应用。二者进入 Shortlink 后会复用相同的聚合规则、额度规则、标签规则、事件发布和缓存驱逐逻辑。

## 风险点

- API Key 明文只返回一次，前端或调用方必须在创建/轮换响应中保存。
- 绑定应用的 key 如果请求了其他应用路径，应由 `ShortLinkActorScopeResolver` 拒绝。
- 普通 `/api/v1/**` 不应接受 `X-API-Key`，避免把机器凭据误用于控制台管理接口。

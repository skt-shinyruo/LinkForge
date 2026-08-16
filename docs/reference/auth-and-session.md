# 认证与会话链路

## 业务目标

认证链路负责把控制台用户或 OpenAPI 客户端转换成后端可使用的身份对象。控制台请求最终得到 `AuthPrincipal`，OpenAPI 请求得到带 `ROLE_OPENAPI` 的 `AuthPrincipal` 和 `ApiKeyAuthenticationDetails`。后续控制器只处理已经认证过的主体，并用 `@PreAuthorize` 或应用层 actor 校验权限。

## 入口与边界

- 用户注册：`POST /api/v1/auth/register`
- 用户登录：`POST /api/v1/auth/login`
- Cookie CSRF token：`GET /api/v1/auth/csrf`
- 用户注销：`POST /api/v1/auth/logout`
- 当前用户：`GET /api/v1/me`
- 普通 API 安全链：`/api/**`
- OpenAPI 安全链：`/api/v1/open/**`
- 跳转链路：`/r/**` 不进入 API 安全链，由 Redirect 自己处理。

## 流程图

<svg xmlns="http://www.w3.org/2000/svg" width="980" height="430" viewBox="0 0 980 430" role="img" aria-label="认证与会话链路">
  <defs>
    <marker id="arrow-auth" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .decision{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4}
      .ok{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .err{fill:#fef2f2;stroke:#dc2626;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-auth)}
    </style>
  </defs>
  <rect class="box" x="30" y="50" width="150" height="64"/>
  <text class="text" x="105" y="78" text-anchor="middle">控制台用户</text>
  <text class="small" x="105" y="98" text-anchor="middle">注册 / 登录 / API</text>

  <rect class="box" x="245" y="50" width="170" height="64"/>
  <text class="text" x="330" y="77" text-anchor="middle">ApiSecurityConfig</text>
  <text class="small" x="330" y="98" text-anchor="middle">匹配 /api/**</text>

  <polygon class="decision" points="505,45 610,82 505,119 400,82"/>
  <text class="text" x="505" y="78" text-anchor="middle">公开认证</text>
  <text class="small" x="505" y="98" text-anchor="middle">register/login/csrf?</text>

  <rect class="box" x="675" y="30" width="190" height="82"/>
  <text class="text" x="770" y="60" text-anchor="middle">AuthController</text>
  <text class="small" x="770" y="80" text-anchor="middle">调用 AuthService</text>
  <text class="small" x="770" y="100" text-anchor="middle">签发 token / cookie</text>

  <rect class="box" x="245" y="190" width="170" height="74"/>
  <text class="text" x="330" y="218" text-anchor="middle">JwtAuthenticationFilter</text>
  <text class="small" x="330" y="238" text-anchor="middle">Bearer 或 Cookie JWT</text>
  <text class="small" x="330" y="256" text-anchor="middle">解析 AuthPrincipal</text>

  <rect class="box" x="480" y="190" width="190" height="74"/>
  <text class="text" x="575" y="218" text-anchor="middle">AccountStatusService</text>
  <text class="small" x="575" y="238" text-anchor="middle">租户 / 用户 ACTIVE</text>
  <text class="small" x="575" y="256" text-anchor="middle">校验 tokenVersion</text>

  <rect class="ok" x="735" y="190" width="170" height="74"/>
  <text class="text" x="820" y="218" text-anchor="middle">SecurityContext</text>
  <text class="small" x="820" y="238" text-anchor="middle">后续 Controller</text>
  <text class="small" x="820" y="256" text-anchor="middle">@PreAuthorize</text>

  <rect class="box" x="30" y="325" width="150" height="64"/>
  <text class="text" x="105" y="352" text-anchor="middle">OpenAPI 客户端</text>
  <text class="small" x="105" y="372" text-anchor="middle">X-API-Key</text>

  <rect class="box" x="245" y="315" width="170" height="84"/>
  <text class="text" x="330" y="345" text-anchor="middle">OpenApiSecurityConfig</text>
  <text class="small" x="330" y="365" text-anchor="middle">只匹配 /api/v1/open/**</text>
  <text class="small" x="330" y="385" text-anchor="middle">禁用 CSRF</text>

  <rect class="box" x="480" y="315" width="190" height="84"/>
  <text class="text" x="575" y="345" text-anchor="middle">ApiKeyAuthenticationFilter</text>
  <text class="small" x="575" y="365" text-anchor="middle">ApiKeyService.authenticate</text>
  <text class="small" x="575" y="385" text-anchor="middle">租户状态校验</text>

  <rect class="ok" x="735" y="325" width="170" height="64"/>
  <text class="text" x="820" y="352" text-anchor="middle">OPENAPI 主体</text>
  <text class="small" x="820" y="372" text-anchor="middle">details 含 applicationId</text>

  <path class="line" d="M180 82 H245"/>
  <path class="line" d="M415 82 H400"/>
  <path class="line" d="M610 82 H675"/>
  <path class="line" d="M330 114 V190"/>
  <path class="line" d="M415 227 H480"/>
  <path class="line" d="M670 227 H735"/>
  <path class="line" d="M180 357 H245"/>
  <path class="line" d="M415 357 H480"/>
  <path class="line" d="M670 357 H735"/>
</svg>

## 关键业务规则

- 自助注册受配置控制。`AuthController.register()` 会先检查 `securityProperties.registrationEnabled`，关闭时直接返回业务错误。
- 注册会创建租户、首个用户和 `TENANT_ADMIN` 角色。`AuthService.register()` 先查邮箱唯一性，再生成 tenantId、userId，保存 active 租户和 active 用户。
- 登录在 primary-only 只读事务内读取用户、租户、角色和 tokenVersion，必须同时满足租户 active、用户 active、密码匹配。不存在用户、禁用用户和错误密码都执行当前 BCrypt cost 的密码比较并返回同一凭据错误；角色为空时降级为 `USER`。
- JWT 中包含 `tenantId`、`email`、`roles`、`tokenVersion`。注销和重置密码都会递增 `tokenVersion`，让旧 JWT 失效。
- 注销使用数据库原子自增 tokenVersion；重置密码只更新 hash 并原子自增 tokenVersion；启用/禁用只更新 status。它们不会以旧整行快照覆盖彼此的安全字段。
- 所有安全状态缓存驱逐都注册为 after-commit：回滚不驱逐；提交会用 Redis Lua 原子推进持久 generation 并删除旧值。cache miss 在回源前读取 generation，只有 generation 未变化才可回填，因此即使旧数据库快照晚于提交完成，也不能重新写回旧 ACTIVE 或旧 tokenVersion。Redis 驱逐失败会记录 warning，但不回滚已提交事实，并由短 TTL 最终收敛。
- 禁用用户会把状态变为非 ACTIVE；它**不**递增 `tokenVersion`。重新启用后，禁用前未过期的 JWT 可能再次可用，这是当前兼容语义。
- 禁用租户管理员前会锁定 tenant 行，在同一事务内检查 ACTIVE 管理员数量并更新状态；两个管理员并发互相禁用时至多一个成功。
- Bearer token 无效时按认证失败处理；Cookie token 无效或超长时清 cookie 后继续，避免公开登录接口被错误 cookie 阻断。
- Cookie 会话下写请求使用双提交 CSRF：前端先拿 `XSRF-TOKEN` cookie，再带 `X-XSRF-TOKEN` header。
- `/api/v1/open/**` 只接受 API Key，不接受 JWT/Cookie；普通 `/api/v1/**` 不接受 `X-API-Key`。
- API Key HMAC pepper 必须独立于 JWT signing secret；旧单 pepper 变量只在滚动兼容期保留，不能借兼容路径重新启用生产 JWT fallback。摘要 keyring 与 rollout 细节见 [OpenAPI 与 API Key](openapi-api-key.md)。

## 源码分析

### 控制器入口

- `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/AuthController.java`
  - `register()`：校验自助注册开关，调用 `AuthService.register()`，按配置写 JWT cookie。
  - `login()`：调用 `AuthService.login()`，返回 token 或只写 HttpOnly cookie。
  - `csrf()`：返回 Spring Security 生成的 CSRF header 名和 token。
  - `logout()`：从当前 principal 找 userId，调用 `AuthService.logout()`，并清理 cookie。
- `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/MeController.java`
  - `GET /api/v1/me`：从 `AuthContext.requirePrincipal()` 读取已认证主体，给前端初始化会话状态。
- `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/UserAdminController.java`
  - 租户管理员用户管理入口。它不是登录链路的一部分，但会影响认证状态：禁用会驱逐缓存并由 ACTIVE 状态拒绝请求；重置密码会额外递增 `tokenVersion`。

### 应用层逻辑

- `server/accounts/application/src/main/java/com/linkforge/accounts/application/AuthService.java`
  - `register()`：检查邮箱、创建租户、创建用户、写 `TENANT_ADMIN` 角色、签发 JWT。
  - `login()`：在 primary-only 只读事务中按邮箱查用户和租户，所有失败路径执行密码比较，读取角色后签发 JWT。
  - `logout()`：原子递增用户 `tokenVersion`，提交后驱逐账号状态缓存。
- `server/accounts/application/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
  - 实现 `AccountStatusVerifier`，给安全过滤器复用。
  - `requireActiveTenant()`：只校验租户。
  - `requireActiveUserAndTenant()`：校验租户、用户归属、用户状态和可选 tokenVersion。
  - 使用短 TTL Redis 状态缓存和 generation-fenced 回填；Redis 不可用时降级查 DB 且跳过缓存填充。
- `server/accounts/application/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
  - 创建用户时角色只允许 `TENANT_ADMIN` 和 `USER`。
  - 禁用用户时禁止禁用自己，并在 tenant 行锁串行化范围内要求至少保留一个启用中的租户管理员。
  - 启用/禁用使用窄 status 命令；重置密码使用 hash + tokenVersion 原子命令；缓存只在事务提交后驱逐。

### 用户管理 HTTP 契约

用户管理都要求当前主体是同租户 `TENANT_ADMIN`：

| 方法与路径 | 作用 | 关键边界 |
| --- | --- | --- |
| `GET /api/v1/users` | 列出租户用户 | 跨租户资源不会泄漏 |
| `POST /api/v1/users` | 创建用户 | email 全局唯一；空角色默认 `USER`，角色仅允许 `USER`/`TENANT_ADMIN` |
| `PUT /api/v1/users/{id}/disable` | 禁用用户 | 不能禁用自己，必须保留至少一个 ACTIVE 租户管理员 |
| `PUT /api/v1/users/{id}/enable` | 启用用户 | 不增加 tokenVersion，历史未过期 JWT 可能恢复可用 |
| `PUT /api/v1/users/{id}/password` | 重置密码 | 更新 hash、增加 tokenVersion、驱逐状态缓存 |

跨租户或不存在用户统一按 not found 处理，避免把用户枚举面暴露给当前租户。

### 安全过滤链

- `server/app/src/main/java/com/linkforge/app/security/ApiSecurityConfig.java`
  - `securityMatcher("/api/**")`，但公开放行 `/api/v1/auth/register`、`/login`、`/logout` 和 `GET /csrf`。
  - 添加 `JwtAuthenticationFilter`。
- `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
  - 解析 Bearer 或 Cookie JWT。
  - 调用 `JwtPrincipalVerifier` 解析 token，再调用 `AccountStatusVerifier.requireActiveUserAndTenant()`。
  - 认证成功后把 `AuthPrincipal` 放进 Spring Security 上下文。
- `server/app/src/main/java/com/linkforge/app/security/OpenApiSecurityConfig.java`
  - `securityMatcher("/api/v1/open/**")`，只挂 `ApiKeyAuthenticationFilter`，禁用 CSRF。
- `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
  - 读取 `X-API-Key`，调用 `ApiKeyAuthenticator`，然后只校验租户状态。
  - 认证成功时创建 `ROLE_OPENAPI` principal，并把 API Key 绑定的应用信息放到 authentication details。

### JWT 实现

- `server/accounts/infrastructure/src/main/java/com/linkforge/accounts/infrastructure/security/JwtService.java`
  - `issueToken()` 使用 HMAC secret 签发 JWT，secret 要求至少 32 bytes。
  - `parseToken()` 校验 issuer 和签名，读取 subject、tenantId、email、roles、tokenVersion。
- `server/accounts/infrastructure/src/main/java/com/linkforge/accounts/infrastructure/security/AccountsJwtTokenIssuer.java`
  - 适配 Accounts 应用层的 token 签发端口。

## 异常与一致性

- 认证失败由 `SecurityConfig` 提供的 `AuthenticationEntryPoint` 和 `AccessDeniedHandler` bean 返回统一 API 错误形状。
- 业务错误进入 `GlobalExceptionHandler`，响应体包含 `ApiResponse.requestId`。
- `RequestIdFilter` 会把请求 ID 放到 MDC、响应头和 API 响应中，便于串联认证失败和后续业务日志。
- 注销和重置密码通过原子 `tokenVersion` 命令加 after-commit 状态缓存驱逐实现会话失效；禁用依赖窄 status 命令、ACTIVE 状态拒绝和相同的提交后驱逐。启用不会撤销或新建 tokenVersion，这是需要在管理员操作中知晓的当前限制。

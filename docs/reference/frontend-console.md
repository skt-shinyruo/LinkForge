# 前端控制台链路

## 业务目标

前端是 Vue 3 + Vite + Pinia 的单页控制台。它不承载后端业务规则，主要负责会话初始化、权限路由、HTTP transport、页面状态组合和用户操作编排。后端返回业务错误时，前端保留 message 展示给用户。

## 页面结构

- `/login`：登录。
- `/links`：短链列表、创建、编辑、归档、恢复、删除、导入导出。
- `/tags`：标签。
- `/stats`：统计。
- `/overview`：租户概览，需要 `TENANT_ADMIN`。
- `/applications`：应用管理，需要 `TENANT_ADMIN`。
- `/domains`：域名管理，需要 `TENANT_ADMIN`。
- `/api-keys`：API Key 管理，需要 `TENANT_ADMIN`。
- `/approvals`：审批，需要 `TENANT_ADMIN` 或 `PLATFORM_ADMIN`。
- `/audit`：审计，需要 `TENANT_ADMIN` 或 `PLATFORM_ADMIN`。

## 流程图

<svg xmlns="http://www.w3.org/2000/svg" width="1060" height="500" viewBox="0 0 1060 500" role="img" aria-label="前端控制台链路">
  <defs>
    <marker id="arrow-fe" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .state{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .view{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .warn{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-fe)}
    </style>
  </defs>
  <rect class="box" x="30" y="60" width="160" height="76"/>
  <text class="text" x="110" y="90" text-anchor="middle">用户访问路由</text>
  <text class="small" x="110" y="112" text-anchor="middle">Vue Router</text>

  <rect class="state" x="255" y="45" width="180" height="106"/>
  <text class="text" x="345" y="75" text-anchor="middle">router.beforeEach</text>
  <text class="small" x="345" y="98" text-anchor="middle">auth.init()</text>
  <text class="small" x="345" y="118" text-anchor="middle">登录态</text>
  <text class="small" x="345" y="138" text-anchor="middle">角色权限</text>

  <rect class="state" x="505" y="45" width="180" height="106"/>
  <text class="text" x="595" y="75" text-anchor="middle">auth store</text>
  <text class="small" x="595" y="98" text-anchor="middle">bearer / cookie</text>
  <text class="small" x="595" y="118" text-anchor="middle">/api/v1/me</text>
  <text class="small" x="595" y="138" text-anchor="middle">logout 清状态</text>

  <rect class="warn" x="755" y="45" width="180" height="106"/>
  <text class="text" x="845" y="75" text-anchor="middle">http.ts</text>
  <text class="small" x="845" y="98" text-anchor="middle">Bearer / credentials</text>
  <text class="small" x="845" y="118" text-anchor="middle">CSRF header</text>
  <text class="small" x="845" y="138" text-anchor="middle">统一 ApiResponse</text>

  <rect class="view" x="255" y="245" width="180" height="110"/>
  <text class="text" x="345" y="275" text-anchor="middle">services/*</text>
  <text class="small" x="345" y="298" text-anchor="middle">links / stats</text>
  <text class="small" x="345" y="318" text-anchor="middle">applications / domains</text>
  <text class="small" x="345" y="338" text-anchor="middle">apiKeys / approvals / audit</text>

  <rect class="view" x="505" y="245" width="180" height="110"/>
  <text class="text" x="595" y="275" text-anchor="middle">composables</text>
  <text class="small" x="595" y="298" text-anchor="middle">页面状态</text>
  <text class="small" x="595" y="318" text-anchor="middle">筛选 / 表单</text>
  <text class="small" x="595" y="338" text-anchor="middle">刷新 / 错误</text>

  <rect class="view" x="755" y="245" width="180" height="110"/>
  <text class="text" x="845" y="275" text-anchor="middle">views/components</text>
  <text class="small" x="845" y="298" text-anchor="middle">渲染页面</text>
  <text class="small" x="845" y="318" text-anchor="middle">触发操作</text>
  <text class="small" x="845" y="338" text-anchor="middle">展示结果</text>

  <rect class="box" x="505" y="410" width="430" height="58"/>
  <text class="text" x="720" y="434" text-anchor="middle">后端 API</text>
  <text class="small" x="720" y="454" text-anchor="middle">业务规则仍以后端为准，前端只做交互约束和 transport</text>

  <path class="line" d="M190 98 H255"/>
  <path class="line" d="M435 98 H505"/>
  <path class="line" d="M685 98 H755"/>
  <path class="line" d="M845 151 V245"/>
  <path class="line" d="M755 300 H685"/>
  <path class="line" d="M505 300 H435"/>
  <path class="line" d="M595 355 V410"/>
</svg>

## 路由与会话

`web/src/router/index.ts` 定义路由和权限元信息：

- 每次跳转先调用 `auth.init()`。
- 未登录访问需要认证的页面，跳转到 `/login?redirect=...`。
- 已登录访问 `/login`，租户管理员跳 `/overview`，普通用户跳 `/links`。
- 权限不足访问管理员页面，回退 `/links`。

`web/src/stores/auth.ts` 支持两种认证模式：

- `VITE_AUTH_MODE=bearer`：token 存 local/session storage，默认 session。
- `VITE_AUTH_MODE=cookie`：不保存 token，依赖服务端 HttpOnly cookie。

`auth.init()` 会调用 `/api/v1/me` 补齐用户、租户和角色，并用 `initInFlight` 避免并发初始化。

初始化状态有三个重要约束：

- Bearer storage 中存在 token 只代表“可以尝试认证”，最终以 `/me` 为准。
- Cookie 模式不读取 HttpOnly token，以 `/me` 返回的 email/roles 建立前端会话快照。
- 初始化失败会清 token、roles 和 tenantId，并稳定设置 `initialized=true`；否则路由守卫会反复 bootstrap。

`requiresTenantAdmin` 只允许 `TENANT_ADMIN`；`requiresAdmin` 允许 `TENANT_ADMIN` 或 `PLATFORM_ADMIN`。平台管理员会被挡在租户控制面页面之外，但能访问共享审批/审计页面。路由元信息只改善导航体验，后端权限仍是最终边界。

## HTTP transport

`web/src/services/http.ts` 是所有 service 的基础：

- `authFetch()` 自动加 Bearer token 或 Cookie credentials。
- Cookie 模式下，POST/PUT/DELETE/PATCH 会先获取 `XSRF-TOKEN` cookie 并写 `X-XSRF-TOKEN` header。
- 401 会清 token，并调用 `main.ts` 注册的 unauthorized handler 跳回登录页。
- `apiFetch()` 统一解析后端 `ApiResponse`，非 2xx 抛异常并保留后端 message。

Cookie 写请求的 CSRF 初始化只由一个共享的在途 Promise 收敛，并发首写只请求一次 `/api/v1/auth/csrf`。Promise 必须确认响应成功且浏览器确实持有 `XSRF-TOKEN` 后才算初始化成功，并在完成后释放；非 2xx、缺 cookie 或之后 cookie 被清除时，下一次写请求会重新初始化。初始化失败的当前请求仍会发给服务端，由服务端 CSRF 规则拒绝，前端不会把缺 token 当作成功降级。服务端对 unsafe 请求返回 403 时，transport 只把现有 token 标记为过期，让下一次写请求重新初始化；它不会自动重放这次可能已产生副作用的请求。

401 回调在同一 microtask 内去重，避免一个页面的并发请求触发多次登录跳转。`auth.logout()` 通知服务端是 best-effort：即使网络失败也会清本地状态，但不能据此宣称旧 bearer token 已在服务端撤销。

### API contract

`web/src/services/apiContract.ts` 统一端点和第二层业务响应检查：

- `apiFetch()` 负责 HTTP 状态和 JSON 形状；HTTP 2xx 内仍可能有非零业务 code。
- `ensureApiSuccess()` 检查 `code===0`，允许成功响应没有 data。
- `requireApiData()` 用于后端承诺返回资源的调用，成功但缺 data 也视为协议错误。
- `buildQueryString()` 保留 `false` 和 `0`，省略 null/undefined，默认省略空字符串。
- `API_ENDPOINTS` 是控制台路径 SSOT。应用域名授权固定为 `/applications/{id}/domain-authorizations/{domainId}`；链接和统计的应用作用域固定走 `/applications/{id}/...`。

Blob 下载和 `FormData` 上传直接使用 `authFetch()`，因为 `apiFetch()` 会消费 body 并要求 JSON。

## services

services 只负责 HTTP transport，不持有页面状态，也不在各文件复制路径：

- `web/src/services/links.ts`：短链列表、创建、更新、归档、恢复、删除、CSV 导入导出。应用级短链自动走 `/applications/{id}/links` 路径。
- `web/src/services/applications.ts`：应用列表和创建。
- `web/src/services/domains.ts`：域名列表、应用可用域名、共享域名创建、应用专属域名创建、共享域名授权。
- `web/src/services/apiKeys.ts`：API Key 列表、创建、启用、禁用、轮换。
- `web/src/services/stats.ts`：概览、Top 链接、单链接日统计。应用级查询使用应用路径。
- `web/src/services/approvals.ts`：审批摘要游标分页和审批动作；wire body 保持数组，service 从响应头组装分页元数据。
- `web/src/services/audit.ts`：不含前后快照的审计摘要游标分页。
- `web/src/services/tags.ts`：标签列表和创建。

## 页面 composables

- `web/src/composables/useLinksPage.ts`
  - 管理短链列表 loading/error/items/page/size/total。
  - 管理筛选条件、应用选择、域名选择、创建和编辑表单。
  - 租户管理员选择应用后加载该应用可用域名。
  - 导入时如果选择应用，必须选择域名。
  - 创建时从表单构造 payload，当前选择应用时注入 applicationId/domainId。
  - 启用/禁用、编辑前检查未归档。
  - 删除前要求短链已归档。
  - CSV 导入使用 `FormData`；导出拿 Blob 并下载 `links.csv`。
- `web/src/composables/links/linkFormCodec.ts`
  - 标签和 query allowlist 支持逗号或换行分隔、去重。
  - `datetime-local` 按浏览器时区解释，再转 UTC ISO instant。
  - 编辑时空过期时间、状态码、query mode 转成对应 clear flag，且不会同时发送值与 clear。
  - 空 allowlist/tags 发送空数组，表示显式清空；创建时空可选字段则省略。
- `web/src/composables/useStatsPage.ts`
  - 默认 7 天，也支持 30 天。
  - 租户管理员可选应用范围。
  - 链接选项使用 `cursor`、`includeTotal=false` 和有界页按需搜索；加载更多只追加当前搜索结果。
  - 概览、Top 链接和单链接日统计使用独立刷新路径；切换短链只刷新该短链趋势，切换排序只刷新 Top 链接。
  - 日期范围按 UTC 自然日构造，不把分日 HLL UV 求和成精确区间 UV。
- `web/src/composables/useApplicationsPage.ts`
  - 应用列表和创建。
- `web/src/composables/useApplicationDetailPage.ts`
  - 使用同一个 route applicationId 并行加载应用列表、API Keys、应用概览、Top 链接、应用域名。
  - 任一请求失败进入统一错误态，避免主动拼接部分新、部分旧的详情快照。
- `web/src/composables/useDomainsPage.ts`
  - 域名创建和共享域名授权。
- `web/src/composables/useApiKeysPage.ts`
  - API Key 创建、启用、禁用、轮换；明文 key 只在响应时展示。
- `web/src/composables/useApprovalsPage.ts`
  - 审批列表、审批原因、审批成功后刷新；下一页按不透明 cursor 追加，不回退到全量加载。
- `web/src/composables/useAuditPage.ts`
  - 审计日志首屏和按 cursor 继续加载。
- `web/src/router/index.ts` 与 `web/src/components/AppPageShell.vue`
  - route meta 是页面标题、导航顺序和角色可见性的事实源；页面 shell 负责渲染导航和登出。

## 典型页面流转

### 登录后管理短链

1. 用户提交登录表单。
2. `auth.login()` 调 `/api/v1/auth/login`。
3. Bearer 模式保存 token；Cookie 模式依赖服务端 cookie。
4. router guard 调 `/api/v1/me` 初始化角色。
5. 进入 `/links`。
6. `useLinksPage` 调 `listLinks()`。
7. 创建短链时，前端按是否选择应用决定调用普通路径或应用路径。
8. 后端返回业务错误时，页面展示 error message。

### 统计页

1. `useStatsPage` 初始化日期范围。
2. 管理员可选择应用范围。
3. 按需搜索一页链接选项，使用 cursor 继续加载；不为选择器计算全量 total。
4. 日期范围变化并行请求概览、Top 链接和当前单链接日统计。
5. Top 排序和当前短链选择分别只刷新其对应报表。
6. 复制 shortUrl 失败时静默，不影响主要页面。

页面的可替代读取使用 `useLatestRequest` 的 AbortController 与 generation；用户快速连续搜索、切换应用、日期或链接时，旧响应不能提交到当前页面快照。独立报表刷新器允许短链选择不触发无关的概览和 Top 请求。

## 源码边界

前端不会复制后端领域规则。比如“应用级短链修改目标地址必须审批”“删除前必须归档”“API Key 是否可访问某应用”等最终以后端为准。前端只做交互上的提前约束，减少明显无效请求。

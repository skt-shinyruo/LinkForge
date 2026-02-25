# API 手册（v1，MVP 实现对齐）

## 1. 概览

- 管理后台与自助创建：`/api/v1/**`（JWT）
- OpenAPI：`/api/v1/open/**`（API Key）
- 跳转：`/r/{code}`（公开，不需要登录）

统一响应建议见 `.helloagents/project.md`。

---

## 2. 认证方式

### 2.1 JWT（管理后台/自助创建）
- Header：`Authorization: Bearer <token>`

### 2.2 JWT Cookie（可选，管理后台浏览器会话）
- 前端：fetch 需携带 `credentials: "include"`
- 后端：登录/注册可下发 HttpOnly Cookie（启用开关后生效）
- 说明：
  - Cookie 模式下前端通常不需要持久化 token（避免 localStorage 长期驻留风险）
  - 启用 Cookie 后，CORS 需要配置为白名单且允许 credentials（详见 `.helloagents/project.md`）
  - Cookie 模式下写接口需携带 CSRF token（双提交 cookie）：`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header（可先请求 `GET /api/v1/auth/csrf` 初始化）

### 2.3 API Key（OpenAPI）
- Header：`X-API-Key: <key>`
- 说明：
  - Key 仅创建时返回一次明文；服务端仅保存哈希
  - **Key 格式（MVP 实现）：** `lfk_{id}_{secret}`（`id` 为数字）

---

## 3. API 列表（草案）

### 3.1 认证与用户（iam）

#### [POST] /api/v1/auth/register
- 描述：自助注册并创建租户（第一个用户成为租户管理员）
- 约束：**email 全局唯一（跨租户）**。该约束用于简化登录体验（登录不需要选择租户）。
- Request: tenantName, email, password
- Response: token + user（含 tenantId/roles）

#### [POST] /api/v1/auth/login
- 描述：登录获取 JWT
- 说明：当前实现按 **email 全局唯一** 设计，登录请求不携带 tenant 信息。
- Request: email, password
- Response: token + user（含 tenantId/roles）

#### [POST] /api/v1/auth/logout
- 描述：注销登录（Cookie 模式下清理 Cookie；Bearer 模式下作为幂等空操作）

#### [GET] /api/v1/auth/csrf
- 描述：获取/刷新 CSRF token（Cookie 模式专用；用于初始化 `XSRF-TOKEN` cookie）
- Response: headerName + token（前端也可直接从 `XSRF-TOKEN` cookie 读取）

#### [GET] /api/v1/me
- 描述：获取当前登录用户信息（含 tenantId、角色）

#### [GET] /api/v1/users
- 描述：租户内用户列表（管理员）

#### [POST] /api/v1/users
- 描述：邀请/创建租户用户（管理员）
- 约束：**email 全局唯一（跨租户）**，用于保持登录定位唯一性。
- Request: email, password, roles（可选；默认 USER）

#### [PUT] /api/v1/users/{id}/disable
- 描述：禁用租户用户（管理员）

#### [PUT] /api/v1/users/{id}/enable
- 描述：启用租户用户（管理员）

#### [PUT] /api/v1/users/{id}/password
- 描述：重置租户用户密码（管理员）
- Request: password

#### [GET] /api/v1/api-keys
- 描述：OpenAPI Key 列表（管理员）

#### [POST] /api/v1/api-keys
- 描述：创建 OpenAPI Key（管理员）
- Request: name
- Response: id + apiKey（仅返回一次明文）

#### [PUT] /api/v1/api-keys/{id}/disable
- 描述：禁用 API Key（管理员）

#### [PUT] /api/v1/api-keys/{id}/enable
- 描述：启用 API Key（管理员）

#### [POST] /api/v1/api-keys/{id}/rotate
- 描述：轮换 API Key（管理员）
- Response: id + apiKey（新的明文 key，仅返回一次）

---

### 3.2 短链管理（shortlink）

#### [POST] /api/v1/links
- 描述：创建短链（支持有效期、备注、标签、自定义短码可选）
- Request:
  - 基础字段：originalUrl, note?, expiresAt?, enabled?, customCode?, tags?
  - 跳转策略（可选）：
    - redirectStatusCode?: 301/302（为空则使用全局默认）
    - previewEnabled?: boolean（浏览器访问 `/r/{code}` 时启用“确认后再跳”）
    - unavailableLandingUrl?: string（禁用/过期时的落地页 URL；为空则使用全局/内置 410 页面）
    - queryForwardMode?: OFF/ALLOWLIST/ALL（为空则继承全局默认）
    - queryForwardAllowlist?: string[]（如 `["utm_*"]`，逗号分隔存储；与全局 allowlist 合并）
- Response: LinkDto（含 shortUrl）

#### [GET] /api/v1/links
- 描述：分页查询短链列表（支持状态/标签/关键字过滤）
- Query: archived?（默认 false）, enabled?, keyword?, tag?, page, size

#### [GET] /api/v1/links/{id}
- 描述：短链详情

#### [PUT] /api/v1/links/{id}
- 描述：更新短链（原始URL、有效期、备注、标签、启用/禁用，以及跳转策略）
- Request（新增可选字段）：
  - redirectStatusCode?: 301/302
  - clearRedirectStatusCode?: boolean（true 表示清空并回退全局默认；与 redirectStatusCode 互斥）
  - previewEnabled?: boolean
  - unavailableLandingUrl?: string（显式传空字符串 `""` 表示清空）
  - queryForwardMode?: OFF/ALLOWLIST/ALL
  - clearQueryForwardMode?: boolean（true 表示清空并继承全局默认；与 queryForwardMode 互斥）
  - queryForwardAllowlist?: string[]（传空数组 `[]` 表示清空 per-link allowlist）

#### [POST] /api/v1/links/{id}/archive
- 描述：归档短链（管理员；可恢复下线）

#### [POST] /api/v1/links/{id}/restore
- 描述：恢复短链（管理员）

#### [DELETE] /api/v1/links/{id}
- 描述：删除短链（管理员；要求先归档）

#### [POST] /api/v1/links/import
- 描述：批量导入（CSV）
- CSV Header（MVP）：originalUrl, code(可选), expiresAt(可选, ISO-8601), note(可选), tags(可选, 逗号分隔)

#### [GET] /api/v1/links/export
- 描述：批量导出（CSV）

---

### 3.3 标签（shortlink）

#### [GET] /api/v1/tags
- 描述：标签列表

#### [POST] /api/v1/tags
- 描述：创建标签

---

### 3.4 统计（analytics）

#### [GET] /api/v1/stats/links/{id}/daily
- 描述：按天统计（PV/UV 等），支持时间范围查询
- Query: from, to（yyyy-MM-dd）

#### [GET] /api/v1/stats/overview
- 描述：租户统计概览（近 7/30 天）
- Query: from, to（yyyy-MM-dd）

#### [GET] /api/v1/stats/top-links
- 描述：Top 链接报表（按时间范围聚合，默认按 PV 降序）
- Query: from, to（yyyy-MM-dd）, limit?（默认 10，最大 100）, sortBy?（pv/uv，默认 pv）

#### [GET] /api/v1/stats/links/{id}/dimensions
- 描述：单短链维度分布（TopN）
- Query:
  - from, to（yyyy-MM-dd）
  - type（`referer_domain|language|ua_family|os_family|device_type|utm_source|utm_medium|utm_campaign`）
  - limit?（默认 10，最大 100）

#### [GET] /api/v1/stats/links/{id}/events
- 描述：单短链最近访问明细（短期留存，主要用于排障/异常访问识别）
- Query:
  - from?（ISO-8601 LocalDateTime，可选；缺省为 `to-1day`）
  - to?（ISO-8601 LocalDateTime，可选；缺省为当前 UTC 时间）
  - limit?（默认 50，最大 200）

---

### 3.5 OpenAPI（openapi）

#### [POST] /api/v1/open/links
- 描述：使用 API Key 创建短链
- Header: X-API-Key
- Request: 同 /api/v1/links
- Response: LinkDto

#### [GET] /api/v1/open/links
- 描述：使用 API Key 查询短链列表
- Header: X-API-Key
- Query: enabled?, keyword?, page, size

---

### 3.6 跳转（redirect）

#### [GET] /r/{code}
- 描述：短码解析与跳转（默认 302；支持按链接配置 301/302）
- 关键行为（Edge 实现）：
  - 不可用体验（浏览器友好）：
    - Accept 包含 `text/html`：短码不存在返回 404 HTML；禁用/过期返回 410 HTML
    - 支持配置全局落地页：`app.redirect.not-found-landing-url` / `app.redirect.gone-landing-url`
    - 支持按链接配置落地页（仅禁用/过期）：`unavailableLandingUrl`
    - 非 HTML 请求保持 JSON 错误结构（兼容脚本/OpenAPI 调用）
  - 预览页（确认后再跳）：
    - 链接开启 `previewEnabled=true` 且浏览器请求时，首次访问返回 200 HTML 预览页
    - 同一路由携带内部参数 `__lf_confirm=1` 后才执行跳转（内部参数不透传）
  - Query 透传/拼接：
    - queryForwardMode: OFF / ALLOWLIST / ALL（link 优先，其次全局）
    - allowlist 支持前缀通配（如 `utm_*`），且会过滤内部保留参数（默认包含 `__lf_confirm`）
    - 冲突策略：目标 URL 已存在同名参数时不覆盖（目标优先）

# iam

## Purpose
提供多租户、用户体系、登录鉴权与角色权限控制（RBAC）。

## Module Overview
- **Responsibility:** 租户与用户、注册/登录、JWT 签发与校验、角色权限（RBAC）、OpenAPI Key 管理；并为全链路提供 tenantId 注入与越权护栏
- **Status:** 🚧In Development
- **Last Updated:** 2026-02-25
- **Code Root Package:** `com.linkforge.api.iam.*`（IAM 领域在 API Service 内的实现）

## Specifications

### Requirement: iam-tenant（租户）
**Module:** iam
支持租户创建与禁用，所有业务数据以 `tenant_id` 进行隔离。

#### Scenario: register-tenant（注册创建租户）
前置条件：用户未登录
- 用户提交邮箱/密码/租户名
- 系统创建租户与首个用户
- 首个用户默认角色为 `tenant_admin`

#### Scenario: disable-tenant（禁用租户）
前置条件：系统管理员或租户管理员具备权限
- 禁用租户后，该租户所有 API 请求被拒绝（返回明确错误码）

---

### Requirement: iam-auth（认证）
**Module:** iam
提供注册、登录、JWT 鉴权与当前用户查询能力。

#### Scenario: login（登录）
前置条件：用户存在且启用
- 输入邮箱/密码登录成功，返回 JWT
- 登录失败返回统一错误码（不泄露“账号是否存在”）

#### Scenario: login-cookie（可选 Cookie 会话）
前置条件：开启后端 Cookie 模式开关
- 注册/登录可下发 HttpOnly Cookie（前端无需持久化 token）
- 前端请求需携带 `credentials: include`

#### Scenario: me（当前用户）
前置条件：携带有效 JWT
- 返回用户基础信息、tenantId、角色列表

---

### Requirement: iam-rbac（角色权限）
**Module:** iam
提供管理员/普通用户权限边界（最小可行 RBAC）。

#### Scenario: tenant-admin-manage-users（管理员管理用户）
前置条件：当前用户为 `tenant_admin`
- 可查看租户内用户列表
- 可邀请/创建租户用户
- 可禁用/启用用户
- 可重置用户密码（用于账号交接/安全处置）

#### Scenario: user-no-admin-ops（普通用户无管理员权限）
前置条件：当前用户为 `user`
- 访问管理员接口返回 403

---

### Requirement: iam-openapi-key（OpenAPI Key）
**Module:** iam
为租户生成/禁用 API Key，供 OpenAPI 使用。

#### Scenario: create-api-key（创建 API Key）
前置条件：`tenant_admin`
- 创建 API Key 时仅返回一次明文
- 服务端存储哈希（不可逆）

#### Scenario: disable-enable-api-key（禁用/启用 API Key）
前置条件：`tenant_admin`
- 禁用后 OpenAPI 请求返回明确错误码
- 启用后恢复可用

#### Scenario: rotate-api-key（轮换 API Key）
前置条件：`tenant_admin`
- 支持轮换 Key（旧明文 Key 立即失效；服务端更新哈希）
- 轮换后会返回一次新的明文 Key（仅返回一次）

## API Interfaces

### [POST] /api/v1/auth/register
**Description:** 注册并创建租户
**Input:** email、password、tenantName
**Output:** JWT + user info

### [POST] /api/v1/auth/login
**Description:** 登录
**Input:** email、password
**Output:** JWT + user info

### [GET] /api/v1/me
**Description:** 当前用户信息
**Input:** JWT
**Output:** user + tenant + roles

### [GET] /api/v1/users
**Description:** 租户用户列表（管理员）

### [POST] /api/v1/users
**Description:** 创建租户用户（管理员）

### [PUT] /api/v1/users/{id}/disable
**Description:** 禁用用户（管理员）

### [PUT] /api/v1/users/{id}/enable
**Description:** 启用用户（管理员）

### [PUT] /api/v1/users/{id}/password
**Description:** 重置用户密码（管理员）

### [POST] /api/v1/api-keys
**Description:** 创建 OpenAPI Key（管理员）

### [GET] /api/v1/api-keys
**Description:** API Key 列表（管理员）

### [PUT] /api/v1/api-keys/{id}/disable
**Description:** 禁用 API Key（管理员）

### [PUT] /api/v1/api-keys/{id}/enable
**Description:** 启用 API Key（管理员）

### [POST] /api/v1/api-keys/{id}/rotate
**Description:** 轮换 API Key（管理员；返回新的明文 Key，仅一次）

## Data Models

### tenants / users / user_roles / api_keys
见 `.helloagents/wiki/data.md`。

## Dependencies
- platform（通用：错误码、日志、上下文）

## Change History
- [202602182227_shortlink_system_mvp](../../history/2026-02/202602182227_shortlink_system_mvp/) - MVP：注册/登录/JWT/RBAC/OpenAPI Key
- [202602191426_edge_api_split_refactor](../../history/2026-02/202602191426_edge_api_split_refactor/) - 鉴权与错误响应统一、tenant guard 护栏、可选 Cookie 会话
- [202602201407_lifecycle_governance_closure](../../history/2026-02/202602201407_lifecycle_governance_closure/) - 治理闭环增强：用户启用/禁用/重置密码，API Key 启用/禁用/轮换

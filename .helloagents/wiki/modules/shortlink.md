# shortlink

## Purpose
提供短链的创建、管理与批量操作能力（有效期、备注、标签、自定义短码可选）。

## Module Overview
- **Responsibility:** 短链 CRUD、启用/禁用、有效期、备注/标签、跳转策略配置（状态码/预览页/query 透传/不可用落地页）、批量导入导出、OpenAPI 短链能力
- **Status:** 🚧In Development
- **Last Updated:** 2026-02-25
- **Code Root Package:** `com.linkforge.api.shortlink.*`（短链领域在 API Service 内的实现）

## Specifications

### Requirement: shortlink-create（创建短链）
**Module:** shortlink
用户可创建短链并获得可访问的短 URL。

#### Scenario: create-auto-code（自动生成短码）
前置条件：用户已登录
- 输入 originalUrl、expiresAt（可选）、note（可选）、tags（可选）
- 返回短链（包含短码 code 与完整短 URL）

#### Scenario: create-custom-code（自定义短码）
前置条件：用户已登录且具备权限（建议仅管理员或受限）
- 用户指定 code
- code 不可与已有短码冲突

---

### Requirement: shortlink-manage（管理短链）
**Module:** shortlink
支持列表、查询、编辑、禁用。

#### Scenario: list-links（短链列表）
前置条件：用户已登录
- 支持分页
- 支持按 enabled/过期/标签/关键字过滤
- 默认仅返回“活动短链”（未归档）；归档列表需显式传 `archived=true`

#### Scenario: update-link（编辑短链）
前置条件：用户已登录且短链属于本租户
- 支持更新 originalUrl、expiresAt、note、tags
- 支持更新跳转策略字段（可选）：
  - redirectStatusCode（301/302，可清空回退全局默认）
  - previewEnabled（浏览器预览确认页）
  - unavailableLandingUrl（禁用/过期时落地页）
  - queryForwardMode（OFF/ALLOWLIST/ALL，可清空继承全局默认）
  - queryForwardAllowlist（如 `utm_*`，传空数组清空 per-link allowlist）

#### Scenario: disable-link（禁用短链）
前置条件：用户已登录且具备权限
- 禁用后 Redirect 访问返回“已失效/不可用”

#### Scenario: archive-link（归档短链）
前置条件：管理员（`tenant_admin`）
- 归档用于“可恢复下线”（不会丢失数据）
- 归档后：
  - `/api/v1/links` 默认列表不可见
  - Redirect Edge 侧视为不可用（表现为短链不存在）
  - 禁止编辑（需先恢复）

#### Scenario: restore-link（恢复短链）
前置条件：管理员（`tenant_admin`）
- 恢复后回到活动列表，可继续编辑/启用/禁用

#### Scenario: delete-link（删除短链）
前置条件：管理员（`tenant_admin`）
- 删除前必须先归档（降低误删风险）
- 删除时同步清理关联数据（link_tags、统计聚合与访问明细等），避免产生孤儿数据

---

### Requirement: shortlink-bulk（批量导入导出）
**Module:** shortlink
支持 CSV 批量导入导出，满足运营或迁移需求。

#### Scenario: import-csv（导入）
前置条件：管理员
- 上传 CSV（至少包含 originalUrl，可选 code/expiresAt/note/tags）
- 返回导入结果（成功/失败原因）
 - **实现约束：** Service 层以 `InputStream/Reader` 作为输入，避免将 `MultipartFile` 传播到业务层（Controller 负责协议适配）

#### Scenario: export-csv（导出）
前置条件：管理员
- 按筛选条件导出 CSV
 - **实现约束：** Service 层以 `Writer/OutputStream` 作为输出，Controller 负责 HTTP 响应包装

## API Interfaces

### [POST] /api/v1/links
**Description:** 创建短链

### [GET] /api/v1/links
**Description:** 分页列表（默认活动；支持 `archived=true|false`）

### [PUT] /api/v1/links/{id}
**Description:** 更新短链

### [POST] /api/v1/links/{id}/archive
**Description:** 归档短链（管理员）

### [POST] /api/v1/links/{id}/restore
**Description:** 恢复短链（管理员）

### [DELETE] /api/v1/links/{id}
**Description:** 删除短链（管理员；要求先归档）

### [POST] /api/v1/links/import
**Description:** CSV 导入

### [GET] /api/v1/links/export
**Description:** CSV 导出

## Data Models

### short_links / tags / link_tags
见 `.helloagents/wiki/data.md`。

## Dependencies
- iam（多租户与权限）
- redirect-edge（缓存预热/更新）
- analytics（统计查询）

## Change History
- [202602182227_shortlink_system_mvp](../../history/2026-02/202602182227_shortlink_system_mvp/) - MVP：短链 CRUD/标签/批量导入导出/OpenAPI
- [202602191426_edge_api_split_refactor](../../history/2026-02/202602191426_edge_api_split_refactor/) - 分层边界治理（Controller→Service）、tenant guard、导入导出去 Web 类型污染
- [202602201026_redirect_experience_control](../../history/2026-02/202602201026_redirect_experience_control/) - 跳转策略字段落库与 API 回显（按链接 301/302、预览页、query 透传、不可用落地页）
- [202602201407_lifecycle_governance_closure](../../history/2026-02/202602201407_lifecycle_governance_closure/) - 生命周期治理闭环：短链归档/恢复/删除 + 列表归档筛选 + UI 能力对齐

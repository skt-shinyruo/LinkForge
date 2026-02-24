# How｜实现方案（API + DB + UI）

## 1) 短链生命周期（Link）

### 1.1 数据模型扩展（MySQL）
- 在 `short_links` 增加 `archived_at`（DATETIME NULL）。
- 列表/查询默认 **排除** 已归档记录（提升“活动链接”管理体验）。

> 说明：本次以 `archived_at` 表达“可恢复的下线/归档”。删除为不可逆动作（物理删除 + 关联数据清理）。

### 1.2 API 设计（ShortLinkController）
- `POST /api/v1/links/{id}/archive`：归档（可逆）
- `POST /api/v1/links/{id}/restore`：恢复
- `DELETE /api/v1/links/{id}`：删除（不可逆，建议要求先归档，避免误删）
- `GET /api/v1/links` 新增查询参数：
  - `archived=true|false|null`：筛选归档状态（默认 null/false：仅活动；true：仅归档）

### 1.3 业务规则（ShortLinkService）
- 归档后：
  - 记录 `archived_at=now()`；
  - 清理 redirect 缓存（`LinkCacheService.evict(code)`）；
  - Redirect Edge 查询层过滤归档记录，确保跳转不可达。
- 恢复：
  - 清空 `archived_at`；
  - 刷新缓存（`put(toMeta(e))`）。
- 删除（不可逆）：
  - 默认要求 `archived_at` 已存在（减少误删风险）；
  - 物理删除短链行；
  - 清理 `link_tags` 关联；
  - 清理统计与明细：`link_stats_daily/link_stats_dim_daily/link_visit_events`（按 tenant_id+link_id）。

## 2) 用户治理（User）

### 2.1 API 设计（UserAdminController）
- `PUT /api/v1/users/{id}/disable`：禁用用户
- `PUT /api/v1/users/{id}/enable`：启用用户
- `PUT /api/v1/users/{id}/password`：重置密码（管理员指定新密码）

### 2.2 业务规则（UserAdminService）
- 仅 `TENANT_ADMIN` 可操作。
- 跨租户禁止操作。
- 返回统一 DTO（包含 status/roles）。

> 注：禁用用户将阻止后续登录（`AuthService.login` 已校验 status），但不会强制使历史 JWT 立即失效（非本次目标）。

## 3) API Key 治理（ApiKey）

### 3.1 API 设计（ApiKeyAdminController）
- `PUT /api/v1/api-keys/{id}/disable`：吊销（禁用）
- `PUT /api/v1/api-keys/{id}/enable`：恢复启用
- `POST /api/v1/api-keys/{id}/rotate`：轮换（生成新 secret，旧 key 立即失效；仅返回一次明文）

### 3.2 业务规则（ApiKeyService）
- `disable/enable` 通过更新 `status` 控制鉴权生效（`authenticate` 已校验）。
- `rotate` 生成新 secret，更新 `key_hash`，并返回新的 `lfk_{id}_{secret}`。

## 4) 前端对齐（LinksView）

### 4.1 创建短链补齐字段
- `customCode`（可选）
- `expiresAt`（可选，ISO-8601，补齐秒）
- `tags`（可选，逗号分隔输入 → 数组）
- `note`、`originalUrl` 保持

### 4.2 编辑短链补齐字段
- 支持编辑 `originalUrl/note/expiresAt/tags`
- 保留已有“跳转策略配置”编辑能力

### 4.3 生命周期操作入口
- 活动链接：提供“归档”
- 归档链接：提供“恢复”“删除”
- 增加简单筛选：查看“活动/归档”

## 5) 文档同步（知识库）
- 更新 `helloagents/wiki/modules/shortlink.md`、`helloagents/wiki/modules/iam.md`
- 更新 `helloagents/wiki/data.md`（short_links 新字段）
- 更新 `helloagents/CHANGELOG.md` 与 `helloagents/history/index.md`


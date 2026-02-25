# Technical Design: architecture_hardening

## Key Changes（实现要点）

### 1) IAM：email 全局唯一（与登录语义对齐）

- 数据库迁移：新增 `users(email)` 全局唯一索引（并按已确认策略清理重复 email 的用户/角色关联）。  
  - `server/api-app/src/main/resources/db/migration/V6__add_users_email_global_unique.sql`
- 应用层：捕获 DB 唯一约束冲突并返回一致业务错误码。  
  - `server/api-app/src/main/java/com/linkforge/iam/service/AuthService.java`
  - `server/api-app/src/main/java/com/linkforge/iam/service/UserAdminService.java`
- 回归：集成测试覆盖“跨租户重复 email 注册/创建用户失败”。  
  - `server/api-app/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`
- 文档：更新登录/注册契约说明（email 全局唯一）。  
  - `.helloagents/wiki/api.md`

### 2) 部署：API/Edge DB 凭证拆分（最小权限）

- MySQL init：创建 `linkforge_edge`（只读）账号并授予最小表集合（当前仅 `short_links`）。  
  - `deploy/mysql-init/01-create-edge-user.sh`
- docker compose：API/Edge 使用不同 `DB_USERNAME/DB_PASSWORD`，并通过 `.env` 提供可覆写变量。  
  - `deploy/docker-compose.yml`
  - `deploy/.env.example`
- 文档：说明账号边界与验证方式。  
  - `README.md`

### 3) 安全：Cookie 模式启用 CSRF（双提交 cookie）

- 后端：当 `app.security.jwt.cookie-enabled=true` 时启用 CSRF（`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header），并对 OpenAPI/Bearer header 认证路径做忽略匹配，避免误伤非浏览器客户端。  
  - `server/api-app/src/main/java/com/linkforge/platform/security/SecurityConfig.java`
- API：提供 `GET /api/v1/auth/csrf` 作为前端初始化入口。  
  - `server/api-app/src/main/java/com/linkforge/iam/web/AuthController.java`
- 前端：cookie 模式下写请求自动附带 CSRF header；必要时先触发 `/api/v1/auth/csrf` 获取 cookie。  
  - `web/src/services/http.ts`
- 测试：集成测试覆盖“无 CSRF 失败/有 CSRF 成功”。  
  - `server/api-app/src/test/java/com/linkforge/CookieCsrfIntegrationTest.java`

### 4) Analytics：flush 性能与可控性

- UV `PFCOUNT`：使用 Redis pipeline 批量化查询，降低 RTT。  
  - `server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsFlushJob.java`
- 维度 flush 限制：增加按天处理活跃链接数上限（`app.analytics.dimensions.max-links-per-day`）以控制扫描成本，并补充日志基线。  
  - `server/shared/src/main/java/com/linkforge/platform/config/AppProperties.java`
  - `server/api-app/src/main/java/com/linkforge/analytics/service/AnalyticsDimensionFlushJob.java`
- 文档：补充 best-effort + 回补窗口 + TTL 语义。  
  - `.helloagents/wiki/modules/analytics.md`

### 5) Contract：显式化 `stats:*` Redis key 契约

- 将 `AnalyticsKeys` 提升为 public contract，并用单测锁定 key 格式。  
  - `server/shared/src/main/java/com/linkforge/analytics/service/AnalyticsKeys.java`
  - `server/shared/src/test/java/com/linkforge/analytics/service/AnalyticsKeysTest.java`

### 6) 配置校验：去重与一致性

- 抽取 API/Edge 公共启动期校验逻辑到 shared，减少漂移点。  
  - `server/shared/src/main/java/com/linkforge/platform/config/StartupValidation.java`
  - `server/api-app/src/main/java/com/linkforge/api/ApiStartupValidator.java`
  - `server/edge-app/src/main/java/com/linkforge/edge/EdgeStartupValidator.java`

### 7) OpenAPI：API Key 写热点治理

- 对 `api_keys.last_used_at` 采用节流写回（默认 300s，可配置 `app.security.api-key.last-used-update-interval-seconds`），并在写回失败时 fail-open。  
  - `server/api-app/src/main/java/com/linkforge/iam/api/ApiKeyService.java`
- 单测：验证节流逻辑不会在窗口内重复写库。  
  - `server/api-app/src/test/java/com/linkforge/iam/api/ApiKeyServiceTest.java`

### 8) 工程卫生：构建产物不入库

- 统一忽略 `**/target/` 并清理工作区中的 `target/` 产物。  
  - `.gitignore`

## Notes

- MySQL init 脚本仅在“全新数据卷”时执行；如需重新初始化账号/权限，请清理 `mysql_data` 数据卷后重启。

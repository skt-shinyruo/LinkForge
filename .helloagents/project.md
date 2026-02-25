# LinkForge 项目技术约定（SSOT）

> 本文件用于记录项目统一技术栈、目录规范、接口约定与工程规范；与代码实现保持一致（当文档与代码冲突时，以代码为准并更新文档）。

---

## 1. 技术栈（现状与目标）

- 后端：
  - **现状构建基线：Java 17**（`server/pom.xml`）
  - 目标：Java 21（当运行/CI 环境具备时可上调）
  - Spring Boot 3.2.x，Maven 多模块（`shared` + `api-app` + `edge-app`）
- 鉴权：
  - 管理后台/自助创建：JWT（`Authorization: Bearer <token>`）
  - 可选：JWT **HttpOnly Cookie** 模式（适用于更安全的浏览器会话）
  - OpenAPI：API Key（`X-API-Key`）
- 数据库：MySQL 8.x（核心业务数据）
- 缓存：Redis 7.x（短链解析缓存、统计计数/去重结构、活跃索引集合）
- 统计链路（当前实现）：Redirect Edge 轻量写 Redis；API Service 定时增量 flush 落库（active-set 驱动）
- 前端：Vue 3 + Vite + TypeScript（管理后台）
- 部署：Docker / Docker Compose（`deploy/docker-compose.yml`），Nginx 统一转发 `/api` 与 `/r`

---

## 2. 仓库目录建议

- `server/`：后端（Maven 多模块）
  - `server/shared/`：跨服务 SSOT（`ApiResponse`、`ErrorCode`、`BusinessException`、RequestId、配置约定等）
  - `server/api-app/`：API Service（`/api/v1/**`）
  - `server/edge-app/`：Redirect Edge Service（`/r/**`）
  - `server/Dockerfile.api`、`server/Dockerfile.edge`：分别构建两个后端镜像
- `web/`：Vue 管理后台
- `deploy/`：Docker Compose、环境示例
- `.helloagents/`：知识库（SSOT），包含方案包与历史归档

---

## 3. API 约定

### 3.1 版本与路径
- 管理后台/自助创建 API：`/api/v1/**`
- OpenAPI：`/api/v1/open/**`（API Key）
- 公开跳转：`/r/{code}`（推荐与管理后台不同域名/子域名部署）

### 3.2 响应包裹格式（实现对齐）
```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "..."
}
```

### 3.3 RequestId 约定
- Header：`X-Request-Id`
- 服务端会生成/透传 requestId，并在错误响应与日志中保持一致，便于排障。

### 3.4 鉴权头 / Cookie
- JWT Bearer：`Authorization: Bearer <token>`
- JWT Cookie（可选）：浏览器侧使用 `credentials: include`；服务端可在登录/注册时下发 HttpOnly Cookie
- OpenAPI：`X-API-Key: <key>`（服务端存储哈希，不存明文）

---

## 4. 多租户与权限约定

- 逻辑多租户：核心业务表均包含 `tenant_id`
- 权限模型：`tenant_admin` / `user`（预留 `sys_admin`）
- 约束：
  - 任何需要租户隔离的查询必须显式带 `tenant_id` 条件（禁止“仅靠前端传参”）
  - Service 层必须做 tenant guard（以认证主体为准），避免越权与“误用 tenantId 参数”的风险

---

## 5. 日志与可观测性

- 日志：SLF4J + Logback（JSON/结构化日志建议）
- 链路字段：`requestId`、`tenantId`、`userId`（注意脱敏）
- 安全：禁止在日志中输出密码、Token、API Key 明文

---

## 6. 测试与质量

- 单元测试：JUnit 5
- 集成测试：Spring Boot Test + Testcontainers（MySQL/Redis）
- 负载验证（建议）：在关键跳转链路做简单压测（QPS 1000 基线）

---

## 7. 本地构建与运行（参考）

> 以 Docker Compose 方式启动最省心（同时包含 MySQL/Redis/API/Edge/Web）。

### 7.1 Docker Compose（推荐）
- 启动：`cd deploy && docker compose up --build`
- 访问：
  - 管理后台：`http://localhost/`
  - API：`http://localhost/api/v1/...`
  - 跳转：`http://localhost/r/{code}`

### 7.2 后端本地运行（IDE / Maven）
- API Service：`server/api-app`（默认 8080）
- Edge Service：`server/edge-app`（默认 8081）
- 根目录聚合测试：`cd server && mvn test`

### 7.3 关键环境变量（与 deploy 对齐）
- DB：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`
- Redis：`REDIS_HOST`、`REDIS_PORT`
- Auth：`JWT_SECRET`；可选 Cookie：`JWT_COOKIE_ENABLED`、`JWT_COOKIE_NAME`、`JWT_COOKIE_SECURE`、`JWT_COOKIE_SAMESITE`
- Analytics：`ANALYTICS_SALT`
- Base URL：`APP_BASE_URL`（用于生成 shortUrl）
- Id：`ID_WORKER_ID`、`ID_DATACENTER_ID`
- Config：`APP_STRICT_CONFIG`（或 `app.strict-config`）
- CORS：`CORS_ALLOWED_ORIGINS`、`CORS_ALLOW_CREDENTIALS`

---

## 8. 提交规范（建议）

采用 Conventional Commits：
- `feat:` 新功能
- `fix:` 修复
- `refactor:` 重构
- `docs:` 文档
- `chore:` 杂项/构建

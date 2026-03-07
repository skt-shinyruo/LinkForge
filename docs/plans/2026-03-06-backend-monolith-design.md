# Backend Monolith (Single Maven Module) Design

**Date:** 2026-03-07

## Goal

将 `server/` 后端从“API + Edge 两个 Spring Boot 应用（多 Maven module）”改为：

- **一个 Maven 单模块**
- **一个 Spring Boot 可部署服务（单端口，默认 `8080`）**
- 同时对外提供：
  - 管理 API：`/api/v1/**`（保持 `ApiResponse` JSON 契约不变）
  - 跳转链路：`/r/**`（保持现有 Edge 的 HTML/JSON 行为不变）

## Current State (Before)

- Maven 多模块：`platform` / `api-contract` / `api` / `edge`
- 两个可部署服务：
  - `server/api`：鉴权、短链管理、统计查询、定时任务（flush / ingest / outbox）
  - `server/edge`：跳转 `/r/{code}`、风控、缓存回源、统计写 Redis

## Proposed Architecture (After)

### 1) Single Maven module

`server/pom.xml` 改为单模块 `jar`，依赖为原各模块依赖集合（去重后）：

- Spring Boot Web / Validation / Security / Data JPA / Redis / JDBC / Actuator / AOP
- Flyway + MySQL driver
- JWT（jjwt）
- ShedLock（Redis provider）
- 测试（starter-test + Testcontainers + ArchUnit）

### 2) One Spring Boot application

新增单体启动入口类（示例）：

- `com.linkforge.LinkForgeApplication`
- `@SpringBootApplication(scanBasePackages = "com.linkforge")`
- 启用 JPA entity/repo 扫描（等价于原 API 启动类）

### 3) Routing/Port

- 服务端口：`server.port=8080`
- 保持路由前缀不变：
  - `/api/v1/**`：管理 API
  - `/r/**`：跳转

### 4) Error contract stays split (API vs Edge)

单体合并后仍保留两套错误结构：

- API：`ApiResponse` + `GlobalExceptionHandler`（`com.linkforge.contract.api.*`）
- Edge：`EdgeErrorResponse` + `EdgeGlobalExceptionHandler`（`com.linkforge.edge.web.error.*`）

关键点：避免 `@RestControllerAdvice` 互相“抢异常”，通过将 Advice 限定到各自的 controller package（`basePackages`）实现隔离：

- API Advice 仅作用于 `com.linkforge.api..`
- Edge Advice 仅作用于 `com.linkforge.edge..`

### 5) Security must not affect `/r/**`

原拆分架构中，Edge 服务不包含 Security；合并后若 Security FilterChain/Filter 作用于 `/r/**`，可能出现“带了过期 cookie/JWT 导致跳转被 401 拦截”的行为变化。

设计选择：将 Spring Security `SecurityFilterChain` 仅匹配 `/api/**`，避免任何安全过滤器影响 `/r/**`。

### 6) Config merge

合并 `api` 与 `edge` 的 `application.yml` 为单份：

- 数据库 / Redis / Flyway / JPA / 端口
- `app.*` 统一（base-url、jwt、redirect、analytics、edge 风控配置等）

### 7) Deploy & Frontend proxy updates

因为后端只剩一个服务，需同步更新：

- `deploy/docker-compose.yml`：移除 `server-api`/`server-edge`，替换为单个 `server`
- `web/nginx.conf`：`/api/` 与 `/r/` 都代理到 `server:8080`
- `web/vite.config.ts`：本地开发代理 `/api` 与 `/r` 都指向 `http://localhost:8080`

## Migration Strategy

1. 在 `server/` 下建立标准 Maven 单模块结构：
   - `server/src/main/java`
   - `server/src/main/resources`
   - `server/src/test/java`
2. 将原 `platform` / `api-contract` / `api` / `edge` 的源码与资源迁移到上述目录（保留 package 命名）。
3. 新增单体启动类，修正所有 `@SpringBootTest` 引用的启动类。
4. 修正异常处理 Advice 的 package 范围与 Security matcher，保证 `/r/**` 行为不变。
5. 更新 deploy 与前端代理配置。
6. 跑 `mvn test`，确保功能与契约一致。

## Success Criteria

- `cd server && mvn test` 通过
- 单体服务启动后：
  - `/api/v1/**` 返回结构与现有 API 相同（`ApiResponse`）
  - `/r/**` 在 `Accept: text/html` 下返回 HTML/redirect，在非 HTML 下返回 Edge JSON 错误结构
- 前端/反代（`web/nginx.conf`、Vite proxy、compose）无需改变对外 URL（仍为 `/api/**` 与 `/r/**`）

## Testing & Guardrails

单体合并后，原先“模块级别”的架构约束测试需要调整其扫描范围，否则会把整个 monolith 都当成 platform-shared 扫描导致误报。

调整策略：

- `PlatformSharedArchitectureTest`：只约束“纯共享包”（例如 `com.linkforge.platform.config/id/tx/util` 以及纯工具类包），避免包含 `platform.web` / `redirect` 等运行时包。
- 测试环境默认关闭定时任务（`app.scheduling.enabled=false`），避免在集成测试 teardown 阶段出现 Redis/MySQL 连接重置带来的噪音与潜在抖动。

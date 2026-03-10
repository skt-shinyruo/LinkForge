# Backend Monolith (Single Port) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将后端统一为一个 Maven 单模块 + 一个 Spring Boot 单体服务（单端口 `8080`），同时提供 `/api/**` 与 `/r/**`，并同步更新 compose / Nginx / Vite 代理与测试守护规则。

**Architecture:** 维持包级边界（`com.linkforge.api..` 与 `com.linkforge.edge..`），运行时为单进程单端口；Security FilterChain 仅匹配 `/api/**`，`/r/**` 走公开跳转链路。

**Tech Stack:** Java 17, Spring Boot 3.2.x, Spring Security, JPA(Flyway/MySQL), Redis, Testcontainers, ArchUnit

---

### Task 1: 修正 ArchUnit “platform-shared” 扫描范围

**Files:**
- Modify: `server/src/test/java/com/linkforge/platform/architecture/PlatformSharedArchitectureTest.java`

**Step 1: 收窄 importer 仅导入纯共享包**

目标：避免在单体工程中把整个 `com.linkforge..` 都当成 “platform-shared” 扫描，从而误报 API/Edge 的 runtime bean 与 web/redis 依赖。

建议导入包（示例）：
- `com.linkforge.platform.config`
- `com.linkforge.platform.id`
- `com.linkforge.platform.tx`
- `com.linkforge.platform.util`
- （可选）`com.linkforge.analytics.service`（纯 key 计算类）

**Step 2: 让规则仅作用于上述包**

- “不得定义 Spring runtime bean”规则仅检查纯共享包
- “不得依赖 web/redis”规则仅检查纯共享包

**Step 3: 运行该测试确认通过**

Run: `cd server && mvn -Dtest=PlatformSharedArchitectureTest test`
Expected: PASS

---

### Task 2: 测试环境禁用定时任务（降低 flakiness）

**Files:**
- Create: `server/src/test/resources/application.properties`

**Step 1: 添加测试配置**

内容：
- `app.scheduling.enabled=false`

**Step 2: 运行最小验证**

Run: `cd server && mvn -Dtest=AnalyticsFlushJobTest test`
Expected: PASS（且不再出现 scheduler 在 teardown 阶段打 Redis 的噪音）

---

### Task 3: 合并 Dockerfile 为单体镜像构建

**Files:**
- Create: `server/Dockerfile`
- Delete: `server/Dockerfile.api`
- Delete: `server/Dockerfile.edge`

**Step 1: 新增单体 Dockerfile**

要求：
- build stage 只需要 `pom.xml` + `src/`
- `mvn -DskipTests package`
- runtime stage 暴露 `8080`
- 复制 `target/linkforge-server-*.jar` 为 `/app/app.jar`

**Step 2: 删除旧 Dockerfile（api/edge）**

确认不再有 compose/文档引用它们。

---

### Task 4: docker-compose 合并为单个 `server` 服务

**Files:**
- Modify: `deploy/docker-compose.yml`
- Modify: `web/nginx.conf`
- Modify: `web/vite.config.ts`

**Step 1: compose 只保留一个后端服务**

- 移除 `server-api` 与 `server-edge`
- 新增 `server`：端口 `8080:8080`
- 环境变量合并（DB/Redis/JWT/ANALYTICS/EDGE_TRUSTED_PROXIES 等）
- `web` 只依赖 `server`

**Step 2: Nginx 同端口反代**

- `/api/` → `http://server:8080`
- `/r/` → `http://server:8080`

**Step 3: Vite dev proxy 同端口**

- `/api` → `http://localhost:8080`
- `/r` → `http://localhost:8080`

---

### Task 5: 更新 README 与示例环境变量

**Files:**
- Modify: `README.md`
- Modify: `deploy/.env.example`

**Step 1: README 开发启动改为单服务**

将后端开发说明改为：
- `cd server && mvn spring-boot:run`

更新端口说明：
- API 与 Redirect 都在 `http://localhost:8080`
- 通过 Nginx 访问仍为 `http://localhost/api/...` 与 `http://localhost/r/...`

**Step 2: `.env.example` 移除/弱化 Edge 独立账号描述**

单体模式下不再需要 `MYSQL_EDGE_USER` / `MYSQL_EDGE_PASSWORD` 的“只读账号”叙述（建议直接移除，避免误导）。

---

### Task 6: 清理遗留多模块配置文件（避免误导）

**Files:**
- Delete: `server/api/src/main/resources/application.yml`
- Delete: `server/edge/src/main/resources/application.yml`
- Delete: `server/api/src/test/resources/application.properties`

**Step 1: 删除旧配置文件**

确保单体配置 SSOT 为：
- `server/src/main/resources/application.yml`
- `server/src/main/resources/application-local.yml`

---

### Task 7: 清理无关 IDE 配置变更（可选但建议）

**Files:**
- Modify: `.vscode/settings.json`

**Step 1: 仅保留项目必要配置**

避免提交与本次后端架构无关的 editor 偏好。

---

### Task 8: 全量验证

**Step 1: 跑全套测试**

Run: `cd server && mvn test`
Expected: PASS

**Step 2:（可选）本地一键启动 smoke test**

Run: `cd deploy && docker compose --env-file .env up --build`
Expected:
- `http://localhost/` 前端可打开
- `http://localhost/api/v1/auth/csrf` 可返回
- `http://localhost/r/{code}` 可跳转/返回 HTML

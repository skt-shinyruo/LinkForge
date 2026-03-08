# LinkForge（短链系统 MVP）

本仓库包含一个基于 **Java Spring Boot + Vue3** 的短链系统 MVP，实现了多租户、登录鉴权、短链管理、跳转与基础统计，并提供 OpenAPI（API Key）。

## 1. 本地一键启动（推荐）

1) 复制环境文件（不要提交 `deploy/.env`）：

- `deploy/.env.example` → `deploy/.env`

2) 在 `deploy/.env` 中配置：

- `JWT_SECRET`：长度至少 32 bytes
- `ANALYTICS_SALT`：用于统计访客指纹 hash 的盐
- `APP_BASE_URL`：创建短链时用于拼接 shortUrl（建议指向反向代理/网关域名；本地默认 `http://localhost`）
- （可选/生产建议）`EDGE_TRUSTED_PROXIES`：可信代理链（CIDR）。当 `/r/**` 经 Nginx/网关反代时需要配置，否则客户端 IP/UV 统计可能严重失真
- （可选）MySQL 账号（默认值可直接使用）：
  - `MYSQL_API_USER` / `MYSQL_API_PASSWORD`：后端服务读写账号（Flyway 迁移与业务写入）

3) 启动：

```bash
cd deploy
docker compose --env-file .env up --build
```

访问：

- 管理后台：`http://localhost/`
- 后端服务：`http://localhost:8080/`（管理 API：`/api/v1/**`；跳转：`/r/**`）
- 跳转（推荐通过反代访问）：`http://localhost/r/{code}`

## 2. 本地开发（前后端分离）

后端：

```bash
cd server
mvn test
mvn -Pit test   # 运行集成测试（需要 Docker/Testcontainers）
mvn -pl app spring-boot:run
```

前端：

```bash
cd web
npm install
npm run dev
```

默认 Vite 代理已配置：
- `/api` → `http://localhost:8080`
- `/r` → `http://localhost:8080`

## 3. 方案与文档（SSOT）

- `docs/plans/`：方案与实现计划（SSOT）

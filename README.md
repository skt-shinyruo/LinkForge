# LinkForge（短链系统 MVP）

本仓库包含一个基于 **Java Spring Boot + Vue3** 的短链系统 MVP，实现了多租户、登录鉴权、短链管理、跳转与基础统计，并提供 OpenAPI（API Key）。

本项目是平台团队给多个内部系统提供统一短链基础设施

## 1. 本地一键启动（推荐）

1) 复制环境文件（不要提交 `deploy/.env`）：

- `deploy/.env.example` → `deploy/.env`

2) 在 `deploy/.env` 中配置：

- `JWT_SECRET`：长度至少 32 bytes
- `ANALYTICS_SALT`：用于统计访客指纹 hash 的盐
- （可选）`ANALYTICS_VISIT_STREAM_MAX_LEN`：基础 PV/UV 使用的 Redis 访问流近似最大长度，默认 `200000`
- （可选）`ANALYTICS_EVENTS_ENABLED` / `ANALYTICS_EVENTS_SAMPLE_RATE`：访问明细落库开关与采样率；只影响 `link_visit_events` 明细，不影响基础 PV/UV
- `APP_BASE_URL`：创建短链时用于拼接 shortUrl（建议指向反向代理/网关域名；本地默认 `http://localhost:18080`）
- （可选）`LINKFORGE_HTTP_BIND` / `LINKFORGE_HTTP_PORT`：本地 compose 网关监听地址与端口，默认 `127.0.0.1:18080`
- （可选/生产建议）`EDGE_TRUSTED_PROXIES`：可信代理链（CIDR）。当 `/r/**` 经 Nginx/网关反代时需要配置，否则客户端 IP/UV 统计可能严重失真
- （可选）MySQL 账号（默认值可直接使用）：
  - `MYSQL_API_USER` / `MYSQL_API_PASSWORD`：主库读写账号（Flyway 迁移与业务写入）
  - `MYSQL_READ_USER` / `MYSQL_READ_PASSWORD`：从库只读账号（ShardingSphere-JDBC 读流量）
  - `MYSQL_REPLICATION_USER` / `MYSQL_REPLICATION_PASSWORD`：MySQL 主从复制账号

3) 启动：

```bash
cd deploy
docker compose --env-file .env up --build
```

本地 compose 使用 `mysql-primary` + `mysql-replica` 模拟 MySQL 主从部署，后端通过 ShardingSphere-JDBC 暴露一个逻辑数据源。Flyway 固定连接主库，业务写入走 `write_ds`，符合条件的非事务查询可走 `read_ds_0`；事务内读保持走主库，降低复制延迟导致的写后读不一致风险。

如果修改 MySQL 初始化账号、复制参数或需要重新初始化主从数据卷，请先通过 `docker compose down -v` 停止并删除旧卷；本项目命令示例带上环境文件：

```bash
cd deploy
docker compose --env-file .env down -v
```

访问：

- 管理后台：`http://localhost:18080/`
- 后端服务：通过网关访问 `http://localhost:18080/api/v1/**`
- 跳转：`http://localhost:18080/r/{code}`
  - 说明：短码 `code` **严格区分大小写**（例如 `Abcdef` ≠ `abcdef`）

本地 compose 只向宿主机发布 Nginx 网关入口；MySQL、Redis、后端服务仅在 Docker 内网可见，避免占用 `3306`、`6379`、`8080` 等常用端口。

## 2. 本地开发（前后端分离）

后端：

```bash
cd server
mvn test
mvn verify       # 单元测试 + JaCoCo 报告，不需要 Docker
mvn -Pit verify  # 额外运行集成测试（需要 Docker/Testcontainers）
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

- `docs/reference/README.md`：项目参考文档索引
- `docs/reference/architecture.md`：当前模块化单体架构总览（推荐先读）
- `docs/reference/core-logic.md`：当前代码核心业务逻辑、跨上下文链路和前端页面逻辑
- `docs/superpowers/plans/`：方案与实现计划（SSOT）

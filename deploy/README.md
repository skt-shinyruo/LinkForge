# LinkForge 部署说明

本目录用于本地一键运行 LinkForge。`docker-compose.yml` 会拉起一套完整环境：

- `web`：Vue3 管理后台的 Nginx 容器，同时作为统一网关。
- `server`：Spring Boot 后端服务。
- `mysql-primary` / `mysql-replica`：MySQL 主从拓扑，用于模拟读写分离部署。
- `redis`：短链跳转与统计相关缓存/队列依赖。

默认只向宿主机暴露 Nginx 网关端口，MySQL、Redis 和后端服务仅在 Docker 内网可见。

## 前置条件

- Docker
- Docker Compose v2（使用 `docker compose` 命令）

## 快速启动

以下命令均在项目根目录执行。

1. 复制环境变量模板：

```bash
cp deploy/.env.example deploy/.env
```

2. 编辑 `deploy/.env`，至少确认以下配置：

- `JWT_SECRET`：生产或共享环境请使用长度不少于 32 bytes 的随机密钥。
- `ANALYTICS_SALT`：统计访客指纹 hash 使用的盐。
- `APP_BASE_URL`：创建短链时拼接 `shortUrl` 的基准地址，本地默认 `http://localhost:18080`。
- `LINKFORGE_HTTP_BIND` / `LINKFORGE_HTTP_PORT`：网关监听地址和端口，本地默认 `127.0.0.1:18080`。

MySQL 账号默认值可直接用于本地开发；如需修改，请在首次启动前修改 `deploy/.env`。

3. 启动全部服务：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up --build
```

首次启动会构建前端和后端镜像，并初始化 MySQL 主库、从库复制和数据库结构。

## 访问地址

默认配置下：

- 管理后台：`http://localhost:18080/`
- 后端 API：`http://localhost:18080/api/v1/**`
- 短链跳转：`http://localhost:18080/r/{code}`

短码 `code` 严格区分大小写，例如 `Abcdef` 和 `abcdef` 是两个不同短码。

## 常用命令

后台启动：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up --build -d
```

查看服务状态：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

查看日志：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f
```

停止服务：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

停止并删除 MySQL 数据卷：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v
```

只有在需要重新初始化数据库、MySQL 账号或主从复制参数时才使用 `down -v`，因为它会删除本地数据库数据。

## 配置说明

`deploy/.env.example` 是本地运行模板，复制出的 `deploy/.env` 不应提交到仓库。

常用配置：

- `MYSQL_ROOT_PASSWORD`：MySQL root 密码。
- `MYSQL_API_USER` / `MYSQL_API_PASSWORD`：后端写库账号，Flyway 迁移和业务写入使用。
- `MYSQL_READ_USER` / `MYSQL_READ_PASSWORD`：后端读库账号，读流量访问从库使用。
- `MYSQL_REPLICATION_USER` / `MYSQL_REPLICATION_PASSWORD`：MySQL 主从复制账号。
- `ANALYTICS_VISIT_STREAM_MAX_LEN`：基础 PV/UV Redis Stream 的近似最大长度。
- `ANALYTICS_EVENTS_ENABLED` / `ANALYTICS_EVENTS_SAMPLE_RATE`：访问明细落库开关和采样率，不影响基础 PV/UV。
- `EDGE_TRUSTED_PROXIES`：可信代理 CIDR。生产环境应按实际网关/反代地址精确配置。
- `ID_WORKER_ID` / `ID_DATACENTER_ID`：Snowflake 节点参数，多实例部署时必须保证唯一。
- `APP_STRICT_CONFIG`：启动期严格配置校验，生产环境建议开启。

## 注意事项

- MySQL 初始化脚本只会在全新数据卷上执行。修改 MySQL 初始化账号、复制账号或复制参数后，需要执行 `docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v` 再重新启动。
- Compose 内部使用 `mysql-primary` + `mysql-replica` 模拟主从部署；后端通过 ShardingSphere-JDBC 暴露逻辑数据源，写入走主库，符合条件的非事务查询可走从库。
- 本地默认绑定 `127.0.0.1:18080`，外部机器无法直接访问。如需共享访问，请谨慎调整 `LINKFORGE_HTTP_BIND`、`APP_BASE_URL` 和相关安全配置。

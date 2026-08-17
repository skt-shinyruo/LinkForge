# LinkForge 部署说明

本目录用于本地一键运行 LinkForge。`docker-compose.yml` 会拉起一套完整环境：

- `web`：Vue3 管理后台的 Nginx 容器，同时作为统一网关。
- `server`：Spring Boot 后端服务。
- MySQL：业务数据库。
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
- `API_KEY_CURRENT_KEY_ID` / `API_KEY_CURRENT_PEPPER`：API Key 摘要当前 keyring 项；key id 最多 64 个字符，pepper 必须独立于 JWT secret。升级已有部署时不要删除原 `API_KEY_HMAC_PEPPER`，先按下方 runbook 完成兼容混部。
- `ANALYTICS_SALT`：统计访客指纹 hash 使用的盐。
- `APP_STRICT_CONFIG`：默认开启；如果保留模板中的示例密钥，后端会拒绝启动。
- `AUTH_REGISTRATION_ENABLED`：自助注册开关，默认关闭。本地首次创建租户可临时设为 `true`，生产建议保持关闭并使用受控初始化/邀请流程。
- `APP_BASE_URL`：创建短链时拼接 `shortUrl` 的基准地址，本地默认 `http://localhost:18080`。
- `LINKFORGE_HTTP_BIND` / `LINKFORGE_HTTP_PORT`：网关监听地址和端口，本地默认 `127.0.0.1:18080`。
- `JWT_COOKIE_ENABLED` / `VITE_AUTH_MODE`：如启用 HttpOnly Cookie 会话，后端需 `JWT_COOKIE_ENABLED=true`，前端镜像构建需 `VITE_AUTH_MODE=cookie`。

MySQL 账号默认值可直接用于本地开发；如需修改，请在首次启动前修改 `deploy/.env`。

3. 启动全部服务：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up --build
```

首次启动会构建前端和后端镜像，并初始化 MySQL 数据库结构。`server` 通过 management 端口 `8081` 的 `/actuator/health` 报告健康状态；`web` 等待该状态后启动，并通过自身首页 healthcheck 报告网关就绪。网关的 `/healthz` 只反向代理这个汇总健康响应，不暴露其他 management 端点。

如需执行与 CI 相同的干净镜像 smoke，可运行：

```bash
bash deploy/smoke.sh
```

脚本使用独立的 `linkforge-smoke` Compose project，分别验证前端首页、网关后的后端健康状态和公开 CSRF API，并在结束后清理该 smoke project。设置 `SMOKE_KEEP_STACK=true` 可在失败后保留现场。

## 访问地址

默认配置下：

- 管理后台：`http://localhost:18080/`
- 汇总健康状态：`http://localhost:18080/healthz`
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

只有在需要重新初始化数据库或 MySQL 账号时才使用 `down -v`，因为它会删除本地数据库数据。

## 配置说明

`deploy/.env.example` 是本地运行模板，复制出的 `deploy/.env` 不应提交到仓库。

常用配置：

- `MYSQL_ROOT_PASSWORD`：MySQL root 密码。
- `MYSQL_API_USER` / `MYSQL_API_PASSWORD`：后端业务账号。
- `API_KEY_CURRENT_KEY_ID` / `API_KEY_CURRENT_PEPPER`：新 API Key 摘要使用的当前 keyring 项；current key id 最多 64 个字符。
- `API_KEY_PREVIOUS_KEY_ID` / `API_KEY_PREVIOUS_PEPPER`：轮换兼容窗口内的上一代 keyring 项；previous key id 最多 64 个字符，必须成对配置。
- `API_KEY_LEGACY_PEPPER`：仅用于验证无 key id 的历史 HMAC 摘要；新部署保持为空。
- `API_KEY_HMAC_PEPPER`：旧版单 pepper 变量。Compose 继续转发它并在 current/legacy 未设置时作为 fallback，仅用于滚动升级和旧二进制兼容。
- `API_KEY_LEGACY_JWT_FALLBACK_ENABLED`：仅用于非生产历史兼容；严格配置必须为 `false`。
- `ANALYTICS_QUOTA_FAIL_OPEN`：Platform quota 查询异常时是否放行，生产建议保持 `false`。
- `ANALYTICS_QUOTA_LOOKUP_CACHE_TTL_SECONDS` / `ANALYTICS_QUOTA_LOOKUP_CACHE_MAX_ENTRIES`：Redirect quota 查询缓存边界。
- `ANALYTICS_EVENTS_FAIL_OPEN`：Redis 聚合写入失败时是否继续 Redirect。设为 `true` 时必须监控 `linkforge.analytics.fail_open`，因为放行不代表统计已持久化。
- `EDGE_TRUSTED_PROXIES`：可信代理 CIDR。生产环境应按实际网关/反代地址精确配置。
- `ID_WORKER_ID` / `ID_DATACENTER_ID`：Snowflake 节点参数，多实例部署时必须保证唯一。
- `APP_STRICT_CONFIG`：启动期严格配置校验，默认开启；会拒绝示例 JWT secret、示例 analytics salt 和默认 Snowflake 节点组合。
- `AUTH_REGISTRATION_ENABLED`：自助注册开关，默认关闭。
- `VITE_AUTH_MODE`：前端构建认证模式，`bearer` 或 `cookie`。必须与后端 `JWT_COOKIE_ENABLED` 保持一致。

## API Key pepper 滚动升级

不要用新模板覆盖现有 `deploy/.env`。从旧版单 pepper 配置升级前，先确认并保留当前
`API_KEY_HMAC_PEPPER`，整个过程都保持 `API_KEY_LEGACY_JWT_FALLBACK_ENABLED=false`。

1. 兼容混部：保持 `API_KEY_HMAC_PEPPER` 为原值；设置 `API_KEY_CURRENT_KEY_ID=v1`，并让
   `API_KEY_CURRENT_PEPPER` 与原值相同。`API_KEY_LEGACY_PEPPER` 可显式设为原值，也可使用 Compose fallback。
   先部署支持 keyring 的实例并观察认证错误；不要在仍有旧实例时引入新 pepper。
2. 真正轮换：确认全部旧实例已停止，并接受从此不能安全回滚到旧二进制。把原 key id/pepper 成对移到
   `API_KEY_PREVIOUS_KEY_ID=v1` / `API_KEY_PREVIOUS_PEPPER`，原 pepper 同时保留在 `API_KEY_LEGACY_PEPPER`；
   再设置新的 `API_KEY_CURRENT_KEY_ID` 和独立 `API_KEY_CURRENT_PEPPER`，滚动重启当前实例。
3. 收缩兼容：认证成功会 CAS 升级旧摘要，但长期不用或 disabled 的 key 不会自动迁移。确认数据库中不再有旧
   key id，且不再有 `key_id IS NULL` 的 HMAC 行；剩余凭据应先逐个轮换、撤销或删除。只有盘点归零后才能移除
   previous/legacy pepper 和旧 `API_KEY_HMAC_PEPPER` 兼容变量。

任一阶段都不要把 pepper 或原始 API Key 写入命令输出、日志、工单和审计字段。current、previous、legacy 与旧
compatibility pepper 可以在第一阶段按上述要求临时指向同一 API Key secret，但都必须独立于 `JWT_SECRET`。

## 注意事项

- MySQL 初始化脚本和 `database/schema.sql` 只会在全新数据卷上执行。修改 schema 或 MySQL 初始化账号后，需要执行 `docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v` 再重新启动。
- Compose 内部使用一个 MySQL 和一个 Redis；后端通过 Spring 标准数据源连接 MySQL，需要写后读一致性的操作在同一数据库事务中完成。
- 后端健康检查依赖运行时镜像中的 `curl` 和 Spring Boot Actuator management 端口 `8081`。如果健康检查失败，`web` 不会接入后端流量，请先查看 `server` 日志。
- 本地默认绑定 `127.0.0.1:18080`，外部机器无法直接访问。如需共享访问，请谨慎调整 `LINKFORGE_HTTP_BIND`、`APP_BASE_URL` 和相关安全配置。

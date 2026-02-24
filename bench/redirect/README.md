# Redirect 压测（k6）

本目录提供 LinkForge `/r/{code}` 跳转链路的基线压测脚本与结果记录，便于做容量评估与回归对比。

⚠️ 注意：默认仅面向本地/测试环境使用，禁止对生产环境直接执行压测。

---

## 1. 前置条件

1) 启动服务（推荐 docker-compose）：

- `cd deploy`
- `cp .env.example .env` 并填好 `JWT_SECRET`、`ANALYTICS_SALT`、`APP_BASE_URL`
- `docker compose --env-file .env up --build`

2) 准备一个可用短码 `CODE`

你可以通过管理后台创建短链，或用 curl 创建：

- 注册：`POST /api/v1/auth/register`
- 创建短链：`POST /api/v1/links`

创建成功后拿到返回的 `code` 作为压测的 `CODE`。

---

## 2. 运行方式（推荐：Docker 运行 k6）

在仓库根目录执行：

```bash
docker run --rm -i \
  -v "$PWD/bench/redirect:/scripts" \
  grafana/k6:latest run /scripts/redirect.js \
  -e BASE_URL="http://localhost:8080" \
  -e CODE="<your_code>" \
  -e RATE="1000" \
  -e DURATION="30s" \
  -e PRE_ALLOCATED_VUS="200" \
  -e MAX_VUS="2000"
```

参数说明：
- `BASE_URL`：目标服务地址（建议直连 server：`http://localhost:8080`）
- `CODE`：短码（必填）
- `RATE`：目标 RPS（每秒请求数）
- `DURATION`：持续时间（例如 `30s` / `1m`）
- `PRE_ALLOCATED_VUS`、`MAX_VUS`：k6 常量到达率执行器的 VU 配置

---

## 3. 结果记录

基线测试结果见：`bench/redirect/results.md`


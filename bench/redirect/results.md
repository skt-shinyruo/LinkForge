# Redirect 压测结果记录

本文件记录 `/r/{code}` 基线压测结果，便于后续迭代对比。

---

## 2026-02-19 基线（目标 1000 RPS）

> 说明：本结果来自本地环境，主要用于回归对比，不代表生产真实性能。

- 目标：1000 RPS（k6 constant-arrival-rate）
- 运行脚本：`bench/redirect/redirect.js`
- 运行方式：Docker `grafana/k6`
- 目标地址：`http://localhost:8080/r/{code}`

### 环境信息
- OS: Linux (WSL2) 6.6.87.2-microsoft-standard-WSL2 x86_64
- CPU: AMD Ryzen 9 9950X 16-Core Processor（32 vCPU / 16C32T）
- Mem: 47Gi
- 部署方式：`deploy/docker-compose.yml`（MySQL + Redis + server + web）

### 关键指标（k6 输出）
- 吞吐：`http_reqs=30001`，约 `1077 req/s`
- http_req_failed：`0.00%`（0/30001）
- http_req_duration：avg `1.24ms`，p(95) `1.35ms`，max `66.21ms`

### 结论
- 在本地 docker-compose 环境下，Redirect `/r/{code}` 可稳定支撑 1000 RPS 级别基线压测，错误率为 0%，p95 延迟在 2ms 内。
- 该结果用于回归对比；生产环境仍需结合网络、实例规格、Redis/MySQL 负载与链路代理情况重新测量。

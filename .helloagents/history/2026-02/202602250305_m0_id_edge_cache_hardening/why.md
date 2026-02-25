# Change Proposal: m0_id_edge_cache_hardening

## Requirement Background

LinkForge 后端已拆分为 API Service + Redirect Edge Service，并在 `server/shared` 中引入 Snowflake ID 与跨服务 SSOT（响应体/错误码/缓存契约等）。

在进入“可水平扩容 + 公网承压”的阶段前，存在两类 M0 风险需要先补齐护栏：

1) **多实例 ID 冲突风险（P0）**：Snowflake 的 `workerId/datacenterId` 默认值为 `1/1`，在多实例部署时若忘记显式配置，极易产生 ID 冲突（主键冲突/数据错写）。
2) **Redirect 缓存穿透风险（P1）**：`/r/{code}` 属于公网高暴露入口。当前 cache-aside 仅缓存命中结果；随机短码扫描会导致“每次 miss 都回源 MySQL”，在风控未开启或阈值偏高时会放大 DB 压力。

## Goals

- 在 `prod` 或 `app.strict-config=true` 下**fail-fast**：禁止 Snowflake 使用默认 `workerId/datacenterId=1/1`，强制显式配置，降低水平扩容踩坑概率。
- 为 Edge `/r/**` 增加“短码格式快速拒绝 + 短码不存在负缓存（短 TTL）”，降低缓存穿透导致的 MySQL 回源放大风险。

## Non-Goals

- 不改变 Edge 风控的默认开关策略（仍由配置控制）。
- 不引入分布式锁/单飞（singleflight）等更重的防击穿机制（后续可按压测结果再补）。


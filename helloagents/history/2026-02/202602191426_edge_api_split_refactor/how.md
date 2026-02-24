# Technical Design: edge_api_split_refactor

## Technical Solution

### Core Technologies
- 后端：Java 21、Spring Boot 3.x、Spring Web、Spring Security、Spring Data JPA、Spring Data Redis、Actuator
- 数据库：MySQL 8.x（核心业务数据）
- 缓存与统计：Redis 7.x（短链解析缓存、PV/UV 去重结构、flush 辅助索引）
- 构建：Maven（多模块/多应用构建）
- 部署：Docker / Docker Compose（开发与单机部署基线）
- 测试：JUnit5、Spring Boot Test、MockMvc、Testcontainers（MySQL/Redis）
- 前端：Vue 3 + Vite + TypeScript + Pinia；Nginx 反向代理

### Implementation Key Points
- **服务拆分**：将 `/r/**` 与 `/api/v1/**` 拆分为两个独立 Spring Boot 应用（可独立扩容、独立发布、独立观测）。
- **共享模块 SSOT**：抽取 `contracts/platform` 类库（错误码、响应体、RequestId、基础安全类型、配置约定与 key 约定），避免两服务产生漂移。
- **分层治理**：Controller 只做协议适配与入参校验；所有业务编排下沉到 Service/UseCase；用架构测试（ArchUnit）固化。
- **去 Web 化**：核心用例不依赖 `HttpServletRequest`/`MultipartFile` 等 Servlet 类型；由 adapter 层提取为值对象/流接口。
- **多租户护栏**：统一 tenant 注入与校验；禁止绕过 tenant guard 的数据访问；以测试锁死规则。
- **统计链路升级**：从 Redis 全量 SCAN 迁移到“可控索引 + 增量 flush”，并补齐幂等/可观测/降级策略。
- **生产化配置治理**：配置校验与启动期 fail-fast（生产环境）；安全关键项禁止默认值误用；节点 ID 可配置。
- **前端会话加固**：提供 HttpOnly Cookie 会话模式（可按环境开关），统一 401 处理与登出逻辑。

## Architecture Design

```mermaid
flowchart TD
    U[Public Browser] -->|GET /r/{code}| G[Ingress / Nginx]
    A[Admin UI (Vue)] -->|/api/v1/*| G

    G -->|/r/*| EDGE[Redirect Edge Service]
    G -->|/api/*| API[API Service]

    EDGE -->|cache-aside read/write| R[(Redis)]
    EDGE -->|fallback read| DB[(MySQL)]
    EDGE -->|PV/UV write| R

    API --> DB
    API --> R
    API -->|flush job (scheduled)| DB

    %% Optional future
    R -. event stream (optional) .-> MQ[(Kafka/MQ)]
    MQ -. OLAP .-> OLAP[(ClickHouse/ES)]
```

### Runtime Responsibility Split

1) **Redirect Edge Service**
- 仅暴露 `/r/**`，不承载管理类 API 与鉴权逻辑
- 目标：最短链路（解析+校验+统计轻量写入+302/301），优先保障可用性与低延迟
- 依赖：Redis（缓存/统计写入）、MySQL（只读回源）

2) **API Service**
- 暴露 `/api/v1/**`：登录注册、RBAC、短链 CRUD、标签、用户管理、API Key、统计查询
- 承载 Analytics Flush Job（定时落库），避免把重 IO 作业放入 Edge
- 依赖：MySQL/Redis；必要时可独立扩容

## Architecture Decision ADR

### ADR-001: Split Redirect Edge Service From API Service
**Context:**
- `/r/**` 具备高 QPS、对尾延迟与稳定性更敏感的特征；与管理 API 的鉴权/CRUD/导入导出/统计查询在资源消耗模式上不同。
- 现有单体在架构治理（分层一致性、去 Web 化、多租户护栏）上需要较大重构，拆分能天然形成边界并降低耦合。

**Decision:**
- 引入两套可部署后端应用：
  - Redirect Edge Service：仅处理 redirect 与采集写入
  - API Service：处理所有管理 API、OpenAPI、统计查询与 flush
- 提取共享类库作为 SSOT：错误码/响应体/RequestId/配置 key 约定/安全基础类型。

**Rationale:**
- 隔离高频链路与管理链路，便于独立扩容与独立发布，降低互相影响。
- 让 Edge 服务“更纯粹”：避免引入过多业务模块与安全复杂度，降低变更风险。
- 共享类库保证协议与 key 约定一致，减少重复实现与漂移。

**Alternatives:**
- 保持单体但优化（拒绝原因：无法从根上隔离资源争用，架构治理边界仍然松散）
- 全面迁移到 Clean/Hexagonal（拒绝原因：文件移动与回归成本更高，且仍然需要解决 redirect 与 API 的资源隔离问题）
- 进一步服务化（例如引入 MQ/OLAP）（拒绝原因：MVP 阶段运维复杂度显著增加，先把边界与治理打牢）

**Impact:**
- Maven 构建结构变为多模块/多应用；Docker 构建与 compose 需要调整
- 需要明确 Redis key、统计语义与缓存策略的跨服务一致性（由 shared 模块约束）
- 需要完善端到端与回归测试，确保路由/鉴权/多租户不回退

## API Design

### External Routing (no path change)
- Redirect：`GET /r/{code}` → Redirect Edge Service
- Admin/OpenAPI：`/api/v1/**` → API Service

### Optional: Admin Session Hardening (cookie mode)
为降低管理后台 Token 暴露风险，可引入“HttpOnly Cookie 会话模式”（默认保持 Bearer 兼容）：
- `POST /api/v1/auth/login`
  - **Response body:** 仍返回 `token`（兼容现有客户端）
  - **Set-Cookie:** `lf_token=<jwt>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=...`（按环境开关）
- 前端调用 `fetch` 开启 `credentials: "include"`，并在 401 时统一登出/跳转登录

## Data Model

### MySQL
- 保持核心表与字段兼容（`short_links`、`users`、`api_keys`、`link_stats_daily` 等）
- 如后续需要扩容统计，可再引入明细表/事件表与异步链路（不作为本次必须项）

### Redis Key Governance
目标：避免 flush 依赖全量 SCAN，改为“被触达 key 索引 + 增量 flush”。

建议约定（由 shared 模块统一生成）：
- PV：`stats:pv:{tenantId}:{linkId}:{yyyyMMdd}`（INCR）
- UV：`stats:uv:{tenantId}:{linkId}:{yyyyMMdd}`（HLL）
- 活跃索引集合（新增）：`stats:active:{yyyyMMdd}`（SET）
  - Edge 写入：每次 recordVisit 时 `SADD stats:active:{day} "{tenantId}:{linkId}"`
  - API flush：按天 `SMEMBERS`/`SSCAN` 活跃集合得到待刷列表，再读取 PV/UV 并 upsert
  - 过期策略：active set TTL 与 PV/UV TTL 对齐（防止泄漏）

## Security and Performance

- **Security**
  - 统一异常响应：401/403/400/404/409/410 映射一致且包含 requestId
  - 认证上下文缺失必须返回 401（禁止 500 漏洞路径）
  - CORS 策略收敛：允许来源白名单；启用 cookie 模式时必须 `allowCredentials=true` 且禁用 `*`
  - OpenAPI Key 与 JWT 的链路隔离：OpenAPI 仅适用于 `/api/v1/open/**`
  - 多租户：Service 层强制 tenantId 约束 + 回归测试覆盖 + 架构测试禁止绕过

- **Performance**
  - Redirect Edge：最小依赖、短路径、缓存优先、回源只读、缓存/统计写入失败可降级
  - Analytics flush：不使用全量 SCAN；批处理读取与写入；必要时引入 Redis pipeline
  - 减少 N+1：用户/角色等管理接口在 Service 层聚合批量查询

## Testing and Deployment

- **Testing**
  - 架构测试：controller-repository 禁止依赖、模块边界、tenant guard 覆盖
  - 单元测试：VisitInfo 指纹、Redis key 生成、配置校验
  - 集成测试：Testcontainers + MockMvc 覆盖注册/登录/短链 CRUD/redirect/统计/多租户隔离/OpenAPI
  - 双服务端到端：docker-compose 启动后验证 Nginx 路由 `/api` 与 `/r` 分别命中不同服务

- **Deployment**
  - docker-compose：新增 `server-api` 与 `server-edge` 两服务，分别暴露端口（例如 8080/8081）
  - web/nginx：按路径反代到不同服务
  - 迁移策略：先双跑（Edge 影子部署）→ 灰度切流 `/r/**` → 完全切换 → 清理 API 服务中的 redirect 路由


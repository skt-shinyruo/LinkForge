# Change Proposal: edge_api_split_refactor

## Requirement Background

当前后端 `server/`（单个 Spring Boot 应用）同时承载：
- 管理后台与 OpenAPI：`/api/v1/**`（JWT / API Key + RBAC + CRUD + 导入导出 + 统计查询）
- 公网跳转链路：`/r/{code}`（高频、低延迟、对稳定性/可观测性更敏感）

在 MVP 阶段这种“单体同进程承载”能快速交付，但现状已经暴露出可维护性与生产化风险：
1. **分层边界不一致**：有的模块走 `Controller -> Service -> Repository`，但也存在 Controller 直接注入 Repository/Encoder/IdGenerator 并承担业务编排，导致规则散落、复用困难与测试成本上升。
2. **业务层被 Servlet/Web 类型污染**：核心用例依赖 `HttpServletRequest`/`MultipartFile` 等 Web 类型，导致逻辑无法脱离 HTTP 场景复用（批处理/消息消费/单元测试）。
3. **认证与异常处理职责分散**：认证上下文缺失可能走到 500（而非 401/403），Filter/EntryPoint/ControllerAdvice 各自“手写响应”，一致性与可观测性难以长期保证。
4. **多租户隔离缺少系统级护栏**：当前更多依赖“开发者自觉传 tenantId + Repository 命名约定”，缺少对越权读取的强约束与架构测试。
5. **统计/缓存/配置/前端会话的生产化债务**：统计落库存在扫描式作业与可扩展性隐患；缓存异常吞噬导致不可观测；安全关键配置缺少 fail-fast/治理；前端 JWT 存储策略偏弱且 401 处理不统一。

为了解决上述 5 个问题，并提升高 QPS redirect 链路的稳定性/扩展性，需要将后端拆分为 **Redirect Edge Service** 与 **API Service** 两个可独立部署的服务，同时在拆分过程中完成架构治理与安全/可测试性加固。

## Change Content

1. 拆分后端为两个可部署服务：
   1) **Redirect Edge Service**：仅负责 `/r/**`，聚焦性能、稳定性、可观测性；依赖 Redis/MySQL（只读回源）与轻量统计写入。
   2) **API Service**：负责 `/api/v1/**`，包含 IAM、短链管理、OpenAPI、统计查询与统计落库（flush job）。
2. 抽取共享模块（contracts/platform）作为跨服务 SSOT：
   - 统一 `ApiResponse`、错误码 `ErrorCode`、异常 `BusinessException`
   - 统一 `RequestId` 生成与日志 MDC 规范
   - 统一安全基础类型（`AuthPrincipal`、roles 等）与鉴权约定
3. 统一分层边界并增加“架构护栏”：
   - 禁止 Controller 直接依赖 Repository（强制通过 Service/UseCase）
   - 用 ArchUnit（或等价方案）固化依赖方向与包边界，防止回退
4. 业务用例去 Servlet 化（清理 Web 类型污染）：
   - Redirect/Analytics：用稳定的 `VisitInfo/ClientInfo` 值对象替代 `HttpServletRequest`
   - CSV 导入导出：Service 接收 `InputStream/OutputStream`（或 Reader/Writer），Controller 仅做协议适配
5. 多租户隔离加固：
   - 统一从认证主体注入 tenantId，并提供可复用的 tenant guard（读写校验）
   - 增加自动化回归：跨租户访问必须失败、禁止未带 tenantId 的仓储访问（结构化测试）
6. 生产化与可观测性加固：
   - 统计落库从“SCAN 全量 key”迁移为“可控索引/增量 flush”
   - 缓存写入失败可观测（日志/指标），不再静默吞异常
   - 关键配置引入校验与启动期 fail-fast（生产环境避免 dev 默认值误用）
   - Snowflake 节点参数可配置，避免多实例冲突
   - 前端会话：提供更安全的 HttpOnly Cookie 模式（保留 Authorization Bearer 兼容），并统一 401 处理策略

## Impact Scope

- **Modules:**
  - `server/`：从单应用重构为多模块/多应用（API + Redirect Edge + shared libs）
  - `deploy/`：docker-compose 拆分服务、环境变量与端口调整
  - `web/`：Nginx 反代目标拆分；前端鉴权与 401 处理策略调整
  - `.helloagents/wiki/*`：架构与模块文档同步更新（SSOT）
- **Files:**
  - Maven 结构：`server/pom.xml` 及新增模块/应用目录
  - 安全/异常/多租户：`server/**/platform/**`、`server/**/security/**`、`server/**/api/**`
  - Redirect/Analytics：`redirect/**`、`analytics/**`
  - IAM/ShortLink：`iam/**`、`shortlink/**`
  - 前端：`web/src/services/http.ts`、`web/src/stores/auth.ts`、`web/src/router/index.ts`
  - 部署：`deploy/docker-compose.yml`、`web/nginx.conf`
- **APIs:**
  - 逻辑路径不变：`/api/v1/**` 与 `/r/**` 仍保留（通过反向代理/网关路由到不同服务）
  - 可选增强：登录接口支持 Set-Cookie（HttpOnly）以提升前端安全性
- **Data:**
  - MySQL：核心表结构保持兼容（必要时补充索引/约束）
  - Redis：统计与缓存 key 约定可能增加“索引 key/活跃集合”以避免全量扫描

## Core Scenarios

### Requirement: svc-split
**Module:** server / deploy / web
将后端拆分为 Redirect Edge Service 与 API Service，并保持现有对外路径稳定。

#### Scenario: split-routing
- 外部访问 `/r/{code}` 仅进入 Redirect Edge Service
- 管理后台与 OpenAPI 调用 `/api/v1/**` 仅进入 API Service
- 反向代理/网关可根据路径转发，支持灰度与回滚

### Requirement: layering-boundary
**Module:** server
统一并固化后端分层边界（Web 层只做协议适配，业务逻辑聚合在 Service/UseCase）。

#### Scenario: controller-no-repo
- Controller 不允许注入 Repository/Encoder/IdGenerator 等基础设施对象
- Controller 只依赖 Service/UseCase 与 DTO/Mapper
- 架构测试在 CI 中强制执行，违规即失败

### Requirement: web-decouple
**Module:** server
核心业务用例不依赖 Servlet/Web 类型，以便复用与测试。

#### Scenario: redirect-analytics-with-visitinfo
- Redirect 用例接收 `VisitInfo`（ip/ua/forwardedFor/requestId 等）而不是 `HttpServletRequest`
- Analytics 采集同样只依赖 `VisitInfo`，并可单元测试验证指纹/TTL/key 生成

#### Scenario: csv-io-abstraction
- CSV 导入导出 Service 使用 `InputStream/OutputStream`（或 Reader/Writer）
- Controller 处理 Multipart/Response header，仅做 I/O 适配

### Requirement: auth-error-unify
**Module:** server
统一认证上下文、异常到 HTTP 的映射与响应结构，确保 401/403/500 语义正确且一致。

#### Scenario: auth-missing-is-401
- 缺少认证主体时不返回 500
- 所有认证失败/无权限都返回统一 `ApiResponse` 结构（含 requestId）

### Requirement: tenant-guard
**Module:** server
多租户隔离引入系统级护栏与回归测试，降低“忘带 tenantId”的人为风险。

#### Scenario: tenant-isolation-regression
- 集成测试覆盖：租户 A 资源对租户 B 不可见
- 架构测试覆盖：禁止绕过 tenant guard 的数据访问路径

### Requirement: prod-hardening
**Module:** server / web / deploy
对统计、缓存、配置与前端会话进行生产化加固。

#### Scenario: analytics-flush-without-scan
- 统计落库不再依赖 Redis 全量 SCAN
- flush 具备幂等、可观测、可降级能力

#### Scenario: safer-admin-session
- 管理后台提供 HttpOnly Cookie 会话模式（保留 Bearer 兼容）
- 前端统一处理 401（自动登出/跳转登录/提示）

## Risk Assessment

- **Risk:** 服务拆分引入构建与部署复杂度、路由配置失误风险
  - **Mitigation:** 分阶段迁移（先双跑再切流）、保留回滚开关、在 docker-compose 与 CI 中加入端到端验证
- **Risk:** 认证与 Cookie 模式变更可能引入跨域/CORS/CSRF 问题
  - **Mitigation:** 默认仍支持 Bearer；Cookie 模式按环境开关；CORS 政策收敛到白名单并加集成测试
- **Risk:** 多租户护栏可能导致历史查询行为变化
  - **Mitigation:** 先引入架构测试与服务层 guard，再逐步收敛 Repository API；用回归测试锁定行为
- **Risk:** 统计链路调整可能影响报表一致性
  - **Mitigation:** 保持 Redis key 兼容并做“影子写入/对账”阶段；提供可回滚到旧 flush 的开关

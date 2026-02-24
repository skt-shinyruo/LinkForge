# Task List: edge_api_split_refactor

Directory: `helloagents/plan/202602191426_edge_api_split_refactor/`

---

## 0. 基线与切分策略（先可跑，再治理）
- [√] 0.1 明确端口与路由策略（dev compose：API=8080、EDGE=8081；web Nginx 按 `/api` 与 `/r` 转发），verify why.md#requirement-svc-split-split-routing
- [√] 0.2 定义“迁移阶段开关/回滚策略”（例如 Nginx 一键切回单体），verify why.md#requirement-svc-split-split-routing

## 1. Maven 多模块与工程结构重构（服务拆分基础）
- [√] 1.1 将 `server/` 重构为 Maven 多模块 parent（聚合构建），新增模块骨架（shared/platform、api-app、edge-app），verify why.md#requirement-svc-split-split-routing
- [√] 1.2 抽取 shared 模块：`ApiResponse/ErrorCode/BusinessException/RequestId/AppProperties` 等 SSOT，API/EDGE 两应用依赖 shared，verify why.md#requirement-svc-split-split-routing
- [√] 1.3 调整 Dockerfile：分别构建 API 与 EDGE 的 jar 镜像（或同一 Dockerfile 多目标），verify why.md#requirement-svc-split-split-routing
- [√] 1.4 调整 `deploy/docker-compose.yml`：新增 `server-api`、`server-edge`，并更新 `web` 依赖，verify why.md#requirement-svc-split-split-routing
- [√] 1.5 更新 `web/nginx.conf`：`/api/` 代理到 `server-api`，`/r/` 代理到 `server-edge`，verify why.md#requirement-svc-split-split-routing

## 2. Redirect Edge Service（/r 高性能链路）
- [√] 2.1 创建 Edge 启动入口（`@SpringBootApplication`）与最小化依赖（仅 Web/Redis/JDBC 或最小 JPA），verify why.md#requirement-svc-split-split-routing
- [√] 2.2 迁移 `RedirectController` 与 redirect 用例到 Edge（仅保留 `/r/**`），verify why.md#requirement-svc-split-split-routing
- [√] 2.3 Redirect 用例去 Servlet 化：引入 `VisitInfo`（或等价值对象）由 Controller 提取，Service 不再依赖 `HttpServletRequest`，verify why.md#requirement-web-decouple-redirect-analytics-with-visitinfo
- [√] 2.4 缓存治理：缓存写入失败可观测（日志/指标）且不影响主链路；缓存 key 生成集中化，verify why.md#requirement-prod-hardening-analytics-flush-without-scan
- [√] 2.5 Edge 统计采集：PV/UV 写 Redis，并维护活跃索引集合（`stats:active:{day}`），verify why.md#requirement-prod-hardening-analytics-flush-without-scan
- [√] 2.6 Edge 可观测性：requestId 透传、关键日志字段（tenantId/linkId/code/status/latency），verify why.md#requirement-prod-hardening-analytics-flush-without-scan

## 3. API Service（/api/v1 管理与 OpenAPI）
- [√] 3.1 创建 API 启动入口（`@SpringBootApplication`）并接管现有 `/api/v1/**` Controllers，verify why.md#requirement-svc-split-split-routing
- [√] 3.2 迁移/保留 IAM、ShortLink、Stats、OpenAPI 等模块到 API 服务，验证路由与安全策略不变，verify why.md#requirement-svc-split-split-routing
- [√] 3.3 统计落库 Flush Job 迁移到 API 服务（Edge 禁用），并改造为“活跃索引驱动”而非全量 SCAN，verify why.md#requirement-prod-hardening-analytics-flush-without-scan

## 4. 分层边界治理（解决 Controller 直连 Repo）
- [√] 4.1 IAM 管理接口重构：为 `/api/v1/users`、`/api/v1/api-keys` 引入 Service/UseCase，Controller 仅做协议适配，verify why.md#requirement-layering-boundary-controller-no-repo
- [√] 4.2 修复 N+1 风险：用户列表接口批量聚合角色（Repository 增加批量查询方法或定制查询），verify why.md#requirement-layering-boundary-controller-no-repo
- [√] 4.3 统一鉴权策略落点：决定“Controller 注解 or Service 注解”为主，并对全项目收敛，verify why.md#requirement-layering-boundary-controller-no-repo

## 5. 业务用例去 Web 化（解决 Servlet 污染）
- [√] 5.1 Analytics：将 `recordVisit(tenantId, linkId, HttpServletRequest)` 重构为 `recordVisit(tenantId, linkId, VisitInfo)`（或等价），并补齐单元测试，verify why.md#requirement-web-decouple-redirect-analytics-with-visitinfo
- [√] 5.2 ShortLink CSV：将 `importCsv(..., MultipartFile)` 改为 `importCsv(..., InputStream/Reader, meta)`；Controller 负责 Multipart 适配，verify why.md#requirement-web-decouple-csv-io-abstraction
- [√] 5.3 ShortLink CSV：将 `exportCsv(..., OutputStream)` 进一步抽象为 Writer/Stream 接口并补齐测试，verify why.md#requirement-web-decouple-csv-io-abstraction

## 6. 认证与异常一致性（解决 500 漏洞路径与响应漂移）
- [√] 6.1 `AuthContext.requirePrincipal()` 缺失认证主体时返回 401（统一走 `BusinessException(ErrorCode.UNAUTHORIZED)` 或等价机制），verify why.md#requirement-auth-error-unify-auth-missing-is-401
- [√] 6.2 收敛 Filter/EntryPoint/Advice 的错误响应生成：抽取统一错误响应 writer（保证 `ApiResponse + requestId` 一致），verify why.md#requirement-auth-error-unify-auth-missing-is-401
- [√] 6.3 为 JWT/API Key 认证链路补齐集成测试：无 token、无 key、无权限、非法 token/key 均返回预期 401/403，verify why.md#requirement-auth-error-unify-auth-missing-is-401

## 7. 多租户隔离护栏（系统级约束 + 回归）
- [√] 7.1 统一 tenant 注入方式（例如从 `AuthPrincipal`/TenantContext 获取），并在 Service 层做 tenant guard（读写校验），verify why.md#requirement-tenant-guard-tenant-isolation-regression
- [√] 7.2 引入架构测试（ArchUnit）：禁止 Controller 依赖 Repository；禁止绕过 tenant guard 的访问路径（按约定包边界），verify why.md#requirement-tenant-guard-tenant-isolation-regression
- [√] 7.3 完善集成测试：租户 A/B 数据隔离回归（CRUD、统计、OpenAPI），verify why.md#requirement-tenant-guard-tenant-isolation-regression

## 8. 统计/缓存/配置生产化加固
- [√] 8.1 Analytics：实现活跃索引集合与 TTL 对齐策略；flush 作业批量化、可降级，verify why.md#requirement-prod-hardening-analytics-flush-without-scan
- [√] 8.2 Cache：缓存写失败打点（日志/指标），并增加可观测字段（key、tenantId、code），verify why.md#requirement-prod-hardening-analytics-flush-without-scan
- [√] 8.3 配置：对安全关键项做校验与启动期 fail-fast（区分 local/test 与 prod），verify why.md#requirement-prod-hardening-analytics-flush-without-scan
- [√] 8.4 Snowflake：workerId/datacenterId 配置化，文档化多实例部署约束，verify why.md#requirement-prod-hardening-analytics-flush-without-scan
- [√] 8.5 CORS：收敛到白名单；启用 cookie 模式时允许 credentials 并禁用 `*`，verify why.md#requirement-prod-hardening-safer-admin-session

## 9. 前端会话与 401 统一处理
- [√] 9.1 前端 `apiFetch` 增加 401 统一处理策略（登出/跳转/提示），verify why.md#requirement-prod-hardening-safer-admin-session
- [√] 9.2 引入（可选）cookie 模式：前端 fetch 使用 `credentials: \"include\"`；后端登录可 Set-Cookie（保留 Bearer 兼容），verify why.md#requirement-prod-hardening-safer-admin-session
- [√] 9.3 更新前端存储策略：默认不再长期把 JWT 明文落 localStorage（按环境/开关），verify why.md#requirement-prod-hardening-safer-admin-session

## 10. Documentation Update（SSOT 同步）
- [√] 10.1 更新 `helloagents/wiki/arch.md`：补充双服务架构图、依赖与部署拓扑
- [√] 10.2 更新 `helloagents/project.md`：补充多模块构建与运行方式、配置约定（workerId、cookie 模式等）
- [√] 10.3 更新 `helloagents/wiki/modules/*`：拆分出 `redirect-edge`、`api-service` 模块说明与变更记录

## 11. Security Check
- [√] 11.1 执行安全检查（G9）：鉴权链路、权限控制、敏感信息、CORS/cookie、安全配置默认值、越权访问回归

## 12. Testing
- [√] 12.1 扩展现有端到端集成测试：验证 Nginx 路由后 `/r/**` 命中 Edge，`/api/**` 命中 API（可用 docker-compose smoke test）
- [√] 12.2 新增架构测试（ArchUnit）：controller->repo 禁止依赖、分层边界、租户护栏约束
- [√] 12.3 新增关键单元测试：VisitInfo 指纹、Redis key 生成与 TTL、配置校验（local/test/prod 行为）


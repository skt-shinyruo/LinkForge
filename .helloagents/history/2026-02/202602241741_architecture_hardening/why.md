# Change Proposal: architecture_hardening

## Requirement Background

LinkForge 当前采用「API Service + Redirect Edge Service」的双服务形态（见 `.helloagents/wiki/arch.md`），整体方向正确，但在上线前仍存在一组“架构假设未落地 / 安全边界不清晰 / 可扩展性风险”的问题。

本次加固目标是在**不推翻现有架构**的前提下，补齐关键护栏，为后续迭代提供可控基础。

## Problem List（对齐代码实现）

1) **登录身份假设与数据库约束不一致（P0）**  
   - 现状：登录仅按 email 定位用户，但 DB 只约束 `(tenant_id, email)`；一旦出现跨租户重复 email，可能导致登录命中不确定甚至越权。  
   - 证据：
     - `server/api-app/src/main/java/com/linkforge/iam/service/AuthService.java`：`findFirstByEmail(email)`（无 tenant 条件）
     - `server/api-app/src/main/resources/db/migration/V1__init.sql`：`uk_users_tenant_email (tenant_id, email)`

2) **Edge 服务最小权限不足（P1）**  
   - 现状：Edge 仅需回源读取 `short_links`，但部署上与 API 共用 DB 账号（默认具备写权限）。  
   - 证据：`deploy/docker-compose.yml` 默认同一 `DB_USERNAME/DB_PASSWORD`。

3) **Cookie 模式下 CSRF 策略缺失（P1）**  
   - 现状：支持 JWT HttpOnly Cookie，但全局关闭 CSRF；一旦启用 cookie 模式，写接口可能被 CSRF 攻击。  
   - 证据：`server/api-app/src/main/java/com/linkforge/platform/security/SecurityConfig.java`：`csrf.disable()`。

4) **统计落库在规模增长下的性能与成本风险（P1/P2）**  
   - 现状：设计为 Edge best-effort 写 Redis，API 定时 flush 落库；但 flush 作业存在 Redis RTT 放大点。  
   - 证据：`AnalyticsFlushJob` 对 UV 逐条 `PFCOUNT`。

5) **跨模块契约存在隐式耦合（P2）**  
   - 现状：`stats:*` key 约定通过包内可见类维护，API/Edge 因“同包名”跨模块访问，属于隐式耦合。  
   - 证据：`server/shared/src/main/java/com/linkforge/analytics/service/AnalyticsKeys.java`（原 package-private）。

6) **重复的启动期配置校验逻辑，长期易漂移（P2）**  
   - 证据：
     - `server/api-app/src/main/java/com/linkforge/api/ApiStartupValidator.java`
     - `server/edge-app/src/main/java/com/linkforge/edge/EdgeStartupValidator.java`

7) **OpenAPI API Key 认证路径存在 DB 写热点（P2）**  
   - 现状：每次鉴权成功都更新 `last_used_at`。  
   - 风险：高 QPS 下写放大与热点表。

8) **工程卫生：构建产物入库（P2）**  
   - 现状：Maven `target/` 目录下的 class/报告文件被纳入版本管理，造成噪音与误用风险。

## Decisions

- D001：采用 **路径 A（email 全局唯一）**，使登录语义与 DB 约束一致。  
- D003：cookie 模式进入生产支持范围，但必须同时启用 **CSRF（或等价策略）**。  
- 数据清理：允许破坏性清理重复 email（跨租户重复 email 的用户可全部删除，不保留）。

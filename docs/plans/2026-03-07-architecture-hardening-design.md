# Architecture Hardening Design (DDD Modular Monolith)
**
Date:** 2026-03-07

## Goal

在保持“模块化单体（Maven multi-module）”整体形态的前提下，解决以下架构问题：

1. **BC 边界被表级耦合绕开**
   - `redirect` 直接读 `short_links` 表（跨 BC 读表，表结构泄漏）。
   - `shortlink` 删除短链时直接 `DELETE` analytics 表（跨 BC 写表）。
2. **DDD 四层落点不一致**
   - `interfaces` 中混入核心策略/决策与业务规则；Controller 过重。
   - `analytics` 缺少 infrastructure 层（SQL/Redis/JDBC 混在 application）。
3. **架构守护规则覆盖不足**
   - 仅靠 `..repo..` 包名规则无法发现 JDBC/SQL 这种真实耦合。
4. **组合根过于隐式**
   - 全量 `@ComponentScan` 导致任意 `@Component` 都会“自动入图”，边界不清晰。
5. **共享内核膨胀风险**
   - `AppProperties` 过大（所有模块依赖同一巨型配置对象）。
   - `ErrorCode` 单枚举聚合所有 BC 错误，且 handler 映射越来越重。
6. **安全链过滤器顺序耦合**
   - OpenAPI Key 与 JWT 的“互为兜底”语义依赖 filter 插入顺序，后续扩展脆弱。

## Non-goals

- 不做微服务拆分。
- 不引入 Spring Modulith 等新框架（治理优先用现有 ArchUnit + 约束）。
- 不改变数据库表结构作为前置条件（允许后续迭代，但本轮优先在代码层收敛耦合）。

## Context

- 后端模块清单：`server/pom.xml`（foundation/contracts/*/accounts/shortlink/redirect/analytics/app）。
- 可执行入口：`server/app/.../LinkForgeApplication`，当前全量扫描 `com.linkforge`。
- redirect 当前回源查询：`redirect.infrastructure.persistence.ShortLinkLookupRepository`（JdbcTemplate）。
- shortlink 删除跨表清理：`shortlink.application.ShortLinkService.delete()` 里直接 `DELETE` analytics 表。
- analytics Top 链接报表：`analytics.application.AnalyticsQueryService` 通过 `JOIN short_links` 获取 code/originalUrl。

## Key Decisions

### D1. Stats/Events 长期保留

当短链被“最终删除”（物理删除 `short_links` 行）时：

- `link_stats_daily` / `link_stats_dim_daily` / `link_visit_events` **长期保留**，不随短链删除清理。
- 报表层允许出现“统计存在但 LinkMeta 不存在”的情况。

### D2. redirect 解耦方式选择：Port（方案 A）

高 QPS redirect 主链路采用“Port 解耦、共享同库同表”的方式（改动小、收益大）：

- `redirect` 不直接访问 `short_links` 表。
- `redirect` 通过 `contracts` 定义的 Port 查询 `LinkMeta`，由 `shortlink` 提供实现。

### D3. Top Links 报表行为

Top 链接报表中：

- 即使短链已删除（无法再获取 `code/originalUrl`），仍应显示统计行。
- UI 对缺失的 meta 标注“已删除”，并禁用跳转/复制等依赖 code 的能力。

## Architecture (After)

### 1) Cross-BC communication via Ports

新增 `contract-redirect` 中的 Port（示例名）：

- `LinkMetaQueryPort`
  - `Optional<LinkMeta> findActiveByCode(String code)`：给 redirect 用（必须过滤 `archived_at is null`）。
  - `Optional<LinkMeta> findById(long tenantId, long linkId)`：给 analytics 富化用（允许返回归档但未删除的 meta；删除则 empty）。

实现落地：

- `shortlink` 提供 adapter 实现 `LinkMetaQueryPort`，封装对 `short_links` 的查询与字段映射。
- `redirect` 改为依赖 `LinkMetaQueryPort`，删除 `ShortLinkLookupRepository`。
- `analytics` Top links SQL 只查统计表，使用 `LinkMetaQueryPort.findById(...)` 做 best-effort 富化。

### 2) Layering conventions (DDD-ish but pragmatic)

约定并强制执行：

- `interfaces`：HTTP/Web 适配层（Controller/Filter/DTO/response writer）
- `application`：用例编排（服务、job、应用层策略装配）
- `domain`：纯规则/值对象/策略（不依赖 Spring/Servlet/JDBC）
- `infrastructure`：外部资源适配（DB/Redis/脚本/outbox/dao/repo）

本轮重点收敛：

- redirect 风控策略从 `interfaces` 迁移到 `application/domain/infrastructure` 合适位置。
- redirect controller 中的跳转 URL 拼装与 query-forward 规则抽取到 application/domain。
- analytics SQL 查询从 application 抽到 infrastructure repository。

### 3) Explicit composition root

收敛 Spring 组件装配边界：

- `LinkForgeApplication` 仅扫描 `com.linkforge.app`，通过显式 `@Import` 引入各模块 `*Module` 配置类。
- 每个 BC 提供一个 `@Configuration` 模块入口（`ComponentScan` + properties enablement）。

### 4) Shared kernel slimming

#### 4.1 Split `AppProperties` into focused property classes

将巨型 `AppProperties` 拆成多个小 `@ConfigurationProperties`，保持外部配置 key 前缀不变（仍为 `app.*`），但内部依赖从“全量”变成“按需”。

#### 4.2 Split `ErrorCode` into per-BC enums + interface

将单个 `ErrorCode` 拆为多个 enum，并统一实现 `AppErrorCode` 接口：

- `CommonErrorCode`
- `AccountsErrorCode`
- `ShortLinkErrorCode`
- `OpenApiErrorCode`

`BusinessException` 持有 `AppErrorCode`，并在 code 中自带 `httpStatus`（int），避免 `GlobalExceptionHandler` 维护一大坨映射。

### 5) Auth filter order removal

用一个组合认证 filter 统一处理 JWT + OpenAPI Key 的 fallback 逻辑，避免多个 filter 的先后顺序成为隐含前提。

## Enforcement (ArchUnit)

在 `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java` 强化规则：

- redirect BC 禁止依赖 JDBC/SQL：不得依赖 `JdbcTemplate`/`DataSource` 等包。
- application/domain 禁止依赖 servlet/web 包。
- controller 类必须 reside in `..interfaces..`。

## Migration Notes

- 由于允许破坏性调整：
  - Top links API 返回的 `code/originalUrl` 允许为 `null`，新增 `deleted` 字段用于前端标注。
  - 错误码类型拆分后，后端 Java 枚举名变更，但数值 code 保持原值以减少客户端震荡。
- DB 不做 schema 变更（本轮）。

## Testing Strategy

- 单元测试：新增/调整 Port adapter 行为测试；风控策略迁移后的测试（已有 `RedirectClientIpResolverTest` 等）。
- 集成测试：
  - `redirect` 负缓存测试与跳转体验测试应继续通过（`server/app/src/test/...Redirect*IntegrationTest`）。
  - `mvn test` 全量跑一遍作为回归门槛。


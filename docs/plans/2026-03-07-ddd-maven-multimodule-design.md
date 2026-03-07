# LinkForge：彻底 DDD（Modular Monolith）+ Maven 多模块改造设计

日期：2026-03-07  
范围：`server/`（Java Spring Boot 后端）

## 0. 背景与目标

当前后端是一个 **单体 Spring Boot**（API + Redirect Edge 同进程），代码组织以形态为主（`api/`、`edge/`、`redirect/` 等），并夹杂少量“平台/基础设施”包（`config/`、`web/`、`tx/`、`id/`）。

本设计目标是做一次“彻底 DDD”式改造，但保持部署形态仍为 **单体（一个可执行 jar）**：

1) 以 **业务边界（Bounded Context）** 组织代码，而不是以接口形态（API/Edge）组织  
2) 用 **Maven 多模块** 明确依赖方向，约束跨域耦合  
3) 明确 **Shared Kernel / Foundation / Published Language（Contracts）** 的边界，避免“Common 大杂烩”  
4) 业务行为与对外接口（HTTP path、响应结构、DB schema）尽量保持不变（重构优先）

## 1. 关键原则（“彻底 DDD”的判定标准）

### 1.1 Bounded Context 优先

- 代码的第一维分组按 BC：`accounts`、`shortlink`、`redirect`、`analytics`
- `api/edge` 不是业务边界，只是 **interfaces 层** 的一种展现方式

### 1.2 Contracts（Published Language）替代“直接依赖实现”

跨 BC 的共享必须通过 contracts（DTO/接口/事件契约）完成，禁止：

- `shortlink` 直接依赖 `redirect` 的缓存实现类
- `redirect` 直接依赖 `analytics` 的具体实现类

### 1.3 Shared Kernel 极小且“纯净”

Shared Kernel 是“共享领域内核”，不是工具包：

- **禁止** Spring / Jakarta / slf4j / DB / Redis 依赖
- 仅允许少量 **跨 BC 的领域原语 / 值对象 / 通用约束**（可选，逐步引入）

### 1.4 Foundation 是“技术底座”，不含业务决策

Foundation 放的是：

- Spring Boot 装配基础（属性绑定、启动校验、事务工具、requestId 等）
- 通用技术能力（ID 生成、序列化、通用过滤器/拦截器等）

Foundation **不得**反向依赖任何业务模块（避免循环依赖）。

## 2. Bounded Context（业务域）切分

基于现有代码与概念（Tenant/User/Role/API Key/JWT、多租户护栏等），建议 BC 如下：

1) `accounts`（原 `api/iam`）：租户、用户、角色、API Key、注册登录、鉴权相关用例  
2) `shortlink`（原 `api/shortlink`）：短链写模型（创建/编辑/归档/删除/标签/导入导出/outbox）  
3) `redirect`（原 `edge/redirect` + edge 风控 + 目前 `redirect/service` 缓存实现）：短码解析、负缓存、跳转控制、风控  
4) `analytics`（原 `api/analytics` + `edge/analytics` + `analytics/AnalyticsKeys`）：访问记录、维度归一化、事件流与聚合/清理/查询

命名说明：不使用 `iam`（偏云厂商术语），改为 `accounts`（贴近业务通用语言，能自然容纳 tenant+user+role+api-key）。

## 3. Maven 多模块拆分（目录与 artifactId）

> 目标：`server/` 变为 aggregator parent（`packaging=pom`），最终可执行 jar 由 `server/app` 产出，jar 名保持 `linkforge-server-<version>.jar` 以减少部署改动。

建议模块清单：

### 3.1 共享模块（方案 B）

1) `server/shared-kernel`（`artifactId=linkforge-shared-kernel`）
   - 内容：跨 BC 的领域原语（可选逐步引入，如 `TenantId`）、通用约束
   - 依赖：JDK-only

2) `server/foundation`（`artifactId=linkforge-foundation`）
   - 内容：`AppProperties`、`StartupValidation`、`AfterCommit`、requestId、基础配置/校验、ID 生成等
   - 依赖：Spring（允许），但不依赖业务模块

### 3.2 Published Language（Contracts）

3) `server/contracts/api`（`artifactId=linkforge-contract-api`）
   - 内容：`ApiResponse`、`BusinessException`、`ErrorCode`（尽量保持纯 DTO/异常/枚举）

4) `server/contracts/redirect`（`artifactId=linkforge-contract-redirect`）
   - 内容：`LinkMeta`（跨域读模型契约）+ `LinkCachePort`（缓存端口接口）
   - 目的：`shortlink` 写缓存、`redirect` 读缓存时不互相依赖实现

5) `server/contracts/analytics`（`artifactId=linkforge-contract-analytics`）
   - 内容：`AnalyticsKeys`、`VisitInfo`（或更名 `VisitContext`）、`VisitRecorderPort`（可选）

### 3.3 业务模块（BC）

6) `server/accounts`（`artifactId=linkforge-accounts`）
7) `server/shortlink`（`artifactId=linkforge-shortlink`）
8) `server/redirect`（`artifactId=linkforge-redirect`）
9) `server/analytics`（`artifactId=linkforge-analytics`）

### 3.4 可执行应用（装配/启动）

10) `server/app`（`artifactId=linkforge-server`，packaging=jar）
   - 内容：`LinkForgeApplication`、Spring Security 过滤器链装配、全局异常处理装配
   - 资源：`application*.yml`、`db/migration/*` 放在此模块
   - 测试：现有 `@SpringBootTest` 集成测试先集中到此模块，保证全链路可跑

## 4. 包结构规范（每个 BC 内部 DDD 四层）

每个 BC 模块包名固定为：

- `com.linkforge.<bc>.interfaces.*`（Controller/Filter/Web DTO）
- `com.linkforge.<bc>.application.*`（用例编排、事务边界、job）
- `com.linkforge.<bc>.domain.*`（领域模型、值对象、领域服务、领域事件）
- `com.linkforge.<bc>.infrastructure.*`（JPA entity/repo、Redis/JDBC、外部系统适配）

说明：本次重构允许“先把代码搬到正确的 BC 模块 + 正确的四层目录”，领域模型是否丰富化可分阶段完成；但 **依赖方向必须一次到位**。

## 5. 关键跨域耦合的 DDD 处理方式（必须做）

### 5.1 shortlink ↔ redirect：缓存读模型

现状：

- `ShortLinkService` / `LinkCacheOutboxJob` 直接依赖 `com.linkforge.redirect.service.LinkCacheService/LinkMeta`
- `RedirectService` 也依赖同一实现

目标：

- `LinkMeta` 移入 `linkforge-contract-redirect`
- 提供端口接口 `LinkCachePort`（例如 `lookup/put/evict/markNotFound/tryPut/tryEvict`）
- `redirect` 模块提供 Redis 实现（adapter），并由 `app` 模块装配注入
- `shortlink` 只依赖 contract + port，不依赖 redirect 的实现包

### 5.2 redirect ↔ analytics：访问记录

现状：

- `RedirectService` 直接调用 `edge.analytics.service.AnalyticsService`

目标（推荐）：

- `contract-analytics` 定义 `VisitRecorderPort`（或更小的 `recordVisit` 接口）
- `analytics` 模块实现该 port
- `redirect` 依赖 contract-analytics，不依赖 analytics 实现

## 6. API/Edge 的“形态隔离”如何在 DDD 后保留

现状：

- API 错误处理：`GlobalExceptionHandler` 仅作用于 `com.linkforge.api`
- Edge 错误处理：`EdgeGlobalExceptionHandler` 仅作用于 `com.linkforge.edge`
- Security 仅匹配 `/api/**`，避免影响 `/r/**`

目标：

- API controllers 迁入各 BC 的 `interfaces` 下，但仍保持“API advice 仅覆盖 API controllers”
- Redirect controllers/filters 迁入 `redirect.interfaces`，Edge advice 仅覆盖 redirect controllers
- Security chain 继续 `securityMatcher("/api/**")`，不影响 `/r/**`

实现策略：

- 将 API advice 的 `basePackages` 改为：
  - `com.linkforge.accounts.interfaces`
  - `com.linkforge.shortlink.interfaces`
  - `com.linkforge.analytics.interfaces`
- 将 redirect advice 的 `basePackages` 改为：
  - `com.linkforge.redirect.interfaces`

## 7. 构建与测试策略

1) `server/pom.xml`：parent + aggregator（`packaging=pom`），统一依赖管理与插件管理  
2) `server/app`：唯一可执行 jar，`spring-boot-maven-plugin` 配置在此  
3) 集成测试：先把 `server/src/test/java` 全部迁入 `server/app/src/test/java`，保持 `mvn test` 仍然跑全量（包含 Testcontainers）  
4) 单元测试：后续按 BC 归位到各模块

## 8. 迁移步骤（高层）

1) 建立 Maven 多模块骨架（不动业务代码）  
2) 提取 `foundation` 与 `contract-*`（把“共享/契约”边界立起来）  
3) 提取 4 个业务 BC 模块，并把代码按 BC + 四层移动  
4) 用 contracts/ports 解除跨 BC 的实现依赖（缓存、统计记录等）  
5) 修复与补齐 ArchUnit 规则（模块依赖 + 分层约束）  
6) 更新 Dockerfile / 部署脚本，使其构建 `server/app` 产物  
7) `cd server && mvn test` 全绿作为验收门槛

## 9. 风险与缓解

- **风险：大规模移动导致编译/测试不稳定**  
  缓解：按“先骨架、再迁移、再解耦”的顺序，每一步都要求能 `mvn test`。

- **风险：contracts 污染（放入 Spring 组件）**  
  缓解：contracts 强制只含 DTO/接口/异常/枚举；Spring 装配移入 `foundation` 或 `app`。

- **风险：shared-kernel 变成 common**  
  缓解：shared-kernel 先保持极小（甚至为空），只在确实出现跨 BC 的领域概念时再引入。


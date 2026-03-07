# LinkForge DDD Modular Monolith + Maven 多模块 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 把 `server/` 从单模块 jar 一步到位改造成“按业务边界（DDD/BC）拆分”的 Maven 多模块，并保持一个可执行 jar（`linkforge-server`）。

**Architecture:** Modular Monolith。按 BC 拆分业务模块（accounts/shortlink/redirect/analytics），用 contracts 作为 Published Language 解耦跨域依赖；共享层拆为 `shared-kernel`（极小、纯净）+ `foundation`（技术底座），由 `app` 模块完成装配启动。

**Tech Stack:** Java 17, Spring Boot 3.2.x, Maven, JPA, Redis, Flyway, Testcontainers, ArchUnit

---

## Before You Start（硬约束）

- 所有路径以仓库根为基准（例如 `server/pom.xml`）。
- 每完成一个大步骤都要跑一次：`cd server && mvn test`，以测试为验收门槛。
- 本环境 `.git/` 在 sandbox 内只读：若需要 `git mv`/`git worktree` 使用已批准前缀；本计划 **不要求 commit**（除非用户明确要求）。

## Task 1: 建立 Maven 多模块骨架（不移动业务代码）

**Files:**
- Modify: `server/pom.xml`
- Create: `server/app/pom.xml`
- Create: `server/shared-kernel/pom.xml`
- Create: `server/foundation/pom.xml`
- Create: `server/contracts/api/pom.xml`
- Create: `server/contracts/redirect/pom.xml`
- Create: `server/contracts/analytics/pom.xml`
- Create: `server/accounts/pom.xml`
- Create: `server/shortlink/pom.xml`
- Create: `server/redirect/pom.xml`
- Create: `server/analytics/pom.xml`

**Step 1: 备份当前 server/pom 关键信息**

- 记录现有 properties（spring boot 版本、testcontainers、shedlock、archunit 等）
- 记录现有 dependencies 列表（web/security/data/jdbc/redis/flyway/mysql/jjwt 等）

**Step 2: 把 `server/pom.xml` 改为 parent+aggregator（packaging=pom）**

- `artifactId` 改为 `linkforge-server-parent`（保留 groupId/version）
- `packaging` 改为 `pom`
- `modules` 列出上述 10 个模块路径
- 把依赖版本放在 parent 的 `<dependencyManagement>` 或 `<properties>`
- 把 surefire 的 `docker.api.version` 系统变量配置保留在 parent（建议移入 `<pluginManagement>`）

**Step 3: 创建 `server/app` 模块（可执行 jar）**

- `artifactId=linkforge-server`（保证 jar 名不变）
- 仅 `app` 模块配置 `spring-boot-maven-plugin`
- `app` 依赖：各业务模块 + contracts + foundation（shared-kernel 通过传递依赖可选）

**Step 4: 每个子模块先建“空壳” pom（可编译）**

- 暂时不放代码，只声明 parent 与必要依赖
- 先保证 `cd server && mvn -q -DskipTests package` 能过

**Step 5: 验证**

Run: `cd server && mvn -q -DskipTests package`  
Expected: BUILD SUCCESS（即使还没迁代码）

## Task 2: 把运行入口与 resources 迁到 app 模块

**Files:**
- Move: `server/src/main/java/com/linkforge/LinkForgeApplication.java` → `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Move: `server/src/main/resources/**` → `server/app/src/main/resources/**`
- Move: `server/src/test/resources/application.properties` → `server/app/src/test/resources/application.properties`
- Move: `server/src/test/java/**` → `server/app/src/test/java/**`（先整体迁入，后续再拆回各模块）
- Modify: `server/Dockerfile`

**Step 1: 迁移 Java 入口类到 app**

- 保持 package 不变（`com.linkforge`），减少扫描配置变更

**Step 2: 迁移 resources**

- `application*.yml`、`db/migration/*` 必须在最终可执行 jar 的 classpath 上（放 app）

**Step 3: 迁移测试**

- 现有集成测试大量依赖 `@SpringBootTest(LinkForgeApplication.class)`：先全部迁到 app，确保全链路还跑

**Step 4: 更新 Dockerfile（build app 模块）**

- 构建阶段：COPY 多模块结构（至少 `pom.xml` + 各子模块 pom + src）
- RUN：`mvn -q -DskipTests -pl app -am package`（或在根执行 package 并指定最终 jar）
- 运行阶段：COPY app 模块产物 jar

**Step 5: 验证**

Run: `cd server && mvn test`  
Expected: 测试全绿（此时业务代码可能仍在旧目录，需要在下一任务继续迁移/编译修复）

## Task 3: 抽取 contracts（Published Language），让跨域依赖“先走契约”

**Files:**
- Move: `server/src/main/java/com/linkforge/contract/api/ApiResponse.java` → `server/contracts/api/src/main/java/com/linkforge/contract/api/ApiResponse.java`
- Move: `server/src/main/java/com/linkforge/contract/api/BusinessException.java` → `server/contracts/api/src/main/java/com/linkforge/contract/api/BusinessException.java`
- Move: `server/src/main/java/com/linkforge/contract/api/ErrorCode.java` → `server/contracts/api/src/main/java/com/linkforge/contract/api/ErrorCode.java`
- Move: `server/src/main/java/com/linkforge/contract/api/GlobalExceptionHandler.java` → `server/app/src/main/java/...`（或 `foundation`，但不留在 contract）
- Move: `server/src/main/java/com/linkforge/contract/api/ApiErrorResponseWriter.java` → `server/app/src/main/java/...`

**Step 1: 让 `linkforge-contract-api` 只保留“纯契约”**

- contract 模块尽量不含 Spring `@Component/@RestControllerAdvice`

**Step 2: app 模块接管 API 错误处理装配**

- `GlobalExceptionHandler` 的 `basePackages` 在后续重命名后要更新

**Step 3: 验证**

Run: `cd server && mvn test`  
Expected: 全绿

## Task 4: 抽取 foundation（技术底座）

**Files:**
- Move: `server/src/main/java/com/linkforge/platform/config/**` → `server/foundation/src/main/java/com/linkforge/foundation/config/**`
- Move: `server/src/main/java/com/linkforge/platform/web/**` → `server/foundation/src/main/java/com/linkforge/foundation/web/**`
- Move: `server/src/main/java/com/linkforge/platform/tx/**` → `server/foundation/src/main/java/com/linkforge/foundation/tx/**`
- Move: `server/src/main/java/com/linkforge/platform/id/**` → `server/foundation/src/main/java/com/linkforge/foundation/id/**`
- Move: `server/src/main/java/com/linkforge/api/scheduling/**` → `server/foundation/src/main/java/com/linkforge/foundation/scheduling/**`（或 app）

**Step 1: 先移动代码，再统一 package 命名**

- 先确保编译通过，再做批量包名替换

**Step 2: 把“跨 BC 都用到的”保留在 foundation**

- `AppProperties`、`StartupValidation`、`AfterCommit`、requestId、ID generator 等

**Step 3: 验证**

Run: `cd server && mvn test`  
Expected: 全绿

## Task 5: 按 BC 抽取业务模块（accounts/shortlink/redirect/analytics）

**Files (examples):**
- Move: `server/src/main/java/com/linkforge/api/iam/**` → `server/accounts/src/main/java/com/linkforge/accounts/**`
- Move: `server/src/main/java/com/linkforge/api/shortlink/**` → `server/shortlink/src/main/java/com/linkforge/shortlink/**`
- Move: `server/src/main/java/com/linkforge/edge/**`（redirect/风控/net/web/error）→ `server/redirect/src/main/java/com/linkforge/redirect/**`
- Move: `server/src/main/java/com/linkforge/api/analytics/**` + `server/src/main/java/com/linkforge/edge/analytics/**` + `server/src/main/java/com/linkforge/analytics/**` → `server/analytics/src/main/java/com/linkforge/analytics/**`

**Step 1: 先做“目录迁移 + package 迁移”，不做领域建模重写**

- 优先做到：代码属于正确 BC 模块、依赖方向正确、测试绿

**Step 2: 每个 BC 内按 DDD 四层整理（interfaces/application/domain/infrastructure）**

- 第一轮允许“只是换包路径”，第二轮再做领域模型下沉

**Step 3: 更新 API/Edge advice 与 security matcher**

- API advice 覆盖各 BC 的 API controllers
- redirect advice 覆盖 redirect controllers
- security chain 继续只匹配 `/api/**`

**Step 4: 验证**

Run: `cd server && mvn test`  
Expected: 全绿

## Task 6: 解除跨 BC 的实现耦合（最关键的 DDD 收益点）

**Files:**
- Create: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMeta.java`
- Create: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkCachePort.java`
- Refactor: shortlink/application 中写缓存逻辑只依赖 `LinkCachePort`
- Refactor: redirect 中提供 `LinkCachePort` 的 Redis adapter 实现

（可选同理做 analytics `VisitRecorderPort`）

**Step 1: 先把类型搬到 contract，再让双方依赖 contract**

**Step 2: 再把实现放回 redirect 的 infrastructure**

**Step 3: 验证**

Run: `cd server && mvn test`  
Expected: 全绿

## Task 7: 收尾：更新 ArchUnit 与 README/Docker

**Files:**
- Modify: `server/src/test/java/**`（ArchUnit 规则迁移到 `server/app/src/test/java/**` 或单独模块）
- Modify: `README.md`（若构建命令或路径改变）
- Modify: `server/Dockerfile`

**Step 1: ArchUnit 规则从“包名约束”升级为“模块依赖约束”**

- contracts 不依赖业务模块
- foundation 不依赖业务模块
- BC 之间只能通过 contracts 依赖

**Step 2: 最终验证**

Run: `cd server && mvn test`  
Expected: BUILD SUCCESS（54+ tests，0 failures）


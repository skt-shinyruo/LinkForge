# 深度 DDD（方案 3）Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status update (2026-03-16):** 本计划已落地，并且按“不要保留 Legacy”要求完成最终实现：仓库中不存在 `LegacyShortLinkService` / `LinkMetaQueryPort` / `LinkCacheOutbox*`。ShortLink 写侧通过 `shortlink-application` 的 `ShortLinkApplicationService` + `command/query` handlers 执行用例，在同一事务内通过 `ShortLinkEventPublisher` 追加写入 `integration_events`；Redirect/Analytics 通过各自 projector job 消费事件并维护 projection（DB + Redis side effects）。  
> 关键入口：`server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`、`server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java`、`server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java`。  
> 验证：`cd server && mvn -Pit -pl integration-tests -am test` 已在 2026-03-16 跑到 `BUILD SUCCESS`。

**Goal:** 将 `shortlink / redirect / analytics` 从“Transaction Script + 同步跨域查询/缓存耦合”升级为“BC 内部四层 Maven 子模块 + Integration Events + Projection”的编译期强边界架构，并完成切流与旧耦合清理。

**Architecture:** 每个 BC 拆成 `domain/application/infrastructure/interfaces` 四个 Maven 子模块；ShortLink 写侧在同一事务内把变更追加写入 `integration_events`；Redirect/Analytics 作为消费者轮询事件并维护自己的 Projection（DB + Redis），从而移除 `LinkMetaQueryPort` 等同步依赖。

**Tech Stack:** Java 17, Spring Boot 3.2.x, Maven reactor, MyBatis, MySQL 8, Redis 7, Flyway, ShedLock, Micrometer, Testcontainers

**Spec for reference:** `docs/plans/2026-03-15-deep-ddd-shortlink-redirect-analytics-multimodule-design.md`

---

## File/Module Map (lock-in)

目标 Maven 结构（只列本计划涉及的 3 个 BC + 新 contracts）：

- `server/contracts/shortlink` → `linkforge-contract-shortlink`
- `server/shortlink` (packaging=pom)
  - `server/shortlink/domain` → `linkforge-shortlink-domain`
  - `server/shortlink/application` → `linkforge-shortlink-application`
  - `server/shortlink/infrastructure` → `linkforge-shortlink-infrastructure`
  - `server/shortlink/interfaces` → `linkforge-shortlink-interfaces`
- `server/redirect` (packaging=pom)
  - `server/redirect/domain` → `linkforge-redirect-domain`
  - `server/redirect/application` → `linkforge-redirect-application`
  - `server/redirect/infrastructure` → `linkforge-redirect-infrastructure`
  - `server/redirect/interfaces` → `linkforge-redirect-interfaces`
- `server/analytics` (packaging=pom)
  - `server/analytics/domain` → `linkforge-analytics-domain`
  - `server/analytics/application` → `linkforge-analytics-application`
  - `server/analytics/infrastructure` → `linkforge-analytics-infrastructure`
  - `server/analytics/interfaces` → `linkforge-analytics-interfaces`
- `server/app`（composition root）
  - compile 依赖：`*-interfaces`
  - runtime 依赖：`*-infrastructure`

事件基础设施（技术底座，放在 `foundation`）：
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/*`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/mapper/*`
- Create: `server/foundation/src/main/resources/com/linkforge/foundation/eventing/mapper/*.xml`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/IntegrationEventMybatisConfig.java`

---

## Chunk 1: Maven 子模块骨架 + 代码搬迁（Phase 0）

### Task 1: 建工作区 + 跑基线（安全绳）

**Files:** none

- [x] **Step 1: Create a git worktree (Skipped in this workspace)**

Run: `git worktree add ../LinkForge_deep_ddd_20260315 -b deep-ddd-20260315`

- [x] **Step 2: Baseline unit tests (fast)**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl app test`

Expected: `BUILD SUCCESS`

- [x] **Step 3: Baseline integration smoke test**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=LinkForgeIntegrationTest test`

Expected: `BUILD SUCCESS`

### Task 3: 将 `server/shortlink` 拆成 4 子模块并搬迁代码（先不改行为）

**Files:**
- Modify: `server/shortlink/pom.xml`
- Modify: `server/pom.xml`
- Create: `server/shortlink/domain/pom.xml`
- Create: `server/shortlink/application/pom.xml`
- Create: `server/shortlink/infrastructure/pom.xml`
- Create: `server/shortlink/interfaces/pom.xml`
- Move: `server/shortlink/src/main/java/com/linkforge/shortlink/interfaces/**` -> `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/**`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java` (interface)
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java` (implementation; no legacy)
- Delete: `server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java` (legacy monolith implementation; replaced)
- Delete: `server/shortlink/src/main/java/com/linkforge/shortlink/application/UrlValidator.java` (replaced by domain `HttpUrl`)
- Move: `server/shortlink/src/main/java/com/linkforge/shortlink/application/query/**` -> `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/**`
- Move: `server/shortlink/src/main/java/com/linkforge/shortlink/application/job/**` -> `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/job/**`
- Move: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/**` -> `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/**`
- Move: `server/shortlink/src/main/resources/**` -> `server/shortlink/infrastructure/src/main/resources/**`
- Move: `server/shortlink/src/test/java/**` -> `server/shortlink/infrastructure/src/test/java/**`
- Modify: `server/app/pom.xml`

- [x] **Step 1: Convert `server/shortlink` to packaging=pom and declare submodules**

Modify `server/shortlink/pom.xml`:
- Set `<packaging>pom</packaging>`
- Add `<modules>` listing: `domain`, `application`, `infrastructure`, `interfaces`
- Remove old dependencies (they will move to submodule poms)

- [x] **Step 2: Create submodule poms**

Create `server/shortlink/domain/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-shortlink</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-shortlink-domain</artifactId>
  <name>linkforge-shortlink-domain</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-shared-kernel</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/shortlink/infrastructure/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-shortlink</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-shortlink-infrastructure</artifactId>
  <name>linkforge-shortlink-infrastructure</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-shortlink-application</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-foundation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-redirect</artifactId>
    </dependency>

    <dependency>
      <groupId>org.mybatis.spring.boot</groupId>
      <artifactId>mybatis-spring-boot-starter</artifactId>
      <version>${mybatis-spring-boot.version}</version>
    </dependency>

    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-csv</artifactId>
      <version>${commons-csv.version}</version>
    </dependency>
    <dependency>
      <groupId>net.javacrumbs.shedlock</groupId>
      <artifactId>shedlock-spring</artifactId>
      <version>${shedlock.version}</version>
    </dependency>
    <dependency>
      <groupId>io.micrometer</groupId>
      <artifactId>micrometer-core</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/shortlink/application/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-shortlink</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-shortlink-application</artifactId>
  <name>linkforge-shortlink-application</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-shortlink-domain</artifactId>
    </dependency>

    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-foundation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-api</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/shortlink/interfaces/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-shortlink</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-shortlink-interfaces</artifactId>
  <name>linkforge-shortlink-interfaces</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-shortlink-application</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [x] **Step 3: Update `server/pom.xml` dependencyManagement for new artifacts**

Modify `server/pom.xml` `dependencyManagement` to add entries (version = `${project.version}`):
- `linkforge-shortlink-domain`
- `linkforge-shortlink-application`
- `linkforge-shortlink-infrastructure`
- `linkforge-shortlink-interfaces`

- [x] **Step 4: Move code/resources/tests and keep dependency direction**

Do the directory moves listed under **Files** for this task, plus these “boundary fixes” so `application` does NOT depend on `infrastructure`:
- Create `com.linkforge.shortlink.application.ShortLinkService` as an **interface** (copy method signatures + nested records `CreateLinkRequest/UpdateLinkRequest/LinkDto/TagDto/ImportResult` from the current class).
- 不保留 legacy 实现：在 `shortlink-application` 提供 `com.linkforge.shortlink.application.ShortLinkApplicationService` 实现 `ShortLinkService`，并把原“巨型应用服务”拆分为 `command/*` 与 `query/*` handlers。
- Move `com.linkforge.shortlink.application.job.*` into `com.linkforge.shortlink.infrastructure.job.*` (these jobs depend on persistence/outbox/Redis ports) and update package statements/imports accordingly.
- Move the existing shortlink unit tests into `shortlink-infrastructure` and update them to instantiate/reference the implementation class (not the interface).

- [x] **Step 5: Fix package-private accesses and resource paths**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl shortlink/domain,shortlink/infrastructure,shortlink/application,shortlink/interfaces -DskipTests compile`

Expected: `BUILD SUCCESS`

- [x] **Step 6: Wire `server/app` dependencies (compile interfaces + runtime infrastructure)**

Modify `server/app/pom.xml`:
- Replace dependency `linkforge-shortlink` with:
  - compile: `linkforge-shortlink-interfaces`
  - runtime: `linkforge-shortlink-infrastructure`

- [x] **Step 7: Run moved shortlink tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl shortlink/infrastructure test`

Expected: `BUILD SUCCESS`

- [x] **Step 8: App smoke test**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl app test`

Expected: `BUILD SUCCESS`

- **Optional: Step 9: Commit**

```bash
git add server/shortlink server/app/pom.xml server/pom.xml
git commit -m "refactor(shortlink): split into domain/application/infrastructure/interfaces modules"
```

---

### Task 4: 将 `server/redirect` 拆成 4 子模块并搬迁代码（先不改行为）

**Files:**
- Modify: `server/redirect/pom.xml`
- Modify: `server/pom.xml`
- Create: `server/redirect/domain/pom.xml`
- Create: `server/redirect/application/pom.xml`
- Create: `server/redirect/infrastructure/pom.xml`
- Create: `server/redirect/interfaces/pom.xml`
- Move: `server/redirect/src/main/java/com/linkforge/redirect/domain/**` -> `server/redirect/domain/src/main/java/com/linkforge/redirect/domain/**`
- Move: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/**` -> `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/**`
- Move: `server/redirect/src/main/java/com/linkforge/redirect/application/**` -> `server/redirect/application/src/main/java/com/linkforge/redirect/application/**`
- Move: `server/redirect/src/main/java/com/linkforge/redirect/infrastructure/**` -> `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/**`
- Move: `server/redirect/src/test/java/com/linkforge/redirect/application/**` -> `server/redirect/application/src/test/java/com/linkforge/redirect/application/**`
- Move: `server/redirect/src/test/java/com/linkforge/redirect/interfaces/**` -> `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/**`
- Modify: `server/app/pom.xml`

- [x] **Step 1: Convert `server/redirect` to packaging=pom and declare submodules**

Modify `server/redirect/pom.xml`:
- Set `<packaging>pom</packaging>`
- Add `<modules>` listing: `domain`, `application`, `infrastructure`, `interfaces`
- Remove old dependencies (they move into submodule poms)

- [x] **Step 2: Create submodule poms**

Create `server/redirect/domain/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-redirect</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-redirect-domain</artifactId>
  <name>linkforge-redirect-domain</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-shared-kernel</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/redirect/application/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-redirect</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-redirect-application</artifactId>
  <name>linkforge-redirect-application</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-redirect-domain</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-foundation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-redirect</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-analytics</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/redirect/infrastructure/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-redirect</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-redirect-infrastructure</artifactId>
  <name>linkforge-redirect-infrastructure</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-redirect-application</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-foundation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-redirect</artifactId>
    </dependency>

    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/redirect/interfaces/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-redirect</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-redirect-interfaces</artifactId>
  <name>linkforge-redirect-interfaces</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-redirect-application</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [x] **Step 3: Update `server/pom.xml` dependencyManagement for new artifacts**

Modify `server/pom.xml` `dependencyManagement` to add entries (version = `${project.version}`):
- `linkforge-redirect-domain`
- `linkforge-redirect-application`
- `linkforge-redirect-infrastructure`
- `linkforge-redirect-interfaces`

- [x] **Step 4: Move code/tests into the correct submodule folders**

Do the directory moves listed under **Files** for this task.

- [x] **Step 5: Compile redirect submodules**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl redirect/domain,redirect/application,redirect/infrastructure,redirect/interfaces -DskipTests compile`

Expected: `BUILD SUCCESS`

- [x] **Step 6: Wire `server/app` dependencies**

Modify `server/app/pom.xml`:
- Replace dependency `linkforge-redirect` with:
  - compile: `linkforge-redirect-interfaces`
  - runtime: `linkforge-redirect-infrastructure`

- [x] **Step 7: Run moved redirect tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl redirect/domain,redirect/application,redirect/interfaces test`

Expected: `BUILD SUCCESS`

- [x] **Step 8: App smoke test**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl app test`

Expected: `BUILD SUCCESS`

- **Optional: Step 9: Commit**

```bash
git add server/redirect server/app/pom.xml server/pom.xml
git commit -m "refactor(redirect): split into domain/application/infrastructure/interfaces modules"
```

---

### Task 5: 将 `server/analytics` 拆成 4 子模块并搬迁代码（先不改行为）

**Files:**
- Modify: `server/analytics/pom.xml`
- Modify: `server/pom.xml`
- Create: `server/analytics/domain/pom.xml`
- Create: `server/analytics/application/pom.xml`
- Create: `server/analytics/infrastructure/pom.xml`
- Create: `server/analytics/interfaces/pom.xml`
- Move: `server/analytics/src/main/java/com/linkforge/analytics/interfaces/**` -> `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/**`
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java` (interface)
- Move: `server/analytics/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java` -> `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/AnalyticsQueryServiceImpl.java` (implementation)
- Move: `server/analytics/src/main/java/com/linkforge/analytics/application/VisitRecorderService.java` -> `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java`
- Move: `server/analytics/src/main/java/com/linkforge/analytics/application/job/**` -> `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/**`
- Move: `server/analytics/src/main/java/com/linkforge/analytics/application/VisitDimensionNormalizer.java` -> `server/analytics/application/src/main/java/com/linkforge/analytics/application/VisitDimensionNormalizer.java`
- Move: `server/analytics/src/main/java/com/linkforge/analytics/application/VisitorFingerprint.java` -> `server/analytics/application/src/main/java/com/linkforge/analytics/application/VisitorFingerprint.java`
- Move: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/**` -> `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/**`
- Move: `server/analytics/src/main/resources/**` -> `server/analytics/infrastructure/src/main/resources/**`
- Move: `server/analytics/src/test/java/com/linkforge/analytics/application/VisitDimensionNormalizerTest.java` -> `server/analytics/application/src/test/java/com/linkforge/analytics/application/VisitDimensionNormalizerTest.java`
- Move: `server/analytics/src/test/java/com/linkforge/analytics/application/VisitorFingerprintTest.java` -> `server/analytics/application/src/test/java/com/linkforge/analytics/application/VisitorFingerprintTest.java`
- Move: `server/analytics/src/test/java/com/linkforge/analytics/application/VisitRecorderServiceTest.java` -> `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/VisitRecorderServiceTest.java`
- Move: `server/analytics/src/test/java/com/linkforge/analytics/application/job/AnalyticsFlushJobTest.java` -> `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJobTest.java`
- Move: `server/analytics/src/test/java/com/linkforge/analytics/application/job/AnalyticsEventIngestJobPoisonIsolationTest.java` -> `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobPoisonIsolationTest.java`
- Modify: `server/app/pom.xml`

- [x] **Step 1: Convert `server/analytics` to packaging=pom and declare submodules**

Modify `server/analytics/pom.xml`:
- Set `<packaging>pom</packaging>`
- Add `<modules>` listing: `domain`, `application`, `infrastructure`, `interfaces`
- Remove old dependencies (they move into submodule poms)

- [x] **Step 2: Create submodule poms**

Create `server/analytics/domain/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-analytics</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-analytics-domain</artifactId>
  <name>linkforge-analytics-domain</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-shared-kernel</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/analytics/infrastructure/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-analytics</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-analytics-infrastructure</artifactId>
  <name>linkforge-analytics-infrastructure</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-analytics-application</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-foundation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-redirect</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-analytics</artifactId>
    </dependency>

    <dependency>
      <groupId>org.mybatis.spring.boot</groupId>
      <artifactId>mybatis-spring-boot-starter</artifactId>
      <version>${mybatis-spring-boot.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <dependency>
      <groupId>net.javacrumbs.shedlock</groupId>
      <artifactId>shedlock-spring</artifactId>
      <version>${shedlock.version}</version>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/analytics/application/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-analytics</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-analytics-application</artifactId>
  <name>linkforge-analytics-application</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-analytics-domain</artifactId>
    </dependency>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-contract-analytics</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Create `server/analytics/interfaces/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-analytics</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>linkforge-analytics-interfaces</artifactId>
  <name>linkforge-analytics-interfaces</name>
  <dependencies>
    <dependency>
      <groupId>com.linkforge</groupId>
      <artifactId>linkforge-analytics-application</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [x] **Step 3: Update `server/pom.xml` dependencyManagement for new artifacts**

Modify `server/pom.xml` `dependencyManagement` to add entries (version = `${project.version}`):
- `linkforge-analytics-domain`
- `linkforge-analytics-application`
- `linkforge-analytics-infrastructure`
- `linkforge-analytics-interfaces`

- [x] **Step 4: Move code/resources/tests and keep dependency direction**

Do the directory moves listed under **Files** for this task, plus these “boundary fixes” so `application` does NOT depend on `infrastructure`:
- Create `com.linkforge.analytics.application.AnalyticsQueryService` as an **interface** (copy method signatures + nested records/enums from the current class).
- Move the current query implementation into `analytics-infrastructure` as `com.linkforge.analytics.infrastructure.query.AnalyticsQueryServiceImpl` and make it `implements com.linkforge.analytics.application.AnalyticsQueryService` (keep Spring annotations on the implementation class).
- Move `com.linkforge.analytics.application.VisitRecorderService` into `analytics-infrastructure` (it depends on Redis) and keep it implementing `VisitRecorderPort`.
- Move `com.linkforge.analytics.application.job.*` into `com.linkforge.analytics.infrastructure.job.*` (these jobs depend on Redis/MyBatis) and update package statements/imports accordingly.
- Move `VisitRecorderServiceTest` + `com.linkforge.analytics.application.job.*` tests into `analytics-infrastructure` tests (the other utility tests can stay in `analytics-application`), and update test package statements/imports accordingly.

- [x] **Step 5: Compile analytics submodules**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl analytics/domain,analytics/infrastructure,analytics/application,analytics/interfaces -DskipTests compile`

Expected: `BUILD SUCCESS`

- [x] **Step 6: Wire `server/app` dependencies**

Modify `server/app/pom.xml`:
- Replace dependency `linkforge-analytics` with:
  - compile: `linkforge-analytics-interfaces`
  - runtime: `linkforge-analytics-infrastructure`

- [x] **Step 7: Run moved analytics tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl analytics/application,analytics/infrastructure test`

Expected: `BUILD SUCCESS`

- [x] **Step 8: App smoke test**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl app test`

Expected: `BUILD SUCCESS`

- **Optional: Step 9: Commit**

```bash
git add server/analytics server/app/pom.xml server/pom.xml
git commit -m "refactor(analytics): split into domain/application/infrastructure/interfaces modules"
```

---

### Task 6: 验收 Chunk 1（build + integration smoke）

**Files:** none

- [x] **Step 1: Full compile**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -DskipTests compile`

Expected: `BUILD SUCCESS`

- [x] **Step 2: Run moved module tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl shortlink/infrastructure,redirect/domain,redirect/application,redirect/interfaces,analytics/application,analytics/infrastructure test`

Expected: `BUILD SUCCESS`

- [x] **Step 3: App tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl app test`

Expected: `BUILD SUCCESS`

- [x] **Step 4: Integration smoke**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=LinkForgeIntegrationTest test`

Expected: `BUILD SUCCESS`

- **Optional: Step 5: Commit “phase boundary” tag commit message**

```bash
git commit --allow-empty -m "chore: phase 0 complete (module skeleton green)"
```

---

## Chunk 2: Contracts + Integration Events 基础设施 + 写侧追加事件（Phase 1；Backfill 已取消）

### Task 2: 引入 `contracts/shortlink`（Published Language）

**Files:**
- Create: `server/contracts/shortlink/pom.xml`
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkEventTypes.java`
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkPublicSnapshot.java`
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkCreatedV1.java`
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkUpdatedV1.java`
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkArchivedV1.java`
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkRestoredV1.java`
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkDeletedV1.java`
- Modify: `server/pom.xml`

- [x] **Step 1: Create module `server/contracts/shortlink` pom**

Create `server/contracts/shortlink/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.linkforge</groupId>
    <artifactId>linkforge-server-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>linkforge-contract-shortlink</artifactId>
  <name>linkforge-contract-shortlink</name>
  <description>Published language: shortlink integration events + snapshots (JDK-only; no Spring/Jackson deps)</description>
</project>
```

- [x] **Step 2: Register module + dependencyManagement**

Modify `server/pom.xml`:
- Add module: `contracts/shortlink`
- Add dependencyManagement entry for `linkforge-contract-shortlink`

- [x] **Step 3: Add event type constants**

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkEventTypes.java`:

```java
package com.linkforge.contract.shortlink;

public final class ShortLinkEventTypes {
    private ShortLinkEventTypes() {}

    public static final String SHORT_LINK_CREATED_V1 = "shortlink.ShortLinkCreated.v1";
    public static final String SHORT_LINK_UPDATED_V1 = "shortlink.ShortLinkUpdated.v1";
    public static final String SHORT_LINK_ARCHIVED_V1 = "shortlink.ShortLinkArchived.v1";
    public static final String SHORT_LINK_RESTORED_V1 = "shortlink.ShortLinkRestored.v1";
    public static final String SHORT_LINK_DELETED_V1 = "shortlink.ShortLinkDeleted.v1";
}
```

- [x] **Step 4: Add snapshot contract**

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkPublicSnapshot.java`:

```java
package com.linkforge.contract.shortlink;

import java.time.Instant;
import java.util.List;

public record ShortLinkPublicSnapshot(
        long tenantId,
        long linkId,
        String code,
        String originalUrl,
        boolean enabled,
        Instant expiresAtUtc,
        Integer redirectStatusCode,
        boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        List<String> queryForwardAllowlist,
        Instant archivedAtUtc
) {
}
```

- [x] **Step 5: Add event records (V1)**

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkCreatedV1.java`:

```java
package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;

import java.time.Instant;

public record ShortLinkCreatedV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}
```

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkUpdatedV1.java`:

```java
package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;

import java.time.Instant;

public record ShortLinkUpdatedV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}
```

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkArchivedV1.java`:

```java
package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;

import java.time.Instant;

public record ShortLinkArchivedV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}
```

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkRestoredV1.java`:

```java
package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;

import java.time.Instant;

public record ShortLinkRestoredV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}
```

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/event/ShortLinkDeletedV1.java`:

```java
package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;

import java.time.Instant;

public record ShortLinkDeletedV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}
```

- [x] **Step 6: Verify contracts build**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl contracts/shortlink test`

Expected: `BUILD SUCCESS` (no tests is OK)

- **Optional: Step 7: Commit**

```bash
git add server/pom.xml server/contracts/shortlink
git commit -m "feat(contracts): add shortlink integration event contracts"
```

### Task 7: 在 `foundation` 落地 Integration Event Store（MyBatis）

**Files:**
- Modify: `server/foundation/pom.xml`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/IntegrationEventRow.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/IntegrationEventStore.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/IntegrationCheckpointRepository.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/IntegrationDeadLetterRepository.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/MybatisIntegrationEventStore.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/MybatisIntegrationCheckpointRepository.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/MybatisIntegrationDeadLetterRepository.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/IntegrationEventMybatisConfig.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/mapper/IntegrationEventMapper.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/mapper/IntegrationCheckpointMapper.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/eventing/mapper/IntegrationDeadLetterMapper.java`
- Create: `server/foundation/src/main/resources/com/linkforge/foundation/eventing/mapper/IntegrationEventMapper.xml`
- Create: `server/foundation/src/main/resources/com/linkforge/foundation/eventing/mapper/IntegrationCheckpointMapper.xml`
- Create: `server/foundation/src/main/resources/com/linkforge/foundation/eventing/mapper/IntegrationDeadLetterMapper.xml`

- [x] **Step 1: Add MyBatis dependency to foundation**

Modify `server/foundation/pom.xml` to add:
- `org.mybatis.spring.boot:mybatis-spring-boot-starter` with `<version>${mybatis-spring-boot.version}</version>`

```xml
<dependency>
  <groupId>org.mybatis.spring.boot</groupId>
  <artifactId>mybatis-spring-boot-starter</artifactId>
  <version>${mybatis-spring-boot.version}</version>
</dependency>
```

- [x] **Step 2: Add mapper scan config**

Create `IntegrationEventMybatisConfig`:

```java
package com.linkforge.foundation.eventing;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.linkforge.foundation.eventing.mapper", annotationClass = Mapper.class)
public class IntegrationEventMybatisConfig {}
```

- [x] **Step 3: Add store/repository APIs (ports)**

Note: these *interfaces* are injected into producer/consumer code by type. Concrete Spring beans are added in Step 5.

Create `IntegrationEventRow`:

```java
package com.linkforge.foundation.eventing;

import java.time.Instant;

public record IntegrationEventRow(
        long seq,
        String eventId,
        String producer,
        String eventType,
        Long tenantId,
        String aggregateType,
        Long aggregateId,
        Instant occurredAtUtc,
        String payloadJson
) {
}
```

Create `IntegrationEventStore` (a small wrapper over MyBatis mappers; no business logic):

```java
package com.linkforge.foundation.eventing;

import java.time.Instant;
import java.util.List;

public interface IntegrationEventStore {
    long loadMaxSeq();

    List<IntegrationEventRow> listAfterSeq(long lastSeqExclusive, int limit);

    void append(
            String eventId,
            String producer,
            String eventType,
            Long tenantId,
            String aggregateType,
            Long aggregateId,
            Instant occurredAtUtc,
            String payloadJson
    );
}
```

Checkpoint repo:

```java
package com.linkforge.foundation.eventing;

public interface IntegrationCheckpointRepository {
    long loadOrInit(String consumer);

    void update(String consumer, long lastSeq);
}
```

Dead letter repo:

```java
package com.linkforge.foundation.eventing;

public interface IntegrationDeadLetterRepository {
    void upsertFailure(String consumer, IntegrationEventRow event, int attempts, String lastError);
}
```

- [x] **Step 4: Create MyBatis mappers + XML**

Create `IntegrationEventMapper`:

```java
package com.linkforge.foundation.eventing.mapper;

import com.linkforge.foundation.eventing.IntegrationEventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface IntegrationEventMapper {

    long loadMaxSeq();

    List<IntegrationEventRow> listAfterSeq(
            @Param("lastSeqExclusive") long lastSeqExclusive,
            @Param("limit") int limit
    );

    int insert(
            @Param("eventId") String eventId,
            @Param("producer") String producer,
            @Param("eventType") String eventType,
            @Param("tenantId") Long tenantId,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") Long aggregateId,
            @Param("occurredAtUtc") Instant occurredAtUtc,
            @Param("payloadJson") String payloadJson
    );
}
```

Create `IntegrationCheckpointMapper`:

```java
package com.linkforge.foundation.eventing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IntegrationCheckpointMapper {

    Long findLastSeq(@Param("consumer") String consumer);

    int insert(@Param("consumer") String consumer, @Param("lastSeq") long lastSeq);

    int update(@Param("consumer") String consumer, @Param("lastSeq") long lastSeq);
}
```

Create `IntegrationDeadLetterMapper`:

```java
package com.linkforge.foundation.eventing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface IntegrationDeadLetterMapper {

    int upsertFailure(
            @Param("consumer") String consumer,
            @Param("seq") long seq,
            @Param("eventId") String eventId,
            @Param("producer") String producer,
            @Param("eventType") String eventType,
            @Param("occurredAtUtc") Instant occurredAtUtc,
            @Param("payloadJson") String payloadJson,
            @Param("attempts") int attempts,
            @Param("lastError") String lastError
    );
}
```

Create `IntegrationEventMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.linkforge.foundation.eventing.mapper.IntegrationEventMapper">

    <!-- Explicit constructor mapping: avoids relying on underscore-to-camel for record components like occurredAtUtc -->
    <resultMap id="IntegrationEventRowResultMap" type="com.linkforge.foundation.eventing.IntegrationEventRow">
        <constructor>
            <idArg column="seq" javaType="long"/>
            <arg column="event_id" javaType="java.lang.String"/>
            <arg column="producer" javaType="java.lang.String"/>
            <arg column="event_type" javaType="java.lang.String"/>
            <arg column="tenant_id" javaType="java.lang.Long"/>
            <arg column="aggregate_type" javaType="java.lang.String"/>
            <arg column="aggregate_id" javaType="java.lang.Long"/>
            <arg column="occurred_at" javaType="java.time.Instant"/>
            <arg column="payload_json" javaType="java.lang.String"/>
        </constructor>
    </resultMap>

    <select id="loadMaxSeq" resultType="long">
        SELECT COALESCE(MAX(seq), 0)
        FROM integration_events
    </select>

    <select id="listAfterSeq" resultMap="IntegrationEventRowResultMap">
        SELECT
            seq,
            event_id,
            producer,
            event_type,
            tenant_id,
            aggregate_type,
            aggregate_id,
            occurred_at,
            payload_json
        FROM integration_events
        WHERE seq &gt; #{lastSeqExclusive}
        ORDER BY seq ASC
        LIMIT #{limit}
    </select>

    <insert id="insert">
        INSERT INTO integration_events (
            event_id,
            producer,
            event_type,
            tenant_id,
            aggregate_type,
            aggregate_id,
            occurred_at,
            payload_json
        ) VALUES (
            #{eventId},
            #{producer},
            #{eventType},
            #{tenantId},
            #{aggregateType},
            #{aggregateId},
            #{occurredAtUtc},
            #{payloadJson}
        )
    </insert>
</mapper>
```

Create `IntegrationCheckpointMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.linkforge.foundation.eventing.mapper.IntegrationCheckpointMapper">

    <select id="findLastSeq" resultType="java.lang.Long">
        SELECT last_seq
        FROM integration_consumer_checkpoint
        WHERE consumer = #{consumer}
        LIMIT 1
    </select>

    <insert id="insert">
        INSERT INTO integration_consumer_checkpoint (consumer, last_seq)
        VALUES (#{consumer}, #{lastSeq})
    </insert>

    <update id="update">
        UPDATE integration_consumer_checkpoint
        SET last_seq = #{lastSeq}
        WHERE consumer = #{consumer}
    </update>
</mapper>
```

Create `IntegrationDeadLetterMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.linkforge.foundation.eventing.mapper.IntegrationDeadLetterMapper">

    <insert id="upsertFailure">
        INSERT INTO integration_consumer_dead_letter (
            consumer,
            seq,
            event_id,
            producer,
            event_type,
            occurred_at,
            payload_json,
            attempts,
            last_error
        ) VALUES (
            #{consumer},
            #{seq},
            #{eventId},
            #{producer},
            #{eventType},
            #{occurredAtUtc},
            #{payloadJson},
            #{attempts},
            #{lastError}
        )
        ON DUPLICATE KEY UPDATE
            attempts = #{attempts},
            last_error = #{lastError}
    </insert>
</mapper>
```

- [x] **Step 5: Add Spring beans implementing the interfaces**

Create `MybatisIntegrationEventStore` (bean discovered by `FoundationModule`’s `@ComponentScan("com.linkforge.foundation")`):

```java
package com.linkforge.foundation.eventing;

import com.linkforge.foundation.eventing.mapper.IntegrationEventMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class MybatisIntegrationEventStore implements IntegrationEventStore {

    private final IntegrationEventMapper mapper;

    public MybatisIntegrationEventStore(IntegrationEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long loadMaxSeq() {
        return mapper.loadMaxSeq();
    }

    @Override
    public List<IntegrationEventRow> listAfterSeq(long lastSeqExclusive, int limit) {
        return mapper.listAfterSeq(lastSeqExclusive, limit);
    }

    @Override
    public void append(
            String eventId,
            String producer,
            String eventType,
            Long tenantId,
            String aggregateType,
            Long aggregateId,
            Instant occurredAtUtc,
            String payloadJson
    ) {
        mapper.insert(eventId, producer, eventType, tenantId, aggregateType, aggregateId, occurredAtUtc, payloadJson);
    }
}
```

Create `MybatisIntegrationCheckpointRepository`:

```java
package com.linkforge.foundation.eventing;

import com.linkforge.foundation.eventing.mapper.IntegrationCheckpointMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisIntegrationCheckpointRepository implements IntegrationCheckpointRepository {

    private final IntegrationCheckpointMapper mapper;

    public MybatisIntegrationCheckpointRepository(IntegrationCheckpointMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long loadOrInit(String consumer) {
        Long existing = mapper.findLastSeq(consumer);
        if (existing != null) {
            return existing;
        }
        mapper.insert(consumer, 0L);
        return 0L;
    }

    @Override
    public void update(String consumer, long lastSeq) {
        mapper.update(consumer, lastSeq);
    }
}
```

Create `MybatisIntegrationDeadLetterRepository`:

```java
package com.linkforge.foundation.eventing;

import com.linkforge.foundation.eventing.mapper.IntegrationDeadLetterMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisIntegrationDeadLetterRepository implements IntegrationDeadLetterRepository {

    private final IntegrationDeadLetterMapper mapper;

    public MybatisIntegrationDeadLetterRepository(IntegrationDeadLetterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsertFailure(String consumer, IntegrationEventRow event, int attempts, String lastError) {
        mapper.upsertFailure(
                consumer,
                event.seq(),
                event.eventId(),
                event.producer(),
                event.eventType(),
                event.occurredAtUtc(),
                event.payloadJson(),
                attempts,
                lastError
        );
    }
}
```

- [x] **Step 6: Add a small foundation unit test**

Test file:
- Create: `server/foundation/src/test/java/com/linkforge/foundation/eventing/IntegrationEventRowTest.java`

Test:
- Verify record getters and null-safety assumptions (minimal).

- [x] **Step 7: Compile**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl foundation test`

Expected: `BUILD SUCCESS`

- **Optional: Step 8: Commit**

```bash
git add server/foundation
git commit -m "feat(foundation): add integration event store primitives"
```

---

### Task 8: Flyway：新增 integration tables + projection tables（DDL）

**Files:**
- Create: `server/app/src/main/resources/db/migration/V3__integration_events.sql`
- Create: `server/app/src/main/resources/db/migration/V4__redirect_link_projection.sql`
- Create: `server/app/src/main/resources/db/migration/V5__analytics_link_catalog.sql`

- [x] **Step 1: Add `integration_events` + `integration_consumer_checkpoint` + `integration_consumer_dead_letter`**

Create `V3__integration_events.sql` with the SQL from the spec:
- `integration_events`
- `integration_consumer_checkpoint`
- `integration_consumer_dead_letter`

- [x] **Step 2: Add redirect projection table**

Create `V4__redirect_link_projection.sql` with `redirect_link_projection` DDL (no archived_at column; delete row on archived/deleted).

- [x] **Step 3: Add analytics catalog table**

Create `V5__analytics_link_catalog.sql` with `analytics_link_catalog` DDL (includes `archived_at`, `deleted`).

- [x] **Step 4: Run app context smoke**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl app test`

Expected: `BUILD SUCCESS`

- **Optional: Step 5: Commit**

```bash
git add server/app/src/main/resources/db/migration
git commit -m "feat(db): add integration event log and projection tables"
```

---

### Task 9: shortlink 写侧追加 Integration Events（保持现有 API，先不做聚合重写）

**Files:**
- Modify: `server/shortlink/infrastructure/pom.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/*CommandHandler.java` (append events in the same tx)
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactory.java`
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventAppender.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/ShortLinkIntegrationEventAppendIntegrationTest.java`

- [x] **Step 1: Add dependency to contracts**

Modify `server/shortlink/infrastructure/pom.xml` to depend on:
- `linkforge-contract-shortlink`

- [x] **Step 2: Implement event factory**

Create `ShortLinkEventFactory` with methods:
- `ShortLinkPublicSnapshot toSnapshot(ShortLinkEntity e, Instant archivedAtUtc)` (use `archivedAtUtc=null` for non-archived events)
- `ShortLinkCreatedV1 created(ShortLinkEntity e, Instant occurredAtUtc, String eventId)` (snapshot archivedAtUtc=null)
- `ShortLinkUpdatedV1 updated(ShortLinkEntity e, Instant occurredAtUtc, String eventId)` (snapshot archivedAtUtc=null)
- `ShortLinkArchivedV1 archived(ShortLinkEntity e, Instant occurredAtUtc, String eventId)` (snapshot archivedAtUtc must be non-null)
- `ShortLinkRestoredV1 restored(ShortLinkEntity e, Instant occurredAtUtc, String eventId)` (snapshot archivedAtUtc=null)
- `ShortLinkDeletedV1 deleted(ShortLinkEntity e, Instant occurredAtUtc, String eventId)` (snapshot archivedAtUtc=null even if the DB row is archived)

Rule: `ArchivedV1` event must set `snapshot.archivedAtUtc` non-null; others must set it null.

- [x] **Step 3: Implement appender adaptor**

Create `ShortLinkEventAppender` that uses `IntegrationEventStore` (from foundation) to append:
- producer = `"shortlink"`
- event_type = constants from `ShortLinkEventTypes`
- aggregate_type = `"shortlink"`
- aggregate_id = `linkId`
- tenant_id = `tenantId`
- payload_json = Jackson serialize of event record using the Spring-managed `ObjectMapper` (so `Instant` writes as UTC ISO-8601)
- event_id = `UUID.randomUUID().toString()`

- [x] **Step 4: Write failing integration test**

Create `server/integration-tests/src/test/java/com/linkforge/ShortLinkIntegrationEventAppendIntegrationTest.java`:
- Create a shortlink via existing API/service (choose the simplest path available in tests; if no public API exists in tests, call `ShortLinkService.create` directly from Spring context)
- Assert `integration_events` table has a row with `producer='shortlink'` and `event_type='shortlink.ShortLinkCreated.v1'`.

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=ShortLinkIntegrationEventAppendIntegrationTest test`

Expected: FAIL before implementation.

- [x] **Step 5: Implement emission in shortlink write use-cases**

Modify `shortlink-application` command handlers to append events at the end of the same DB transaction:
- `CreateShortLinkCommandHandler` → `eventPublisher.created(...)` (CreatedV1)
- `UpdateShortLinkCommandHandler` → `eventPublisher.updated(...)` (UpdatedV1)
- `ArchiveShortLinkCommandHandler` → `eventPublisher.archived(...)` (ArchivedV1)
- `RestoreShortLinkCommandHandler` → `eventPublisher.restored(...)` (RestoredV1)
- `DeleteShortLinkCommandHandler` → `eventPublisher.deleted(...)` (DeletedV1)

Note: event append must be in the same DB transaction as the shortlink row mutation.

- [x] **Step 6: Re-run integration test**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=ShortLinkIntegrationEventAppendIntegrationTest test`

Expected: PASS

- **Optional: Step 7: Commit**

```bash
git add server/shortlink server/integration-tests
git commit -m "feat(shortlink): append integration events on link mutations"
```

---

### Task 10: Backfill Runner（取消：仓库视为 greenfield）

> **Decision (2026-03-16):** 用户确认无需 backfill（默认无历史数据/可删库重来）。因此仓库不再提供 backfill runner，也不保留 `app.shortlink.backfill.enabled` 等过渡开关。

**Files:**
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/backfill/ShortLinkEventBackfillRunner.java`
- Delete: `server/integration-tests/src/test/java/com/linkforge/ShortLinkBackfillRunnerIntegrationTest.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.java` (remove cursor scan query)
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml` (remove cursor scan SQL)
- Modify: `docs/architecture.md` / `docs/plans/*` (remove backfill references)

- [x] **Step 1: Confirm “no backfill needed” premise**

Treat `integration_events` as “born with the system”; no need to convert historical `short_links` rows into events.

- [x] **Step 2: Delete runner + test + scan query**

Remove:
- `ShortLinkEventBackfillRunner`
- `ShortLinkBackfillRunnerIntegrationTest`
- `ShortLinkQueryMapper.listAllAfterId(...)` and its XML `<select id="listAllAfterId">...`

- [x] **Step 3: Clean docs references**

Remove `backfill` / `bf:shortlink:v1:*` references from the architecture + design + implementation plan docs so docs remain SSOT.

---

## Chunk 3: Projectors + 切流 + 移除同步依赖（Phase 2/3）

### Task 11: Redirect projector + 切流到 projection

**Files:**
- Create: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjection.java`
- Create: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.java`
- Create: `server/redirect/infrastructure/src/main/resources/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.xml`
- Create: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/ArchivedLinkRedirectIntegrationTest.java`

- [x] **Step 1: Write failing redirect integration test (projection-based resolve)**

Update `RedirectNegativeCacheIntegrationTest` to stop spying `LinkMetaQueryPort` (it will be removed):
- Spy the projection mapper instead
- Assert two calls to `/r/{code}` only hit DB once (negative cache)

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=RedirectNegativeCacheIntegrationTest test`

Expected: FAIL until projector + projection resolve is implemented.

- [x] **Step 2: Implement projection repository (MyBatis)**

Implement:
- `RedirectLinkProjection` fields matching table
- Mapper methods:
  - `RedirectLinkProjection findByCode(String code)`
  - `int upsert(RedirectLinkProjection row)`
  - `int deleteByCode(String code)`

- [x] **Step 3: Implement projector job consuming `integration_events`**

Create `ShortLinkEventProjectorJob` (in redirect infrastructure):
- `@Scheduled(fixedDelayString="...")`
- `@SchedulerLock(name="lf:job:redirect-shortlink-projector", lockAtMostFor="PT2M")`
- Loop events after checkpoint, per-event transaction:
  - Deserialize payload_json to the corresponding V1 record (switch by event_type constant)
  - For Created/Updated/Restored: upsert projection + `linkCache.tryEvict(code)` + `linkCache.tryPut(meta)`
  - For Archived/Deleted: delete projection + `linkCache.tryEvict(code)`
  - On poison: insert dead-letter and advance checkpoint

- [x] **Step 4: Switch `RedirectService` resolveMeta() to projection**

In `RedirectService`:
- Cache miss → query `RedirectLinkProjectionMapper.findByCode(code)`
- If null → markNotFound + 404
- If found → map to `LinkMeta` and `tryPut`

- [x] **Step 5: Re-run redirect integration tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=RedirectNegativeCacheIntegrationTest,ArchivedLinkRedirectIntegrationTest test`

Expected: PASS

- **Optional: Step 6: Commit**

```bash
git add server/redirect server/integration-tests
git commit -m "feat(redirect): project shortlink events and resolve via projection"
```

---

### Task 12: Analytics catalog projector + topLinks 切流（移除 N+1 回源）

**Files:**
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/AnalyticsLinkCatalogRow.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/AnalyticsLinkCatalogMapper.java`
- Create: `server/analytics/infrastructure/src/main/resources/com/linkforge/analytics/infrastructure/catalog/AnalyticsLinkCatalogMapper.xml`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/AnalyticsTopLinksCatalogIntegrationTest.java`

- [x] **Step 1: Write failing integration test for topLinks without LinkMetaQueryPort**

Create `AnalyticsTopLinksCatalogIntegrationTest`:
- Seed `link_stats_daily` rows for two linkIds
- Seed `analytics_link_catalog` (or run projector once by inserting integration_events)
- Call analytics API/service and assert returned rows include `code` and `originalUrl`

Expected: FAIL until query joins catalog.

- [x] **Step 2: Implement catalog mapper + projector job**

Implement MyBatis upsert rules per spec:
- Deleted sets `deleted=1` and nulls code/url
- Archived sets archived_at

- [x] **Step 3: Update `AnalyticsQueryService.topLinks`**

Replace per-row `linkMetaQuery.findById(...)` with a join query (recommended) or a bulk query.

- [x] **Step 4: Re-run integration test**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=AnalyticsTopLinksCatalogIntegrationTest test`

Expected: PASS

- **Optional: Step 5: Commit**

```bash
git add server/analytics server/integration-tests
git commit -m "feat(analytics): project shortlink catalog and join for topLinks"
```

---

### Task 13: 维护窗口写入开关（实现计划的运行前置条件）

**Files:**
- Modify: `server/contracts/api/src/main/java/com/linkforge/contract/api/ErrorCode.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkWriteGuard.java`
- Test: `server/shortlink/interfaces/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkWriteGuardTest.java`
- Test (integration): `server/integration-tests/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkWriteGuardIntegrationTest.java`

- [x] **Step 1: Add guard component**

`ShortLinkWriteGuard` reads `app.shortlink.write-enabled` (default true) and throws `BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "维护中")` when disabled.

- [x] **Step 2: Apply guard to write endpoints**

In controllers, call guard at the beginning of:
- create/update/archive/restore/delete/import/createTag/setTags

- [x] **Step 3: Add small unit test in interfaces module**

If no unit tests exist, add a minimal Spring MVC test verifying write endpoint returns 503 when write-enabled=false.

- **Optional: Step 4: Commit**

```bash
git add server/shortlink/interfaces
git commit -m "feat(shortlink): add maintenance write guard"
```

---

### Task 14: 移除同步跨域端口 `LinkMetaQueryPort`（彻底解耦）

**Files:**
- Delete: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaQueryPort.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/port/ShortLinkMetaQueryAdapter.java`
- Modify: any references in redirect/analytics/app tests and wiring

- [x] **Step 1: Search all usages**

Run: `cd ../LinkForge_deep_ddd_20260315 && rg -n \"LinkMetaQueryPort\" server`

- [x] **Step 2: Delete interface + adapters**
- [x] **Step 3: Fix compilation**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -DskipTests compile`

Expected: `BUILD SUCCESS`

- [x] **Step 4: Run redirect+analytics integration tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests -Dtest=Redirect*IntegrationTest,Analytics*IntegrationTest test`

Expected: `BUILD SUCCESS`

- **Optional: Step 5: Commit**

```bash
git add -A
git commit -m "refactor(contracts): remove LinkMetaQueryPort; switch to projections"
```

---

## Chunk 4: 深度 DDD 内核改造（拆掉 Transaction Script）

> 目标：把 `ShortLinkService` 拆成 domain 聚合 + command/query handlers，并把规则从 service/mapper 层回收到 domain。

### Task 15: shortlink domain（聚合 + 值对象）引入（TDD）

**Files:**
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/model/ShortLink.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/model/ShortCode.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/model/OriginalUrl.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/model/QueryForwardPolicy.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/model/QueryParamPattern.java`
- Test: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/model/ShortCodeTest.java`
- Test: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/model/OriginalUrlTest.java`
- Test: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/model/ShortLinkStateMachineTest.java`

- [x] **Step 1: ShortCode failing tests**

Write `ShortCodeTest` covering:
- blank/null rejected
- length <6 or >32 rejected
- invalid chars rejected
- preserves case (Abc != abc)

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -pl shortlink/domain test`
Expected: FAIL

- [x] **Step 2: Implement `ShortCode`**

Implement immutable value object with `static ShortCode of(String raw)` returning validated instance and `String value()`.

- [x] **Step 3: OriginalUrl failing tests + impl**

Cover: http/https only, host required, length cap.

- [x] **Step 4: ShortLink aggregate state machine tests + impl**

Cover:
- update forbidden when archived
- delete requires archived (if keeping guard)
- archive/restore transitions

- **Optional: Step 5: Commit**

```bash
git add server/shortlink/domain
git commit -m "feat(shortlink-domain): add ShortLink aggregate and core value objects"
```

---

### Task 16: shortlink application handlers（替换 `ShortLinkService`）

**Files:**
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/SearchShortLinksQueryHandler.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/GetShortLinkDetailQueryHandler.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Deprecate/Delete: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`

- [x] **Step 1: Add repository ports in application**

Create interfaces:
- `ShortLinkRepository`
- `TagRepository` / `LinkTagRepository`

Handlers only depend on these ports + event appender + clock/id.

- [x] **Step 2: Write handler unit tests (mock ports)**

For each command handler:
- verify repository save called with expected new state
- verify integration event appended with correct type + snapshot

- [x] **Step 3: Implement MyBatis-backed repositories in shortlink-infrastructure**

Adapt existing mappers/entities to implement the new repository ports.

- [x] **Step 4: Switch controllers to handlers**

Controllers become thin: map request → command → handler → response.

- [x] **Step 5: Remove ShortLinkService**

Delete the old class once everything is wired.

- [x] **Step 6: Run integration tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests test`
Expected: `BUILD SUCCESS`

- **Optional: Step 7: Commit**

```bash
git add server/shortlink
git commit -m "refactor(shortlink): replace ShortLinkService with command/query handlers"
```

---

### Task 17: 清理 `link_cache_outbox`（表 + job + 依赖）

**Files:**
- Create: `server/app/src/main/resources/db/migration/V6__drop_link_cache_outbox.sql`
- Delete: `server/shortlink/**/LinkCacheOutbox*` (repo/job/mapper/xml)
- Modify: any shortlink code referencing outbox

- [x] **Step 1: Write migration dropping the table**

Create `V6__drop_link_cache_outbox.sql`:
```sql
DROP TABLE IF EXISTS link_cache_outbox;
```

- [x] **Step 2: Delete outbox code**
- [x] **Step 3: Run tests**

Run: `cd ../LinkForge_deep_ddd_20260315/server && mvn -Pit -pl integration-tests test`
Expected: `BUILD SUCCESS`

- **Optional: Step 4: Commit**

```bash
git add -A
git commit -m "refactor(shortlink): remove legacy link_cache_outbox"
```

---

## Execution Handoff

- Plan complete and saved to `docs/plans/2026-03-15-deep-ddd-shortlink-redirect-analytics-implementation-plan.md`. Ready to execute?

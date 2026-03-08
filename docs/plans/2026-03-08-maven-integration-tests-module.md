# Maven Integration Tests Module Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将所有基于 Testcontainers 的集成测试集中到同一个 Maven module，并做到：

- 本地默认执行 `mvn test` 不运行集成测试（不依赖 Docker）
- CI（GitHub Actions）默认开启并运行集成测试

**Architecture:** 在 `server/` 下新增一个仅用于集成测试的 reactor module：`integration-tests/`。

- 该 module 只包含 `src/test/java`（无业务代码），并以 `test` scope 依赖可执行应用 module `com.linkforge:linkforge-server`
- 通过 Maven profile（例如 `-Pit`）来“按需加入”此 module：默认不在 `<modules>` 中，因此本地默认构建不编译/不运行集成测试
- CI 中用 `mvn -Pit test` 激活 profile，从而把集成测试 module 加入 reactor 并执行

**Tech Stack:** Maven multi-module reactor、JUnit 5、Spring Boot Test、Testcontainers、GitHub Actions

---

## Current State (for context)

- 集成测试当前位于 `server/app/src/test/java`，并使用 `@SpringBootTest` + `@Testcontainers` 启动 `mysql`、`redis` 容器
- 这些测试会在默认 `mvn test` 时执行（Surefire 默认匹配 `*Test`），导致本地跑单测也需要 Docker
- CI 当前在 `.github/workflows/ci.yml` 中执行 `cd server && mvn -B test`

集成测试候选（当前 app module 内、使用 Testcontainers 的测试）：

- `server/app/src/test/java/com/linkforge/ArchivedLinkRedirectIntegrationTest.java`
- `server/app/src/test/java/com/linkforge/CookieCsrfIntegrationTest.java`
- `server/app/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`
- `server/app/src/test/java/com/linkforge/RedirectExperienceIntegrationTest.java`
- `server/app/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java`
- `server/app/src/test/java/com/linkforge/RedirectRiskControlIntegrationTest.java`
- `server/app/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java`
- `server/app/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

非集成测试（建议继续留在 app module、默认执行）：

- `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`（ArchUnit）
- `server/app/src/test/java/com/linkforge/config/ApiStartupValidatorTest.java`（纯单元测试）

---

### Task 1: Add Optional Integration-Test Module Skeleton

**Files:**
- Modify: `server/pom.xml`
- Create: `server/integration-tests/pom.xml`
- Create: `server/integration-tests/src/test/resources/application.properties`

**Step 1: Add a Maven profile that adds the module**

Modify `server/pom.xml`：

- 在默认 `<modules>` 中不要加入 `integration-tests`
- 新增 profile（推荐 id：`it`），在 profile 内加入：
  - `<modules><module>integration-tests</module></modules>`

**Step 2: Create the integration-tests module POM**

Create `server/integration-tests/pom.xml`：

- parent 指向 `com.linkforge:linkforge-server-parent`（`../pom.xml`）
- `artifactId` 推荐：`linkforge-integration-tests`
- 依赖（均为 `test` scope）：
  - `com.linkforge:linkforge-server`（被测应用）
  - `org.springframework.boot:spring-boot-starter-test`
  - `org.testcontainers:testcontainers`
  - `org.testcontainers:junit-jupiter`
  - `org.testcontainers:mysql`

**Step 3: Disable scheduling for integration tests**

Create `server/integration-tests/src/test/resources/application.properties`：

```properties
app.scheduling.enabled=false
```

**Step 4: Quick sanity (no Docker required)**

Run: `cd server && mvn -B test`

Expected:
- PASS
- 不会执行 Testcontainers 的集成测试（因为 module 未加入 reactor）

**Step 5: Commit**

```bash
git add server/pom.xml server/integration-tests/pom.xml server/integration-tests/src/test/resources/application.properties
git commit -m "test: add optional integration-tests module (profile gated)"
```

---

### Task 2: Move Integration Tests Into The New Module

**Files:**
- Move: `server/app/src/test/java/com/linkforge/ArchivedLinkRedirectIntegrationTest.java`
- Move: `server/app/src/test/java/com/linkforge/CookieCsrfIntegrationTest.java`
- Move: `server/app/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`
- Move: `server/app/src/test/java/com/linkforge/RedirectExperienceIntegrationTest.java`
- Move: `server/app/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java`
- Move: `server/app/src/test/java/com/linkforge/RedirectRiskControlIntegrationTest.java`
- Move: `server/app/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java`
- Move: `server/app/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

目标路径（保持原 package 不变）：

- `server/integration-tests/src/test/java/com/linkforge/*.java`
- `server/integration-tests/src/test/java/com/linkforge/shortlink/application/*.java`

**Step 1: Move the files (no code changes expected)**

保持：
- package 声明不变
- `@SpringBootTest(classes = LinkForgeApplication.class, ...)` 不变
- `DynamicPropertySource` 等保持不变

**Step 2: Local unit-test sanity (no Docker required)**

Run: `cd server && mvn -B test`

Expected:
- PASS
- app module 只剩下 `ArchitectureTest` / `ApiStartupValidatorTest` 等“非容器”测试

**Step 3: Integration tests run with profile (Docker required)**

Run: `cd server && mvn -B -Pit test`

Expected:
- PASS（前提：本机可用 Docker）
- 会拉起 `mysql:8.0.36` 与 `redis:7.2.4-alpine` 等镜像并执行集成测试

**Step 4: Commit**

```bash
git add server/app/src/test/java server/integration-tests/src/test/java
git commit -m "test: move testcontainers integration tests into integration-tests module"
```

---

### Task 3: Remove Unused Testcontainers Dependencies From app Module (Cleanup)

**Files:**
- Modify: `server/app/pom.xml`

**Step 1: Remove Testcontainers dependencies from app**

在 `server/app/pom.xml` 中删除不再需要的 test 依赖：

- `org.testcontainers:testcontainers`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:mysql`

保留：
- `spring-boot-starter-test`
- `archunit-junit5`（ArchitectureTest 仍使用）

**Step 2: Verify again**

Run: `cd server && mvn -B test`

Expected: PASS

**Step 3: Commit**

```bash
git add server/app/pom.xml
git commit -m "build: remove unused testcontainers deps from app module"
```

---

### Task 4: Enable Integration Tests In GitHub Actions CI

**Files:**
- Modify: `.github/workflows/ci.yml`

**Step 1: Switch backend job to run with the it profile**

将 `Test (server)` 从：

- `mvn -B test`

改为：

- `mvn -B -Pit test`

说明：
- 这样 CI 会在一次 Maven 运行中同时跑单测 + 集成测试（且只跑一次）

**Optional Step 2: Add docker diagnostics (helps debugging CI flakiness)**

在 Maven 测试前加一步：

```yaml
- name: Docker info
  run: docker version && docker info
```

**Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: enable integration tests via maven profile"
```

---

### Task 5: Developer Docs (Optional but Recommended)

**Files:**
- Modify: `README.md`

**Step 1: Document commands**

在 “本地开发（前后端分离）/后端” 段落补充：

- 默认（不跑集成测试）：`cd server && mvn test`
- 手动跑集成测试（需要 Docker）：`cd server && mvn -Pit test`

**Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document integration test profile"
```


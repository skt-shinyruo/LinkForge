# Split platform into core + web + redis Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `linkforge-platform-shared` a true core library (no Web/Redis starters, no runtime beans), and split Web/Redis runtime concerns into explicit modules `platform-web` and `platform-redis`.

**Architecture:** Keep existing `server/platform` artifact as “core” and add two Maven modules: `server/platform-web` (servlet/web helpers) and `server/platform-redis` (Redis cache helpers). Move `com.linkforge.platform.web.*` and `com.linkforge.redirect.service.*` out of core, remove `AppConfig/IdConfig` from core, and explicitly wire `AppProperties` + `SnowflakeIdGenerator` in API/Edge.

**Tech Stack:** Java 17, Spring Boot 3.2.x, Maven multi-module, ArchUnit.

---

### Task 1: Add failing architecture guardrails to platform-shared

**Files:**
- Modify: `server/platform/pom.xml`
- Create: `server/platform/src/test/java/com/linkforge/platform/architecture/PlatformSharedArchitectureTest.java`

**Step 1: Add ArchUnit test dependency (test scope)**

Add to `server/platform/pom.xml` (test scope, match API’s version):

```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version>
  <scope>test</scope>
</dependency>
```

**Step 2: Write a test that fails with current code**

Create `server/platform/src/test/java/com/linkforge/platform/architecture/PlatformSharedArchitectureTest.java`:

- Rule A: no classes in `com.linkforge..` are annotated with:
  - `@Component`, `@Service`, `@Repository`, `@Configuration`, `@RestControllerAdvice`, `@ControllerAdvice`, `@Controller`, `@RestController`
- Rule B: no classes depend on:
  - `jakarta.servlet..`
  - `org.springframework.web..`
  - `org.springframework.data.redis..`

**Step 3: Run tests to confirm failure**

Run: `cd server && mvn -pl platform test`

Expected: FAIL (because `platform` currently contains `CorsConfig`, `RequestIdFilter`, `LinkCacheService`, etc.).

**Step 4: Commit (optional)**

```bash
git add server/platform/pom.xml server/platform/src/test/java/com/linkforge/platform/architecture/PlatformSharedArchitectureTest.java
git commit -m "test(platform): guard platform-shared boundaries"
```

> If repo policy disallows commits, skip commit and continue.

---

### Task 2: Add new Maven modules platform-web and platform-redis

**Files:**
- Modify: `server/pom.xml`
- Create: `server/platform-web/pom.xml`
- Create: `server/platform-redis/pom.xml`

**Step 1: Register modules in parent reactor**

Edit `server/pom.xml` to include:

```xml
<module>platform-web</module>
<module>platform-redis</module>
```

**Step 2: Create `server/platform-web/pom.xml`**

Dependencies:
- `com.linkforge:linkforge-platform-shared`
- `org.springframework.boot:spring-boot-starter-web`

**Step 3: Create `server/platform-redis/pom.xml`**

Dependencies:
- `com.linkforge:linkforge-platform-shared`
- `org.springframework.boot:spring-boot-starter-data-redis`
- `org.springframework.boot:spring-boot-starter-json` (for `ObjectMapper`)

**Step 4: Build to confirm reactor wiring**

Run: `cd server && mvn -pl platform-web,platform-redis -am test`

Expected: PASS (even before moving classes, modules should compile with empty sources).

---

### Task 3: Move web runtime package from platform-shared to platform-web

**Files:**
- Move: `server/platform/src/main/java/com/linkforge/platform/web/*` → `server/platform-web/src/main/java/com/linkforge/platform/web/*`
- Modify: `server/api/pom.xml`
- Modify: `server/edge/pom.xml`
- Modify: `server/api-contract/pom.xml`

**Step 1: Move package with git mv**

```bash
git mv server/platform/src/main/java/com/linkforge/platform/web \
  server/platform-web/src/main/java/com/linkforge/platform/web
```

**Step 2: Update dependencies**

- `server/api/pom.xml`: add dependency on `linkforge-platform-web`
- `server/edge/pom.xml`: add dependency on `linkforge-platform-web`
- `server/api-contract/pom.xml`: replace dependency from `linkforge-platform-shared` to `linkforge-platform-web`
  - (because `ApiErrorResponseWriter` uses `RequestId` / `RequestIdFilter`)

**Step 3: Compile to catch missing deps**

Run: `cd server && mvn -pl platform-web,api-contract,api,edge -am test -DskipTests`

Expected: compilation succeeds.

---

### Task 4: Move redis cache package from platform-shared to platform-redis

**Files:**
- Move: `server/platform/src/main/java/com/linkforge/redirect/service/*` → `server/platform-redis/src/main/java/com/linkforge/redirect/service/*`
- Modify: `server/api/pom.xml`
- Modify: `server/edge/pom.xml`

**Step 1: Move package with git mv**

```bash
git mv server/platform/src/main/java/com/linkforge/redirect/service \
  server/platform-redis/src/main/java/com/linkforge/redirect/service
```

**Step 2: Update deps**

- `server/api/pom.xml`: add dependency on `linkforge-platform-redis`
- `server/edge/pom.xml`: add dependency on `linkforge-platform-redis`

**Step 3: Compile**

Run: `cd server && mvn -pl platform-redis,api,edge -am test -DskipTests`

Expected: compilation succeeds.

---

### Task 5: Remove runtime beans from platform-shared and wire explicitly in services

**Files:**
- Delete: `server/platform/src/main/java/com/linkforge/platform/config/AppConfig.java`
- Delete: `server/platform/src/main/java/com/linkforge/platform/id/IdConfig.java`
- Modify: `server/api/src/main/java/com/linkforge/api/LinkForgeApiApplication.java`
- Modify: `server/edge/src/main/java/com/linkforge/edge/LinkForgeEdgeApplication.java`
- Create: `server/api/src/main/java/com/linkforge/api/config/ApiIdConfig.java` (or similar)

**Step 1: Delete `AppConfig` and `IdConfig` from core**

```bash
git rm server/platform/src/main/java/com/linkforge/platform/config/AppConfig.java
git rm server/platform/src/main/java/com/linkforge/platform/id/IdConfig.java
```

**Step 2: Enable properties explicitly**

Add `@EnableConfigurationProperties(AppProperties.class)` to:
- `server/api/src/main/java/com/linkforge/api/LinkForgeApiApplication.java`
- `server/edge/src/main/java/com/linkforge/edge/LinkForgeEdgeApplication.java`

**Step 3: Provide SnowflakeIdGenerator bean in API only**

Create `server/api/src/main/java/com/linkforge/api/config/ApiIdConfig.java`:

```java
@Configuration
public class ApiIdConfig {
  @Bean
  public SnowflakeIdGenerator snowflakeIdGenerator(AppProperties properties) { ... }
}
```

**Step 4: Run unit tests for platform module**

Run: `cd server && mvn -pl platform test`

Expected: PASS once all moves/dependency removals are done (Task 1 guardrails should now pass).

---

### Task 6: Make platform-shared dependencies minimal (no Web/Redis starters)

**Files:**
- Modify: `server/platform/pom.xml`

**Step 1: Remove runtime starters from core**

Remove from `server/platform/pom.xml`:
- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-security-core`
- `spring-boot-starter-data-redis`

Keep/add minimal compile deps required by remaining code:
- `org.springframework.boot:spring-boot` (for `@ConfigurationProperties`)
- `org.springframework:spring-tx` (for `AfterCommit`)
- `org.slf4j:slf4j-api` (for `StartupValidation`)
- Keep `spring-boot-configuration-processor` as optional

**Step 2: Compile**

Run: `cd server && mvn -pl platform -am test -DskipTests`

Expected: compilation succeeds.

---

### Task 7: Fix Docker build inputs (copy new modules)

**Files:**
- Modify: `server/Dockerfile.api`
- Modify: `server/Dockerfile.edge`

**Step 1: Update COPY list**

Ensure build stage copies:
- `platform` (core)
- `platform-web`
- `platform-redis`
- `api-contract`
- `api`
- `edge`

**Step 2: Build jars**

Run:
- `cd server && mvn -q -DskipTests -pl api -am package`
- `cd server && mvn -q -DskipTests -pl edge -am package`

Expected: SUCCESS.

---

### Task 8: Full verification

**Step 1: Run full server tests**

Run: `cd server && mvn test`

Expected: `BUILD SUCCESS`.

**Step 2: (Optional) Build Docker images**

From `deploy/`:

Run: `docker compose --env-file .env build`

Expected: both `server-api` and `server-edge` images build successfully.

---

### Task 9: Commit (optional)

If commits are allowed:

```bash
git add -A
git commit -m \"refactor(platform): split core web redis modules\"
```


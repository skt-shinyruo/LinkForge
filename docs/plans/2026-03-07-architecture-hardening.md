# Architecture Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enforce bounded-context boundaries, unify layering conventions, make module wiring explicit, slim the shared kernel, and remove auth filter order coupling.

**Architecture:** Keep a Maven multi-module “modular monolith”. Replace cross-BC table access with Port-based collaboration, introduce explicit module composition (`@Import`), split `AppProperties` into focused properties beans, split error codes by BC, and implement a single composite authentication filter for `/api/**`.

**Tech Stack:** Java 17, Spring Boot 3.2, Maven reactor, Spring Security, JPA/JdbcTemplate, Redis, Flyway, ArchUnit, Vue3 + Vite.

---

### Task 0: Create isolated worktree (safety)

**Files:**
- None (git metadata)

**Step 1: Create worktree**

Run: `git worktree add /tmp/linkforge-arch-hardening HEAD`  
Expected: worktree created, detached HEAD, clean status.

**Step 2: Verify clean**

Run: `cd /tmp/linkforge-arch-hardening && git status --porcelain`  
Expected: empty output.

---

### Task 1: Add Port `LinkMetaQueryPort` (contract)

**Files:**
- Create: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaQueryPort.java`
- Test: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java` (later tasks enforce contract rules)

**Step 1: Write the failing compile reference (optional)**

Add a temporary TODO usage in redirect (later removed) to force compile-time wiring.

**Step 2: Implement Port**

Create:

```java
package com.linkforge.contract.redirect;

import java.util.Optional;

public interface LinkMetaQueryPort {

    /** Redirect path: must NOT return archived links. */
    Optional<LinkMeta> findActiveByCode(String code);

    /** Analytics enrichment path: best-effort, may return archived links; delete => empty. */
    Optional<LinkMeta> findById(long tenantId, long linkId);
}
```

**Step 3: Compile contracts**

Run: `cd server && mvn -pl contracts/redirect test`  
Expected: BUILD SUCCESS.

---

### Task 2: Implement Port in `shortlink` and refactor `redirect` to use it

**Files:**
- Create: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/port/ShortLinkMetaQueryAdapter.java`
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Delete: `server/redirect/src/main/java/com/linkforge/redirect/infrastructure/persistence/ShortLinkLookupRepository.java`

**Step 1: Write a focused integration test (red)**

Add a SpringBootTest verifying redirect resolves without `ShortLinkLookupRepository` (or that the bean is gone).  
Place under `server/app/src/test/java/com/linkforge/redirect/...` (pick existing redirect integration test suite).

**Step 2: Implement adapter**

Implement `LinkMetaQueryPort` using `ShortLinkRepository`:

```java
@Component
public class ShortLinkMetaQueryAdapter implements LinkMetaQueryPort {
  private final ShortLinkRepository repo;
  public Optional<LinkMeta> findActiveByCode(String code) { /* repo.findByCode + archived_at filter */ }
  public Optional<LinkMeta> findById(long tenantId, long linkId) { /* repo.findByTenantIdAndId */ }
}
```

If `ShortLinkRepository` lacks the right query for active-by-code, add a repository method:
- `Optional<ShortLinkEntity> findByCodeAndArchivedAtIsNull(String code)`

**Step 3: Refactor `RedirectService`**

- Remove `ShortLinkLookupRepository` field and constructor param
- On cache miss: call `linkMetaQueryPort.findActiveByCode(normalized)`
- If empty: `linkCache.markNotFound(normalized)` then throw not found

**Step 4: Remove old repo**

Delete `ShortLinkLookupRepository.java`.

**Step 5: Run redirect tests**

Run: `cd server && mvn -pl app -Dtest=Redirect* test`  
Expected: PASS.

---

### Task 3: Stop deleting analytics data when deleting links

**Files:**
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`

**Step 1: Add regression test (red)**

Create an integration test that:
- Creates a link
- Inserts fake rows into `link_stats_daily/link_visit_events`
- Deletes link
- Asserts stats rows still exist

**Step 2: Remove cross-BC deletes**

Remove the 3 `jdbcTemplate.update("DELETE FROM link_stats_...")` statements in `delete()`.

**Step 3: Run shortlink tests**

Run: `cd server && mvn -pl app -Dtest=*ShortLink* test`  
Expected: PASS.

---

### Task 4: Decouple Top Links report from `short_links` table, support “deleted link” rows

**Files:**
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java`
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java` (only if API DTO changes)
- Modify: `web/src/services/types.ts`
- Modify: `web/src/views/StatsView.vue`

**Step 1: Introduce new API DTO (server)**

Change `TopLinkStat` to include nullable fields + deleted marker:

```java
public record TopLinkStat(long linkId, String code, String originalUrl, long pv, long uv, boolean deleted) {}
```

**Step 2: Rewrite SQL to only read stats**

Replace JOIN query with:
- Query `link_stats_daily` grouped by `link_id` for date range, ordered by PV/UV
- Return `linkId/pv/uv`

**Step 3: Enrich via `LinkMetaQueryPort.findById(...)`**

Inject `LinkMetaQueryPort` into `AnalyticsQueryService` and for each row:
- If meta present: set `code/originalUrl`, `deleted=false`
- Else: set `code=null/originalUrl=null`, `deleted=true`

**Step 4: Update web types + UI**

- Update `TopLinkStat` in `web/src/services/types.ts`:
  - `code?: string | null`
  - `originalUrl?: string | null`
  - `deleted?: boolean`
- Update `web/src/views/StatsView.vue`:
  - When `t.deleted` or `!t.code`: show “已删除” and disable `/r/${code}` link and copy button

**Step 5: Run tests**

Backend: `cd server && mvn -pl app -Dtest=*Stats* test`  
Frontend (optional): `cd web && npm test` (if exists) or `npm run build` to type-check.

---

### Task 5: Normalize redirect layering (move risk logic out of `interfaces`)

**Files:**
- Move: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/risk/UserAgentBotDetector.java`
- Move: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/risk/RedisFixedWindowRateLimiter.java`
- Move: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/risk/RedirectRiskControl.java`
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/web/RedirectRiskControlFilter.java`

**Step 1: Update packages**

- `UserAgentBotDetector` -> `com.linkforge.redirect.domain.risk`
- `RedisFixedWindowRateLimiter` -> `com.linkforge.redirect.infrastructure.risk`
- `RedirectRiskControl` -> `com.linkforge.redirect.application.risk`

Keep signatures stable so the filter change is only imports/constructor wiring.

**Step 2: Fix compilation and existing tests**

Run: `cd server && mvn -pl app -Dtest=RedirectClientIpResolverTest test`  
Expected: PASS.

---

### Task 6: Slim `RedirectController` by extracting URL building policy

**Files:**
- Create: `server/redirect/src/main/java/com/linkforge/redirect/application/RedirectUrlBuilder.java`
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`

**Step 1: Extract pure logic**

Move:
- query-forward mode resolution
- allowlist/reserved filtering
- URI build logic

into `RedirectUrlBuilder` (application), keeping `HttpServletRequest`-free helpers in domain as needed.

**Step 2: Controller becomes thin**

Controller responsibilities:
- detect html/confirm
- call service to resolve meta
- call builder to compute final URL
- assemble `ResponseEntity` / HTML

**Step 3: Run redirect experience tests**

Run: `cd server && mvn -pl app -Dtest=RedirectExperienceIntegrationTest test`  
Expected: PASS.

---

### Task 7: Extract analytics SQL into infrastructure repository

**Files:**
- Create: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/AnalyticsQueryRepository.java`
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java`

**Step 1: Create repository**

Repository contains JdbcTemplate + SQL, returns row records for:
- tenantDaily
- linkDaily
- topLinks rows (linkId/pv/uv)
- dimensions rows
- visit events rows

**Step 2: Service becomes orchestrator**

Service:
- validates tenant guard + input ranges
- calls repository
- enriches topLinks via `LinkMetaQueryPort`

**Step 3: Run analytics tests**

Run: `cd server && mvn -pl app -Dtest=*Analytics* test`  
Expected: PASS.

---

### Task 8: Make composition root explicit (module `@Import` instead of global scan)

**Files:**
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Create: `server/app/src/main/java/com/linkforge/app/compose/AccountsModule.java`
- Create: `server/app/src/main/java/com/linkforge/app/compose/ShortlinkModule.java`
- Create: `server/app/src/main/java/com/linkforge/app/compose/RedirectModule.java`
- Create: `server/app/src/main/java/com/linkforge/app/compose/AnalyticsModule.java`
- Create: `server/app/src/main/java/com/linkforge/app/compose/FoundationModule.java`

**Step 1: Add module configs**

Each module config:

```java
@Configuration
@ComponentScan("com.linkforge.<module>")
public class <Module> {}
```

**Step 2: Restrict application scan + import modules**

In `LinkForgeApplication`:
- `@SpringBootApplication(scanBasePackages = "com.linkforge.app")`
- `@Import({FoundationModule.class, AccountsModule.class, ShortlinkModule.class, RedirectModule.class, AnalyticsModule.class})`

Keep `@EnableJpaRepositories/@EntityScan` broad for now.

**Step 3: Run a small boot test**

Run: `cd server && mvn -pl app -Dtest=LinkForgeIntegrationTest test`  
Expected: PASS (context loads).

---

### Task 9: Split `AppProperties` into focused properties beans and update wiring

**Files:**
- Replace/Modify: `server/foundation/src/main/java/com/linkforge/foundation/config/AppProperties.java`
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Modify: `server/app/src/main/java/com/linkforge/app/startup/AppStartupValidator.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/web/CorsConfig.java`
- Modify: all classes currently injecting `AppProperties`

**Step 1: Introduce new properties classes**

Create:
- `CoreProperties` (`app.base-url`, `app.id.*`)
- `SecurityProperties` (`app.security.*`)
- `CorsProperties` (`app.cors.*`)
- `RedirectProperties` (`app.redirect.*`)
- `AnalyticsProperties` (`app.analytics.*`)
- `EdgeProperties` (`app.edge.*`)

**Step 2: Update binder registration**

In `LinkForgeApplication`, replace `@EnableConfigurationProperties(AppProperties.class)` with:
- `@EnableConfigurationProperties({CoreProperties.class, SecurityProperties.class, ...})`

**Step 3: Update injection sites**

Replace `AppProperties` params with the smallest needed properties type.

**Step 4: Fix startup validator**

Rewrite `AppStartupValidator` to use the new properties types and update `StartupValidation` helpers accordingly.

**Step 5: Run config/boot tests**

Run: `cd server && mvn -pl app -Dtest=ApiStartupValidatorTest test`  
Expected: PASS.

---

### Task 10: Split error codes by BC + simplify exception handling

**Files:**
- Modify: `server/contracts/api/src/main/java/com/linkforge/contract/api/BusinessException.java`
- Replace: `server/contracts/api/src/main/java/com/linkforge/contract/api/ErrorCode.java`
- Create: `server/contracts/api/src/main/java/com/linkforge/contract/api/AppErrorCode.java`
- Create: `server/contracts/api/src/main/java/com/linkforge/contract/api/CommonErrorCode.java`
- Create: `server/contracts/api/src/main/java/com/linkforge/contract/api/AccountsErrorCode.java`
- Create: `server/contracts/api/src/main/java/com/linkforge/contract/api/ShortLinkErrorCode.java`
- Create: `server/contracts/api/src/main/java/com/linkforge/contract/api/OpenApiErrorCode.java`
- Modify: `server/app/src/main/java/com/linkforge/app/api/error/GlobalExceptionHandler.java`
- Modify: all usages of `ErrorCode.*`

**Step 1: Implement `AppErrorCode`**

```java
public interface AppErrorCode {
  int getCode();
  String getDefaultMessage();
  int getHttpStatus();
}
```

**Step 2: Implement per-BC enums**

Example:

```java
public enum CommonErrorCode implements AppErrorCode {
  BAD_REQUEST(40000, 400, "请求参数错误"), ...
}
```

Keep numeric codes identical to current `ErrorCode` to reduce client impact.

**Step 3: Update `BusinessException` + handler**

`BusinessException` holds `AppErrorCode`.  
`GlobalExceptionHandler` uses `ex.getErrorCode().getHttpStatus()` instead of a mapping switch.

**Step 4: Update callers**

Replace:
- `new BusinessException(ErrorCode.BAD_REQUEST)` -> `new BusinessException(CommonErrorCode.BAD_REQUEST)`
- accounts/openapi/shortlink specific -> use their enums.

**Step 5: Run server tests**

Run: `cd server && mvn test`  
Expected: BUILD SUCCESS.

---

### Task 11: Replace auth filter pair with a single composite auth filter

**Files:**
- Create: `server/app/src/main/java/com/linkforge/app/security/ApiAuthenticationFilter.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Modify/Delete: `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- Modify/Delete: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`

**Step 1: Add tests for fallback semantics**

Add integration test:
- `/api/v1/open/**`:
  - valid JWT, missing API key => OK
  - invalid JWT, valid API key => OK
  - missing both => 401

**Step 2: Implement composite filter**

In `doFilterInternal`:
- if already authenticated: continue
- if `/api/v1/open/**`:
  - try JWT if token present (on invalid token, do NOT immediately 401; fall through)
  - try API key if header present
  - if still unauthenticated: 401
- else (non-open): strict JWT behavior (invalid token => 401)

**Step 3: Wire single filter**

In `SecurityConfig`, replace `.addFilterBefore(apiKey...)` and `.addFilterBefore(jwt...)` with one filter.

**Step 4: Run auth tests**

Run: `cd server && mvn -pl app -Dtest=*Auth* test`  
Expected: PASS.

---

### Task 12: Strengthen ArchUnit rules to prevent regressions

**Files:**
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

**Step 1: Add “redirect cannot depend on JDBC” rule**

Ensure no class in `com.linkforge.redirect..` depends on `org.springframework.jdbc..` or `javax.sql..`.

**Step 2: Add layer rules**

- application/domain must not depend on servlet/web
- controllers must be in interfaces

**Step 3: Run architecture tests**

Run: `cd server && mvn -pl app -Dtest=ArchitectureTest test`  
Expected: PASS.

---

### Task 13: Full regression + docs

**Files:**
- Modify: `README.md` (only if user-facing behavior changed)
- Modify: `docs/plans/2026-03-07-architecture-hardening-design.md` (if deviations found during implementation)

**Step 1: Full test suite**

Run: `cd server && mvn test`  
Expected: BUILD SUCCESS.

**Step 2: Optional frontend build**

Run: `cd web && npm run build`  
Expected: build success, typecheck ok.

**Step 3: Final sanity**

- `redirect` endpoints still work: `/r/{code}` preview/confirm
- OpenAPI auth works with JWT fallback and API key fallback
- Top links shows deleted rows as “已删除”


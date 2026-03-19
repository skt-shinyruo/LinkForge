# Review Findings Remediation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the concrete correctness, security, and scalability issues identified in the March 19 code review without changing product scope.

**Architecture:** Keep the existing modular-monolith and Vue SPA structure, but harden the weak edges that currently leak correctness: accounts auth state must become immediately consistent and cheaper to validate, analytics dimension flush must not acknowledge work it failed to process, frontend auth bootstrap must recover from transient failures instead of caching them forever, list-heavy pages must consume server pagination instead of truncating at 50 rows, and UTC handling must be made explicit anywhere the code still relies on local JVM time. The tasks below are split by bounded write-set so they can be implemented and reviewed in parallel with minimal merge friction.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, Redis, MyBatis, Maven, Vue 3, Pinia, Vue Router, TypeScript, Vite, Vitest.

---

## Execution Notes

- Parallel-safe initial tracks: Task 1, Task 2, Task 3, and Task 5.
- Task 4 should start after Task 3 lands the frontend test harness, or it should be executed without touching `web/package.json` / `web/vite.config.ts`.
- Do not assign the same frontend infrastructure files to multiple implementer subagents at the same time.

---

## File Structure

### Backend: accounts auth-state consistency and hot-path simplification
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountStatusCache.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/cache/RedisAccountStatusCache.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/JwtService.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java` only if a new field or method signature is required
- Test: `server/accounts/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`
- Test: `server/app/src/test/java/com/linkforge/app/security/JwtAuthenticationFilterTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java`
- Create/Test if needed: `server/accounts/src/test/java/com/linkforge/accounts/application/AccountStatusServiceTest.java`

### Backend: analytics dimension flush retry semantics
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJob.java`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJobTest.java`

### Frontend: auth bootstrap resilience
- Modify: `web/package.json`
- Modify: `web/vite.config.ts`
- Modify: `web/src/stores/auth.ts`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/main.ts`
- Modify: `web/src/services/http.ts` if needed to stop unauthorized-handler recursion or duplicate side effects
- Create: `web/src/stores/auth.spec.ts`
- Create: `web/src/router/index.spec.ts`

### Frontend: paginated links and scalable stats link loading
- Modify: `web/src/services/links.ts`
- Modify: `web/src/composables/useLinksPage.ts`
- Modify: `web/src/composables/useStatsPage.ts`
- Modify: `web/src/components/links/LinkListTable.vue`
- Modify: `web/src/views/LinksView.vue`
- Modify: `web/src/services/types.ts` only if page metadata typing needs to be expanded
- Create: `web/src/composables/useLinksPage.spec.ts`
- Create: `web/src/composables/useStatsPage.spec.ts`
- Create if needed: `web/src/components/links/LinkListTable.spec.ts`

### Backend/domain: explicit UTC handling
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Test: `server/accounts/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Create/Test if needed: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java`

---

### Task 1: Make accounts auth-state changes immediate and reduce duplicate user-store reads

**Files:**
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountStatusCache.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/cache/RedisAccountStatusCache.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/JwtService.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java` only if required
- Test: `server/accounts/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`
- Test: `server/app/src/test/java/com/linkforge/app/security/JwtAuthenticationFilterTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java`
- Create/Test if needed: `server/accounts/src/test/java/com/linkforge/accounts/application/AccountStatusServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void disable_should_invalidate_cached_active_user_immediately() { ... }

@Test
void enable_should_clear_cached_disabled_user_immediately() { ... }

@Test
void resetPassword_should_invalidate_cached_active_user_immediately() { ... }

@Test
void logout_should_invalidate_cached_active_user_immediately() { ... }

@Test
void jwt_filter_should_parse_claims_once_and_delegate_user_state_validation_to_account_status_service() { ... }
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `cd server && mvn -q -P it -pl accounts,app,integration-tests -Dtest=UserAdminServiceTest,JwtAuthenticationFilterTest,AuthPersistenceIntegrationTest,AccountStatusServiceTest test`
Expected: FAIL because user disable/enable does not touch the Redis auth-status cache and JWT validation still performs a direct user-store lookup inside `JwtService`.

- [ ] **Step 3: Implement the minimal cache/state redesign**

Implement:
- extend `AccountStatusCache` with explicit invalidation or overwrite operations for tenant/user auth state
- make `RedisAccountStatusCache` support those operations without changing existing key format
- move JWT parsing in `JwtService` back to signature/claim parsing only; do not read `AccountsUserStore` inside `parseToken(...)`
- pass `tokenVersion` from the parsed principal into `AccountStatusService`
- make `AccountStatusService` validate tenant status, user status, tenant ownership, and token version in one fallback read path, then cache the resulting auth state
- make `UserAdminService.disable(...)`, `UserAdminService.enable(...)`, `UserAdminService.resetPassword(...)`, and `AuthService.logout(...)` actively invalidate or overwrite the relevant cache entries after a successful state change
- preserve the existing public 401/403 behavior in `JwtAuthenticationFilter`

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `cd server && mvn -q -P it -pl accounts,app,integration-tests -Dtest=UserAdminServiceTest,JwtAuthenticationFilterTest,AuthPersistenceIntegrationTest,AccountStatusServiceTest test`
Expected: PASS

- [ ] **Step 5: Run accounts/app regression**

Run: `cd server && mvn -q -pl accounts,app test`
Expected: PASS

### Task 2: Make analytics dimension flush retry-safe on partial Redis failure

**Files:**
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJob.java`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJobTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void flush_should_not_ack_dirty_record_when_dim_hash_scan_fails() { ... }

@Test
void flush_should_surface_member_failure_as_retryable_batch_failure() { ... }
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `cd server && mvn -q -pl analytics/infrastructure -Dtest=AnalyticsDimensionFlushJobTest test`
Expected: FAIL because `AnalyticsDimensionFlushJob` currently logs scan exceptions and still returns success to the outer ack flow.

- [ ] **Step 3: Implement the minimal reliability fix**

Implement:
- stop swallowing hash-scan failures as success inside `flushActiveMembers(...)`
- treat any Redis read/scan failure for a dirty member as a retryable batch failure so `flushDay(...)` exits without acknowledging the stream record
- keep the existing successful ack-after-upsert ordering intact
- avoid changing the Redis key contract or MySQL upsert semantics

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `cd server && mvn -q -pl analytics/infrastructure -Dtest=AnalyticsDimensionFlushJobTest test`
Expected: PASS

- [ ] **Step 5: Run analytics regression**

Run: `cd server && mvn -q -pl analytics/infrastructure test`
Expected: PASS

### Task 3: Make frontend auth bootstrap retryable instead of one-shot failure cached forever

**Files:**
- Modify: `web/package.json`
- Modify: `web/vite.config.ts`
- Modify: `web/src/stores/auth.ts`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/main.ts`
- Modify: `web/src/services/http.ts` if needed to stop unauthorized-handler recursion or duplicate side effects
- Create: `web/src/stores/auth.spec.ts`
- Create: `web/src/router/index.spec.ts`

- [ ] **Step 1: Write the failing frontend tests**

```ts
it("retries init after a transient /me failure instead of pinning initialized=true forever", async () => { ... })

it("does not redirect an authenticated cookie-mode user to /login after one failed bootstrap request", async () => { ... })
```

- [ ] **Step 2: Add the test harness and run the focused tests to verify they fail**

Run: `cd web && npm install`
Run: `cd web && npx vitest run src/stores/auth.spec.ts src/router/index.spec.ts`
Expected: FAIL because the current store sets `initialized` before `/me` succeeds and the router caches the first `initPromise`.

- [ ] **Step 3: Implement the minimal bootstrap redesign**

Implement:
- add a lightweight frontend unit-test setup with Vitest and `jsdom`
- change `auth.init()` so failure does not permanently lock the store into an initialized-but-empty state
- add an idempotent in-flight promise inside the store instead of a router-global one-shot promise
- ensure the unauthorized handler clears state without recursively triggering logout/network loops
- keep existing login/logout API behavior and route targets unchanged

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `cd web && npx vitest run src/stores/auth.spec.ts src/router/index.spec.ts`
Expected: PASS

- [ ] **Step 5: Run frontend build verification**

Run: `cd web && npm run build`
Expected: PASS

### Task 4: Consume backend pagination instead of truncating links/stat selections at 50 rows

**Files:**
- Modify: `web/src/services/links.ts`
- Modify: `web/src/composables/useLinksPage.ts`
- Modify: `web/src/composables/useStatsPage.ts`
- Modify: `web/src/components/links/LinkListTable.vue`
- Modify: `web/src/views/LinksView.vue`
- Modify: `web/src/services/types.ts` only if required
- Create: `web/src/composables/useLinksPage.spec.ts`
- Create: `web/src/composables/useStatsPage.spec.ts`
- Create if needed: `web/src/components/links/LinkListTable.spec.ts`

**Prerequisite:** Task 3 has already landed the Vitest/jsdom harness in `web/package.json` and `web/vite.config.ts`.

- [ ] **Step 1: Write the failing frontend tests**

```ts
it("useLinksPage requests the selected page instead of hardcoding page=0,size=50", async () => { ... })

it("changing keyword or archived filter resets pagination back to page 0", async () => { ... })

it("useStatsPage can load link options beyond the first backend page", async () => { ... })

it("LinkListTable exposes previous/next paging actions through its public props/events", async () => { ... })
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `cd web && npx vitest run src/composables/useLinksPage.spec.ts src/composables/useStatsPage.spec.ts src/components/links/LinkListTable.spec.ts`
Expected: FAIL because the current links page hardcodes `page: 0, size: 50`, the stats page only consumes the first page of links, and the table does not expose pagination controls.

- [ ] **Step 3: Implement the minimal pagination-aware behavior**

Implement:
- keep `listLinks(...)` returning full backend page metadata instead of only the items
- make `useLinksPage` hold `page`, `size`, and `total`, and wire previous/next page actions into `LinkListTable`
- keep the existing search and archived filters, but reset to page 0 when those filters change
- make `useStatsPage` load link options through paginated iteration or another bounded helper so it is not limited to the first 50 rows
- do not change backend contracts or add a new endpoint unless existing pagination proves insufficient

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `cd web && npx vitest run src/composables/useLinksPage.spec.ts src/composables/useStatsPage.spec.ts src/components/links/LinkListTable.spec.ts`
Expected: PASS

- [ ] **Step 5: Run frontend build verification**

Run: `cd web && npm run build`
Expected: PASS

### Task 5: Remove remaining local-time fallbacks from UTC-sensitive paths

**Files:**
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Test: `server/accounts/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Create/Test if needed: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void authenticate_should_write_lastUsedAt_in_utc_based_on_injected_clock() { ... }

@Test
void archive_with_null_nowUtc_should_not_fall_back_to_local_jvm_timezone() { ... }
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `cd server && mvn -q -pl accounts,shortlink/domain -Dtest=ApiKeyServiceTest,ShortLinkTest test`
Expected: FAIL because `ApiKeyService` still uses `LocalDateTime.now()` and `ShortLink.archive(null)` still falls back to local JVM time.

- [ ] **Step 3: Implement the minimal UTC fix**

Implement:
- inject `Clock` into `ApiKeyService` and derive `LocalDateTime` through `LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)`
- make `ShortLink.archive(...)` use explicit UTC fallback semantics if `null` is ever passed, or remove the implicit-now branch if all callers already provide a timestamp
- keep database column types and external DTO formats unchanged

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `cd server && mvn -q -pl accounts,shortlink/domain -Dtest=ApiKeyServiceTest,ShortLinkTest test`
Expected: PASS

- [ ] **Step 5: Run regression for touched modules**

Run: `cd server && mvn -q -pl accounts,shortlink test`
Expected: PASS

## Final verification

- [ ] **Run the full backend suite in the worktree**

Run: `cd server && mvn -q test`
Expected: PASS for the default reactor modules

Run: `cd server && mvn -q -P it test`
Expected: PASS

- [ ] **Run frontend unit tests and build in the worktree**

Run: `cd web && npm run test`
Expected: PASS

Run: `cd web && npm run build`
Expected: PASS

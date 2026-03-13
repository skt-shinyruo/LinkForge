# LinkForge Hardening & Bugfixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the correctness/security/scalability issues found in code review across redirect, API security, analytics ingestion, and the admin web UI.

**Architecture:** Apply small targeted fixes with TDD in backend modules (JUnit 5). For frontend, prefer existing test runner if present; otherwise verify with `vue-tsc`/build. Avoid breaking public API/DB compatibility; use additive Flyway migrations for schema/index changes.

**Tech Stack:** Java 17, Spring Boot 3.x, Spring Security, Redis Streams, MyBatis, Flyway, JUnit 5 + AssertJ, Vue 3 + TypeScript.

---

### Task 1: Fix redirect query-forward URL corruption (double-encoding)

**Files:**
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/application/RedirectUrlBuilder.java`
- Create: `server/redirect/src/test/java/com/linkforge/redirect/application/RedirectUrlBuilderTest.java`

**Step 1: Write the failing test**

- Case: target URL already contains encoded query (`%2B`, `%20`, etc.) and a fragment; forwarding request params must not turn `%` into `%25` and must keep the fragment.

**Step 2: Run test to verify it fails**

Run:
- `cd server && mvn -pl redirect -am test -Dmaven.repo.local=../.m2/repository -Dtest=RedirectUrlBuilderTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected:
- FAIL: output contains `%25`-prefixed sequences or fragment/query ordering is broken.

**Step 3: Implement minimal fix**

- Avoid rebuilding `URI` from raw components in a way that re-serializes existing `%xx`.
- Preserve the original URL as a string; insert merged query before `#fragment` using string operations.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

---

### Task 2: Make redirect availability/expiry boundary consistent

**Files:**
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
- Test: add/extend controller/service unit tests under `server/redirect/src/test/java/...`

**Steps:**
- Write a test for an exact-boundary `expiresAt == now` scenario.
- Align both “redirect decision” and “unavailable reason” to the same inclusive/exclusive rule.

---

### Task 3: Harden redirect risk-control behavior (key sanitization, path matching, no-store errors)

**Files:**
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/application/risk/RedirectRiskControl.java`
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/web/RedirectRiskControlFilter.java`
- Modify: `server/redirect/src/main/java/com/linkforge/redirect/interfaces/web/error/RedirectErrorResponseWriter.java`
- Test: add/extend tests under `server/redirect/src/test/java/...`

**Steps:**
- Test: `ip_code` limiter key should not contain unbounded / unsafe user-controlled data.
- Implement: normalize/sanitize `code` used in Redis keys (length + charset).
- Implement: match `/r/**` using servlet path/context-path aware logic (avoid `requestURI.startsWith("/r/")` fragility).
- Implement: risk reject responses should include `Cache-Control: no-store` (and `Pragma: no-cache` for compatibility).

---

### Task 4: Fix API auth filter lockout when cookie JWT is invalid/expired

**Files:**
- Modify: `server/app/src/main/java/com/linkforge/app/security/ApiCompositeAuthenticationFilter.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Modify: `server/app/src/test/java/com/linkforge/app/security/ApiCompositeAuthenticationFilterTest.java`

**Steps:**
- Test: invalid cookie token must not block permitAll endpoints (`/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/auth/csrf`).
- Implement: on cookie token parse failure, clear the cookie (best-effort) and continue filter chain (do not hard 401).
- Keep strict behavior for invalid Bearer tokens (401) to avoid silently ignoring explicit auth headers.

---

### Task 5: Tighten CSRF ignoring rules to actual auth modes

**Files:**
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Test: add/extend security tests under `server/app/src/test/java/...` if present

**Steps:**
- Only ignore CSRF for Authorization header when it is a real Bearer token (starts with `Bearer ` and has a non-blank token).
- Only ignore CSRF for `X-API-Key` on OpenAPI routes (and/or require `ROLE_OPENAPI` on those endpoints).

---

### Task 6: Sanitize `X-Request-Id` to prevent abuse

**Files:**
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/web/RequestIdFilter.java`
- Create: `server/foundation/src/test/java/com/linkforge/foundation/web/RequestIdFilterTest.java`

**Steps:**
- Test: overly-long or invalid header values must be replaced by a generated safe request id.
- Implement: accept only bounded length + safe charset; otherwise generate a new id.

---

### Task 7: Make analytics ingestion robust (idempotency + poison isolation)

**Files:**
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsEventIngestJob.java`
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/repository/LinkVisitEventMapper.xml` (or equivalent MyBatis mapper)
- Test: add unit/integration tests under `server/analytics/src/test/java/...`

**Steps:**
- Write failing test: a batch containing a duplicate (unique `(tenant_id, request_id)`) should not stall the consumer.
- Implement: batch insert should ignore duplicates (DB-side upsert/ignore) so ack can proceed.
- Implement: on non-duplicate failures, split the batch to isolate bad records; ack or dead-letter the bad ones so the stream does not get stuck pending.

---

### Task 8: Add missing index for retention deletes

**Files:**
- Create: `server/app/src/main/resources/db/migration/V2__analytics_indexes.sql`

**Steps:**
- Add an index on `link_visit_event.created_at` (and any other retention-filtered columns) to prevent table scans.
- Verify Flyway runs in tests (or `mvn -pl app -am test`).

---

### Task 9: Fix admin web UI auth/bootstrap and HTTP defaults

**Files:**
- Modify: `web/src/stores/auth.ts`
- Modify: `web/src/services/http.ts`
- Modify: `web/src/views/LoginView.vue`
- Modify: `web/src/views/LinksView.vue`
- Modify: `web/src/views/StatsView.vue`

**Steps:**
- If a test runner exists: write failing unit tests (vitest/jest) for auth init + redirect param validation; then implement.\n+- Otherwise: implement with minimal changes and verify with `cd web && npm run build` (and `npm run typecheck` if available).

---

### Task 10: Regression verification

**Backend:**
- `cd server && mvn test -Dmaven.repo.local=../.m2/repository`

**Frontend:**
- `cd web && npm ci && npm run build`


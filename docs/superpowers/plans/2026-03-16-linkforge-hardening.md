# LinkForge Hardening & Consistency Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (subagents) to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make LinkForge’s auth boundaries, time semantics, analytics correctness, and projector reliability consistent and production-safe (no silent data loss, no RBAC bypass, no time drift).

**Architecture:** Split API security into two independent Spring Security filter chains (OpenAPI key vs. JWT), align audit fields for OpenAPI writes, make time values unambiguous (UTC `Instant` on management APIs + UI conversion), make analytics aggregation monotonic and implement dimension UV, and make projectors forward-compatible and transient-failure-safe.

**Tech Stack:** Java 21+ (Spring Boot, Spring Security, MyBatis, Flyway), Redis, MySQL, Vue 3 + TypeScript + Vite.

---

## File/Module Map (what changes where)

### Backend: Security & Accounts
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Modify/Delete: `server/app/src/main/java/com/linkforge/app/security/ApiCompositeAuthenticationFilter.java` (replace with dedicated filters)
- Create: `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- Create: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java` (add optional `apiKeyId`, helpers)
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java` (tenant status validation)
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java` (tenant/user status checks + cache)
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/TenantMapper.java` / XML as needed
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/UserMapper.java` / XML as needed

### Backend: Shortlink (audit + time semantics + tags)
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/TagController.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java` (use `Instant` for time fields)
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/mapper/ShortLinkDtoMapper.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/*` and `query/*` for `Instant` conversion
- Modify: `server/shortlink/infrastructure/src/main/java/.../ShortLinkEntity.java` + mappers/XML for `created_by_type`
- Flyway: create `server/app/src/main/resources/db/migration/V7__short_links_created_by_type.sql`

### Backend: Redirect projector + Analytics projector reliability
- Modify: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java` (ignore unknown event types; poison vs transient)
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java` (don’t advance checkpoint on transient failure)

### Backend: Analytics correctness (dimension UV + monotonic flush)
- Modify: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/AnalyticsKeys.java` (add dimension UV key contract)
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java` (write dim UV HLL)
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJob.java` (read dim UV)
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJob.java` (remove “skip if 0”; rely on monotonic upsert)
- Modify: `server/analytics/infrastructure/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/LinkStatsDailyMapper.xml` (monotonic upsert)
- Modify: `server/analytics/infrastructure/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/LinkStatsDimDailyMapper.xml` (monotonic upsert)

### Frontend: auth hydration + UTC time UX
- Modify: `web/src/stores/auth.ts` (Bearer mode hydrate via `/api/v1/me`)
- Modify: `web/src/views/LinksView.vue` (convert datetime-local ↔ `Instant` strings; display)
- Optional Modify: `web/src/views/TagsView.vue` (only if tag RBAC changes impact UX)

### Tests
- Modify/Add: `server/integration-tests/src/test/java/...` for security boundaries, OpenAPI behavior, time conversion, analytics keys
- Modify/Add: unit tests in `server/*/src/test/java/...` as appropriate

---

## Task 1: Split Security Filter Chains (OpenAPI Key vs JWT)

**Goal:** `/api/v1/open/**` accepts ONLY `X-API-Key` auth; JWT must not authenticate OpenAPI routes. Non-open APIs accept JWT (Bearer or cookie) only.

**Files:**
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Create: `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- Create: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- Remove/Deprecate: `server/app/src/main/java/com/linkforge/app/security/ApiCompositeAuthenticationFilter.java`

- [x] Implement two `SecurityFilterChain` beans with explicit `@Order` and `securityMatcher`:
  - Chain A (higher priority): `/api/v1/open/**` → stateless, CSRF disabled, requires auth via API key filter only.
  - Chain B: `/api/v1/**` (excluding open) → JWT filter + optional CSRF when cookie mode enabled.
- [x] Ensure `/api/v1/auth/*` still behaves (login/register/logout/csrf) without “bad cookie lockout”.
- [x] Add tests proving JWT cannot call OpenAPI routes; API key cannot call non-open routes.

**Acceptance:**
- Calling `/api/v1/open/links` with Bearer token returns 401/403 (not authenticated).
- Calling `/api/v1/open/links` with valid `X-API-Key` succeeds.
- Calling `/api/v1/links` with `X-API-Key` fails (401).

---

## Task 2: Enforce Tenant/User Status for JWT + API Keys

**Goal:** Tenant disabled ⇒ both JWT and API key auth fail. User disabled ⇒ JWT auth fails.

**Files:**
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/*AuthenticationFilter.java`
- Modify mappers: `server/accounts/src/main/java/.../TenantMapper.java`, `UserMapper.java` (+ XML)

- [x] Implement `AccountStatusService`:
  - `requireActiveTenant(long tenantId)`
  - `requireActiveUserAndTenant(long userId, long tenantId)`
  - Cache results (Redis best-effort, short TTL) to avoid per-request DB reads.
- [x] Wire status checks into:
  - API key auth (after API key validated)
  - JWT auth (after token parsed)

**Acceptance:**
- If tenant status is disabled, any request using JWT/API key returns 403 (or consistent business code).
- If user status is disabled, JWT requests fail.

---

## Task 3: Fix OpenAPI Audit (created_by_type + created_by_id)

**Goal:** OpenAPI-created shortlinks have correct audit info: created by API key id, and type stored.

**Files:**
- Flyway: `server/app/src/main/resources/db/migration/V7__short_links_created_by_type.sql`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java` (add createdByType)
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/CreatedByType.java`
- Modify: `server/shortlink/infrastructure/src/main/java/.../ShortLinkEntity.java` (+ mappers/XML)
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`

- [x] Add `created_by_type` column with default `USER` for existing rows.
- [x] For OpenAPI, use `AuthPrincipal.apiKeyId` as createdBy and set created_by_type=`API_KEY`.
- [x] For JWT, set created_by_type=`USER`.

**Acceptance:**
- New OpenAPI-created rows have `created_by_type='API_KEY'` and `created_by=<apiKeyId>`.
- New JWT-created rows have `created_by_type='USER'` and `created_by=<userId>`.

---

## Task 4: Make Tag Semantics Consistent (Remove RBAC Bypass)

**Goal:** Either (A) tags are user-manageable, or (B) tags are admin-only AND cannot be implicitly created. Pick one and align API + UI + service behavior.

**Recommended:** A) Tags user-manageable (because `SetLinkTagsCommandHandler` already creates tags today).

**Files:**
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/TagController.java`
- Optional Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/SetLinkTagsCommandHandler.java`
- Modify: `web/src/views/TagsView.vue` (if needed)

- [x] Remove `TENANT_ADMIN` restriction on Tag list/create; keep authenticated-only.
- [x] Keep server-side caps (`<=64` name, max per link) and ensure errors are user-friendly.

**Acceptance:**
- Normal authenticated users can list/create tags via `/api/v1/tags`.
- Tag creation via link tagging is no longer a “bypass” (because it’s allowed).

---

## Task 5: Make `expiresAt` Unambiguous (Use `Instant` on Management APIs)

**Goal:** Eliminate timezone drift by using UTC `Instant` at HTTP boundary and converting properly for storage.

**Files:**
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/mapper/ShortLinkDtoMapper.java`
- Modify: relevant handlers (`CreateShortLinkCommandHandler`, `UpdateShortLinkCommandHandler`, `ImportShortLinksCsvCommandHandler`, `ExportShortLinksCsvQueryHandler`)
- Modify: `web/src/views/LinksView.vue`

- [x] Change API models:
  - `CreateLinkRequest.expiresAt` and `UpdateLinkRequest.expiresAt` → `Instant`
  - `LinkDto.expiresAt/archivedAt/createdAt` → `Instant` (serialize as `...Z`)
- [x] Convert `Instant` ↔ `LocalDateTime` using `ZoneOffset.UTC` for persistence/domain.
- [x] CSV import accepts both legacy `LocalDateTime` and new `Instant` formats (strictly documented).
- [x] Frontend converts:
  - datetime-local (local) → `Instant` string when submitting
  - `Instant` string → local datetime-local when editing

**Acceptance:**
- No 8-hour drift for users in UTC+8/UTC-xx.
- UI shows correct local time while server stores UTC.

---

## Task 6: Frontend Auth Hydration (Bearer mode)

**Goal:** Refreshing the page keeps admin role + email populated in Bearer mode.

**Files:**
- Modify: `web/src/stores/auth.ts`

- [x] In Bearer mode, if token exists, call `/api/v1/me` and populate `email/tenantId/roles`.
- [x] On 401, clear token and route to login (reuse existing unauthorized handler).

**Acceptance:**
- After refresh, admin-only UI (import/export) remains visible for admins.

---

## Task 7: Analytics Dimension UV + Monotonic Flush

**Goal:** Dimension UV is non-zero and consistent; flush never regresses stats due to partial Redis failures.

**Files:**
- Modify: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/AnalyticsKeys.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJob.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJob.java`
- Modify: `server/analytics/infrastructure/src/main/resources/.../LinkStatsDailyMapper.xml`
- Modify: `server/analytics/infrastructure/src/main/resources/.../LinkStatsDimDailyMapper.xml`

- [x] Add Redis key contract for dim UV HLL keys (safe/hardened key format).
- [x] Record UV per dim value using `PFADD`.
- [x] Flush reads PV hash + UV HLL and writes both.
- [x] Change MySQL upserts to monotonic (`GREATEST(existing, incoming)`).

**Acceptance:**
- `/api/v1/stats/links/{id}/dimensions?type=...` returns meaningful uv values.
- Daily/Dim stats never decrease over time within the same day.

---

## Task 8: Projector Reliability & Forward Compatibility

**Goal:** Unknown future event types do not flood DLQ. Transient failures do not permanently skip events.

**Files:**
- Modify: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java`

- [x] Redirect projector: ignore unknown `shortlink.*` events; DLQ only invalid JSON / missing required fields.
- [x] Analytics catalog projector:
  - Poison errors (invalid JSON/required fields) → DLQ + checkpoint advance
  - Transient DB errors → stop processing WITHOUT advancing checkpoint (retry next tick)

**Acceptance:**
- Adding a new shortlink event type does not create DLQ spam.
- Temporary DB outage does not cause permanent catalog gaps.

---

## Task 9: Verification

**Commands:**
- [x] Backend unit tests: `cd server && mvn test`
- [x] (If available) Integration tests: `cd server && mvn -Pit test`
- [x] Frontend build: `cd web && npm run build`

**Expected:**
- All modified tests pass
- No compilation/type errors

---

## Notes / Constraints
- Do **not** run `git commit` unless explicitly requested by the user of this session.
- Prefer adding tests close to the impacted behavior (unit tests for pure logic; integration tests for end-to-end security boundaries).

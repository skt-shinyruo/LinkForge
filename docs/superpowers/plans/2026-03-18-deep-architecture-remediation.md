# LinkForge Deep Architecture Remediation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining high-cost architectural inconsistencies in LinkForge by hardening backend boundaries, separating shared-library code from runtime infrastructure, and shrinking oversized backend/frontend orchestrators.

**Architecture:** Execute in three phases. First harden the highest-risk backend seams (`accounts` and `redirect`). Next extract runtime/platform responsibilities out of `foundation` and out of the `app` knowledge center. Finally decompose large analytics/frontend orchestrators into smaller collaborators while preserving behavior.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, MyBatis, Redis, Maven reactor, Vue 3, TypeScript, Vite.

---

## File/Module Map

### Phase 1: Accounts boundary hardening
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsPasswordHasher.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/ApiKeyAuthCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountStatusCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/cache/RedisApiKeyAuthCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/cache/RedisAccountStatusCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/SpringAccountsPasswordHasher.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`

### Phase 1: Redirect flow simplification
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHtmlPageRenderer.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectConfirmHrefBuilder.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectAvailabilityPolicy.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerExpiryBoundaryTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerPreviewConfirmHrefLimitTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerTimezoneTest.java`

### Phase 2: Foundation/platform extraction and app bootstrap slimming
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/web/RequestIdFilter.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/web/CorsConfig.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/time/TimeConfig.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/persistence/IntegrationEventMybatisConfig.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/security/TenantGuard.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationCheckpointRepository.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationDeadLetterRepository.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationEventStore.java`
- Create: `server/app/src/main/java/com/linkforge/app/startup/StartupCheck.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/startup/AccountsStartupCheck.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/startup/RedirectStartupCheck.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/startup/AnalyticsStartupCheck.java`
- Modify: `server/app/src/main/java/com/linkforge/app/startup/AppStartupValidator.java`
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Modify/Test: `server/app/src/test/java/com/linkforge/config/ApiStartupValidatorTest.java`

### Phase 3: Analytics ingest decomposition
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventStreamReader.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventBatchAssembler.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventBatchWriter.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventDeadLetterWriter.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java`
- Create/Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobTest.java`

### Phase 3: Frontend decomposition
- Create: `web/src/composables/useStatsPage.ts`
- Create: `web/src/components/stats/StatsRangeToolbar.vue`
- Create: `web/src/components/stats/StatsOverviewPanel.vue`
- Create: `web/src/components/stats/TopLinksTable.vue`
- Create: `web/src/components/stats/LinkTrendPanel.vue`
- Create: `web/src/composables/links/linkFormCodec.ts`
- Create: `web/src/composables/links/useLinkImportExport.ts`
- Create: `web/src/composables/links/useLinkMutations.ts`
- Modify: `web/src/views/StatsView.vue`
- Modify: `web/src/composables/useLinksPage.ts`
- Modify: `web/src/services/types.ts`
- Modify: `web/src/components/links/LinkCreateForm.vue`
- Modify: `web/src/components/links/LinkListTable.vue`

---

### Task 1: Remove Remaining Technical Leakage from `accounts.application`

**Files:**
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsPasswordHasher.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/ApiKeyAuthCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountStatusCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/cache/RedisApiKeyAuthCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/cache/RedisAccountStatusCache.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/SpringAccountsPasswordHasher.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`

- [ ] **Step 1: Write the failing boundary tests**

Add an architecture rule that `com.linkforge.accounts.application..` must not depend on `org.springframework.data.redis..` or `org.springframework.security.crypto..`, and update focused application tests so constructor wiring uses new ports instead of raw framework types.

Run: `cd server && mvn -q -pl app,accounts -am -Dtest=ArchitectureTest,ApiKeyServiceTest,UserAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL because application services still reference Redis/security framework classes directly.

- [ ] **Step 2: Introduce focused ports**

Create ports for password hashing, API-key auth cache, and tenant/user status caching. Keep the method surfaces narrow and tailored to current use cases; do not leak Redis or Spring abstractions through the port types.

- [ ] **Step 3: Implement infrastructure adapters**

Back the new ports with Redis and Spring Security adapters in `accounts.infrastructure`, preserving existing key formats, TTL semantics, and best-effort error handling.

- [ ] **Step 4: Refactor application services onto the new ports**

Replace `StringRedisTemplate` / `PasswordEncoder` usage in `AuthService`, `UserAdminService`, `ApiKeyService`, and `AccountStatusService` with the new ports while keeping current business behavior and caching semantics intact.

- [ ] **Step 5: Run focused verification**

Run: `cd server && mvn -q -pl app,accounts -am -Dtest=ArchitectureTest,ApiKeyServiceTest,UserAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with the new architecture rule and updated service tests.

---

### Task 2: Simplify Redirect Runtime Flow and Split Controller Helpers

**Files:**
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHtmlPageRenderer.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectConfirmHrefBuilder.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectAvailabilityPolicy.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerExpiryBoundaryTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerPreviewConfirmHrefLimitTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerTimezoneTest.java`

- [ ] **Step 1: Write the failing redirect tests**

Update redirect tests to reflect the simplified runtime model and add focused coverage for extracted helper behavior (availability calculation, confirm-href building, and HTML rendering entry points).

Run: `cd server && mvn -q -pl redirect/application,redirect/interfaces -am -Dtest=RedirectServiceAuthoritativeFallbackTest,RedirectServiceTimezoneTest,RedirectControllerExpiryBoundaryTest,RedirectControllerPreviewConfirmHrefLimitTest,RedirectControllerTimezoneTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL because `RedirectService` still carries an unused projection dependency and `RedirectController` still owns helper logic inline.

- [ ] **Step 2: Remove dead projection dependency from application flow**

Refactor `RedirectService` to depend only on the ports it actually uses in monolith mode: cache, authoritative source, visit recorder, and clock.

- [ ] **Step 3: Extract interface-layer collaborators**

Move HTML page rendering, confirm-link construction, and availability classification out of `RedirectController` into focused collaborators so the controller becomes thin request orchestration.

- [ ] **Step 4: Preserve behavior and logging**

Keep current browser/non-browser behavior, request-id logging, preview gating, and redirect-status resolution exactly as today while delegating the heavy lifting.

- [ ] **Step 5: Run focused verification**

Run: `cd server && mvn -q -pl redirect/application,redirect/interfaces -am -Dtest=RedirectServiceAuthoritativeFallbackTest,RedirectServiceTimezoneTest,RedirectControllerExpiryBoundaryTest,RedirectControllerPreviewConfirmHrefLimitTest,RedirectControllerTimezoneTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with smaller runtime objects and unchanged behavior.

---

### Task 3: Extract Runtime Beans Out of Shared `foundation` and Shrink `app` Knowledge Center

**Files:**
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/web/RequestIdFilter.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/web/CorsConfig.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/time/TimeConfig.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/persistence/IntegrationEventMybatisConfig.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/security/TenantGuard.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationCheckpointRepository.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationDeadLetterRepository.java`
- Move/Create: `server/foundation/src/main/java/com/linkforge/foundation/runtime/eventing/MybatisIntegrationEventStore.java`
- Create: `server/app/src/main/java/com/linkforge/app/startup/StartupCheck.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/startup/AccountsStartupCheck.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/startup/RedirectStartupCheck.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/startup/AnalyticsStartupCheck.java`
- Modify: `server/app/src/main/java/com/linkforge/app/startup/AppStartupValidator.java`
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Modify/Test: `server/app/src/test/java/com/linkforge/config/ApiStartupValidatorTest.java`

- [ ] **Step 1: Write the failing guardrail tests**

Broaden foundation guardrails to cover the current shared-library intent and add focused startup-validator tests that assert module-owned checks are discoverable from `app`.

Run: `cd server && mvn -q -pl foundation,app -am -Dtest=FoundationSharedArchitectureTest,ArchitectureTest,ApiStartupValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL because runtime beans still live under `foundation` and startup validation remains centralized.

- [ ] **Step 2: Move runtime-only beans to explicit runtime ownership**

Relocate runtime-only filters/configs/scanners/tenant-guard beans into explicit `com.linkforge.foundation.runtime..` packages so the shared-library surface becomes unambiguous without forcing application code to depend on `app`.

- [ ] **Step 3: Replace the monolithic startup validator**

Introduce a small `StartupCheck` contract in `app`, then move redirect/accounts/analytics-specific validation into module-owned checks that `app` aggregates.

- [ ] **Step 4: Run focused verification**

Run: `cd server && mvn -q -pl foundation,app -am -Dtest=FoundationSharedArchitectureTest,ArchitectureTest,ApiStartupValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with explicit runtime ownership and slimmer `app` bootstrap knowledge.

---

### Task 4: Decompose the Analytics Ingest Pipeline

**Files:**
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventStreamReader.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventBatchAssembler.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventBatchWriter.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/VisitEventDeadLetterWriter.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java`
- Create/Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobTest.java`

- [ ] **Step 1: Write the failing focused tests**

Add tests around batch assembly / poison isolation / DLQ writing so the ingest behavior is specified independently of the scheduled coordinator.

Run: `cd server && mvn -q -pl analytics/infrastructure -am -Dtest=AnalyticsEventIngestJobTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL because the helper collaborators do not exist yet and behavior is still owned by the job class.

- [ ] **Step 2: Extract reader / assembler / writer / DLQ collaborators**

Split stream interaction, record sanitation, batch persistence, and dead-letter writing into focused classes with narrow method surfaces.

- [ ] **Step 3: Reduce `AnalyticsEventIngestJob` to orchestration**

Keep scheduling, high-level control flow, and retry policy in the job; delegate everything else to the extracted collaborators.

- [ ] **Step 4: Run focused verification**

Run: `cd server && mvn -q -pl analytics/infrastructure -am -Dtest=AnalyticsEventIngestJobTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with materially smaller coordinator logic.

---

### Task 5: Finish Frontend Page/Composable Decomposition

**Files:**
- Create: `web/src/composables/useStatsPage.ts`
- Create: `web/src/components/stats/StatsRangeToolbar.vue`
- Create: `web/src/components/stats/StatsOverviewPanel.vue`
- Create: `web/src/components/stats/TopLinksTable.vue`
- Create: `web/src/components/stats/LinkTrendPanel.vue`
- Create: `web/src/composables/links/linkFormCodec.ts`
- Create: `web/src/composables/links/useLinkImportExport.ts`
- Create: `web/src/composables/links/useLinkMutations.ts`
- Modify: `web/src/views/StatsView.vue`
- Modify: `web/src/composables/useLinksPage.ts`
- Modify: `web/src/services/types.ts`
- Modify: `web/src/components/links/LinkCreateForm.vue`
- Modify: `web/src/components/links/LinkListTable.vue`

- [ ] **Step 1: Extract stats page orchestration**

Move range calculation, loading/error handling, and stats data fetching into `useStatsPage.ts`, then let `StatsView.vue` render focused stats components.

- [ ] **Step 2: Split `useLinksPage.ts` by responsibility**

Extract form encoding/decoding and import/export/browser-effect logic into focused link composable helpers so `useLinksPage.ts` becomes a smaller coordinator.

- [ ] **Step 3: Keep route views orchestration-only**

Ensure `StatsView.vue` and `LinksView`-related code paths primarily compose page-level hooks/components rather than owning formatting and browser-side effect logic inline.

- [ ] **Step 4: Run frontend verification**

Run: `cd web && npm run build`
Expected: PASS with smaller views/composables and unchanged user behavior.

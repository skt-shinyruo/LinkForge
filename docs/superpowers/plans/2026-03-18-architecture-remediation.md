# LinkForge Architecture Remediation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the highest-cost architectural inconsistencies in LinkForge without changing product scope: harden backend boundaries, make redirect reads correct in monolith mode, deduplicate shortlink HTTP contracts, split oversized frontend views, and align docs/module structure with reality.

**Architecture:** Keep the modular-monolith shape, but enforce it more consistently. `accounts` keeps its single Maven module for now while gaining ports/adapters internally; redirect keeps its async projectors for warm/recovery paths but no longer relies on them for correctness in monolith mode; shortlink web transport objects move to shared interface-layer mappers/DTOs; frontend behavior moves from page SFCs into smaller services/composables/components; repo docs/modules are updated to match the actual architecture.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, MyBatis, Redis, Maven reactor, Vue 3, TypeScript, Vite.

---

## File/Module Map

### Backend: Accounts boundary hardening
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsTenantStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsUserStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsUserRoleStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsApiKeyStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsTokenIssuer.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsTenantStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsUserStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsUserRoleStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsApiKeyStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/AccountsJwtTokenIssuer.java`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java`

### Backend: Redirect correctness independent of projector timing
- Create: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaSourcePort.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/RedirectCacheSyncPort.java`
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java`
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/redirect/RedirectCacheSyncAdapter.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify/Test: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java`

### Backend: Shortlink controller contract deduplication
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkCreateHttpRequest.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkUpdateHttpRequest.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkPageHttpResponse.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkHttpMapper.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Test: `server/shortlink/interfaces/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkHttpMapperTest.java`

### Frontend: Split oversized view logic and shared page shell
- Create: `web/src/components/AppPageShell.vue`
- Create: `web/src/components/links/LinkCreateForm.vue`
- Create: `web/src/components/links/LinkListTable.vue`
- Create: `web/src/composables/useAppSessionNavigation.ts`
- Create: `web/src/composables/useLinksPage.ts`
- Create: `web/src/services/links.ts`
- Create: `web/src/services/stats.ts`
- Create: `web/src/services/tags.ts`
- Modify: `web/src/services/types.ts`
- Modify: `web/src/views/LinksView.vue`
- Modify: `web/src/views/StatsView.vue`
- Modify: `web/src/views/TagsView.vue`

### Repo/docs/module alignment
- Create: `docs/architecture.md`
- Modify: `README.md`
- Modify: `server/pom.xml`
- Delete: `server/shared-kernel/`
- Delete: `server/shared-kernel/pom.xml`
- Modify: `server/shortlink/domain/pom.xml`
- Modify: `server/redirect/domain/pom.xml`
- Modify: `server/analytics/domain/pom.xml`
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`

---

### Task 1: Harden `accounts` Internal Boundaries

**Files:**
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsTenantStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsUserStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsUserRoleStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsApiKeyStore.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsTokenIssuer.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsTenantStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsUserStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsUserRoleStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsApiKeyStoreMybatisAdapter.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/AccountsJwtTokenIssuer.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java`

- [ ] **Step 1: Write the failing architecture and integration tests**

Run: `cd server && mvn -q -Dtest=ArchitectureTest,AuthPersistenceIntegrationTest test`
Expected: FAIL because `accounts.application` still imports `accounts.infrastructure.*` and the persistence integration test still expects direct mapper constructor dependencies.

- [ ] **Step 2: Introduce `accounts.application.port` interfaces**

Define ports for tenant/user/user-role/api-key persistence and token issuing with methods matching the current application use cases only. Do not leak MyBatis/entity types across the application boundary.

- [ ] **Step 3: Implement minimal infrastructure adapters**

Back the new ports with MyBatis-based adapters and a JWT token issuer adapter, keeping entities/mappers confined to `accounts.infrastructure`.

- [ ] **Step 4: Refactor services onto the new ports**

Replace mapper/entity/JWT-service dependencies in `AuthService`, `UserAdminService`, `ApiKeyService`, and `AccountStatusService` with the new ports while preserving business behavior and Redis caching semantics.

- [ ] **Step 5: Run focused verification**

Run: `cd server && mvn -q -Dtest=ArchitectureTest,AuthPersistenceIntegrationTest test`
Expected: PASS, including the new boundary rule and updated constructor/bean assertions.

---

### Task 2: Make Redirect Reads Correct Without Waiting for Projectors

**Files:**
- Create: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaSourcePort.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/RedirectCacheSyncPort.java`
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java`
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/redirect/RedirectCacheSyncAdapter.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Create: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java`

- [ ] **Step 1: Write failing redirect correctness tests**

Add one unit test for cache-miss authoritative fallback in `RedirectService` and integration coverage proving create/update/archive/delete (and restore for the archived path) become visible without calling `redirectProjector.drain()`.

Run: `cd server && mvn -q -Dtest=RedirectServiceAuthoritativeFallbackTest,ShortLinkCacheAfterCommitIntegrationTest test`
Expected: FAIL because redirect correctness still depends on projector timing.

- [ ] **Step 2: Add authoritative read and cache-sync ports**

Expose an authoritative `LinkMetaSourcePort` for redirect reads and an application-level `RedirectCacheSyncPort` so shortlink writers can evict stale redirect cache entries immediately.

- [ ] **Step 3: Implement monolith correctness path**

Update `RedirectService` to use the authoritative source before falling back to the projector-backed read model when cache misses occur, and update shortlink mutation handlers to evict redirect cache only after successful commit (or via an equivalent rollback-safe hook) so cache state cannot diverge from durable writes.

- [ ] **Step 4: Keep projectors as warm/recovery path**

Do not remove projector jobs; preserve them for recovery/backfill and for split-runtime readiness, but make redirect correctness no longer depend on them in monolith mode.

- [ ] **Step 5: Run focused verification**

Run: `cd server && mvn -q -Dtest=RedirectServiceAuthoritativeFallbackTest,ShortLinkCacheAfterCommitIntegrationTest test`
Expected: PASS, including “no projector drain required” coverage.

---

### Task 3: Deduplicate Shortlink HTTP Contracts and Mapping

**Files:**
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkCreateHttpRequest.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkUpdateHttpRequest.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkPageHttpResponse.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkHttpMapper.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Create: `server/shortlink/interfaces/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkHttpMapperTest.java`

- [ ] **Step 1: Write the failing mapping test**

Add a focused unit test for `ShortLinkHttpMapper` covering create/update request translation and page response construction.

Run: `cd server && mvn -q -pl shortlink/interfaces -Dtest=ShortLinkHttpMapperTest test`
Expected: FAIL because the mapper/DTO layer does not exist yet.

- [ ] **Step 2: Create shared interface-layer DTOs and mapper**

Move duplicated transport records and page response construction into dedicated interface-layer types and mapping helpers.

- [ ] **Step 3: Refactor both controllers to reuse the shared mapper**

Replace the duplicated inline `new ShortLinkService.CreateLinkRequest(...)`, `new ShortLinkService.UpdateLinkRequest(...)`, and page-response assembly in both controllers.

- [ ] **Step 4: Run focused verification**

Run: `cd server && mvn -q -pl shortlink/interfaces -Dtest=ShortLinkHttpMapperTest test`
Expected: PASS with no controller behavior changes.

---

### Task 4: Split Frontend Page Logic into Services, Composables, and Shared Shell

**Files:**
- Create: `web/src/components/AppPageShell.vue`
- Create: `web/src/components/links/LinkCreateForm.vue`
- Create: `web/src/components/links/LinkListTable.vue`
- Create: `web/src/composables/useAppSessionNavigation.ts`
- Create: `web/src/composables/useLinksPage.ts`
- Create: `web/src/services/links.ts`
- Create: `web/src/services/stats.ts`
- Create: `web/src/services/tags.ts`
- Modify: `web/src/views/LinksView.vue`
- Modify: `web/src/views/StatsView.vue`
- Modify: `web/src/views/TagsView.vue`

- [ ] **Step 1: Extract shared transport helpers**

Move link/stats/tag API calls out of views into dedicated `web/src/services/*.ts` modules, keeping response typing in `web/src/services/types.ts`.

- [ ] **Step 2: Extract shared session/navigation shell**

Create `AppPageShell.vue` and `useAppSessionNavigation.ts` so the repeated header/navigation/logout logic is defined once and reused by `LinksView`, `StatsView`, and `TagsView`.

- [ ] **Step 3: Split `LinksView.vue` by responsibility**

Move create/edit/list page state and behavior into `useLinksPage.ts`, and render create/list UI via `LinkCreateForm.vue` and `LinkListTable.vue` so `LinksView.vue` becomes orchestration-only.

- [ ] **Step 4: Run frontend build verification**

Run: `cd web && npm run build`
Expected: PASS with the extracted modules/components wired correctly.

---

### Task 5: Align Repo Structure and Documentation with Reality

**Files:**
- Create: `docs/architecture.md`
- Modify: `README.md`
- Modify: `server/pom.xml`
- Delete: `server/shared-kernel/pom.xml`
- Modify: `server/shortlink/domain/pom.xml`
- Modify: `server/redirect/domain/pom.xml`
- Modify: `server/analytics/domain/pom.xml`
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`

- [ ] **Step 1: Remove the dead `shared-kernel` module**

Delete the empty `server/shared-kernel/` module directory and remove its reactor/dependency-management/domain-module references.

Run: `cd server && mvn -q -pl shortlink/domain,redirect/domain,analytics/domain test`
Expected: PASS, proving those domain modules do not need the deleted artifact.

- [ ] **Step 2: Restore architecture SSOT**

Write `docs/architecture.md` to describe the actual backend module boundaries, redirect monolith correctness path, frontend layering, and the role of `foundation`.

- [ ] **Step 3: Fix repository pointers and guardrail wording**

Update `README.md` to point to a real architecture document and refresh `FoundationSharedArchitectureTest` wording so it reflects the post-remediation structure instead of referencing a dead module as if it were active.

- [ ] **Step 4: Run final repo verification**

Run: `cd server && mvn test -q`
Run: `cd web && npm run build`
Expected: PASS, with repo docs/module structure aligned to the code that actually exists.

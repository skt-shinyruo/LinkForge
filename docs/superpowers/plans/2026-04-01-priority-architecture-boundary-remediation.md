# LinkForge Priority Architecture Boundary Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the highest-cost backend architecture gaps in LinkForge by turning the current directory-level module boundaries into enforceable compile-time and test-time boundaries.

**Architecture:** Keep the modular-monolith deployment shape. Do not split runtimes. Instead, add missing architecture guardrails first, then replace direct cross-context service dependencies with published-language contracts, then make bootstrap/runtime ownership explicit so the codebase matches the declared module model.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, MyBatis, Redis, Maven reactor, ArchUnit.

---

## Scope

This plan intentionally covers only the first two remediation phases:

1. Make the allowed dependency graph explicit and enforceable.
2. Replace the worst direct cross-context dependencies (`shortlink/accounts/analytics -> platform/governance`, `governance -> accounts`) with published contracts and shared security vocabulary.
3. Align `app` bootstrap and `foundation` runtime ownership with the real dependency graph.

Out of scope for this plan:

- splitting `accounts` into submodules
- frontend decomposition
- broad redirect redesign beyond removing dead/duplicated boundary surface

---

## File/Module Map

### Architecture guardrails and bootstrap truth
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/PlatformModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/GovernanceModule.java`
- Create/Test: `server/app/src/test/java/com/linkforge/architecture/AppModuleCompositionTest.java`

### New published contracts
- Create: `server/contracts/platform/pom.xml`
- Create: `server/contracts/platform/src/main/java/com/linkforge/contract/platform/ApplicationScopePort.java`
- Create: `server/contracts/platform/src/main/java/com/linkforge/contract/platform/ApplicationQuotaView.java`
- Create: `server/contracts/governance/pom.xml`
- Create: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalSubmissionPort.java`
- Create: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalRequestView.java`
- Create: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/SensitiveOperation.java`
- Modify: `server/pom.xml`

### Provider-side implementations
- Modify: `server/platform/application/pom.xml`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformApplicationScopeAdapter.java`
- Modify: `server/governance/application/pom.xml`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapter.java`

### Consumer-side refactors
- Modify: `server/accounts/pom.xml`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/shortlink/application/pom.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/migration/LegacyShortLinkBackfillService.java`
- Modify: `server/shortlink/interfaces/pom.xml`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Modify: `server/analytics/interfaces/pom.xml`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`

### Shared security vocabulary and foundation ownership
- Create: `server/foundation/src/main/java/com/linkforge/foundation/security/StandardRoles.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/domain/Roles.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/web/RequestIdFilter.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/persistence/IntegrationEventMybatisConfig.java`

### Redirect boundary cleanup
- Delete/Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/projection/LinkMetaProjectionPort.java`
- Delete/Modify: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionQueryService.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`

### Verification
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/CreateShortLinkCommandHandlerTest.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/UpdateShortLinkCommandHandlerTest.java`
- Modify/Test: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`
- Modify/Test: `server/integration-tests/src/test/java/com/linkforge/platform/ControlPlaneEndToEndIntegrationTest.java`
- Modify: `docs/architecture.md`

---

### Task 1: Freeze the Allowed Dependency Graph Before Refactoring

**Files:**
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Create/Test: `server/app/src/test/java/com/linkforge/architecture/AppModuleCompositionTest.java`
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`

- [ ] **Step 1: Make the current violations visible**

Add/adjust ArchUnit rules so the following are no longer silently allowed:

- `com.linkforge.shortlink..` must not depend on `com.linkforge.platform.application..` or `com.linkforge.governance.application..`
- `com.linkforge.accounts..` must not depend on `com.linkforge.platform.application..`
- `com.linkforge.analytics..` must not depend on `com.linkforge.governance.application..`
- `com.linkforge.governance..` must not depend on `com.linkforge.accounts..`
- `platform` and `governance` must be included in the bounded-context rule set instead of being implicit exceptions

Run: `cd server && mvn -q -pl app -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL with direct dependency violations in:

- `ApiKeyService`
- `CreateShortLinkCommandHandler`
- `UpdateShortLinkCommandHandler`
- `StatsController`
- `GovernanceService`

- [ ] **Step 2: Add a bootstrap composition smoke test**

Create a focused Spring context test that asserts:

- `TenantAdminApplicationController` is present
- `TenantAdminDomainController` is present
- `ApprovalController` is present
- `AuditController` is present

Run: `cd server && mvn -q -pl app -am -Dtest=AppModuleCompositionTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: either FAIL because the current composition is implicit/drifting, or PASS and document the exact mechanism that makes `platform/governance` load today.

- [ ] **Step 3: Make `app` composition explicit**

Whichever way the smoke test behaves, end this step with one explicit composition mechanism only:

- either `LinkForgeApplication` imports `PlatformModule` and `GovernanceModule`
- or the compose wrappers are removed and replaced by one explicit, test-covered bootstrap path

Do not keep a situation where `app/pom.xml` includes modules that `LinkForgeApplication` does not explicitly own.

- [ ] **Step 4: Re-run focused architecture verification**

Run: `cd server && mvn -q -pl app -am -Dtest=ArchitectureTest,AppModuleCompositionTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: still FAIL on cross-context service dependencies, but PASS on composition truth.

---

### Task 2: Publish a `platform` Language Instead of Injecting `PlatformControlPlaneService`

**Files:**
- Create: `server/contracts/platform/pom.xml`
- Create: `server/contracts/platform/src/main/java/com/linkforge/contract/platform/ApplicationScopePort.java`
- Create: `server/contracts/platform/src/main/java/com/linkforge/contract/platform/ApplicationQuotaView.java`
- Modify: `server/pom.xml`
- Modify: `server/platform/application/pom.xml`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformApplicationScopeAdapter.java`
- Modify: `server/accounts/pom.xml`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/shortlink/application/pom.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/migration/LegacyShortLinkBackfillService.java`
- Modify: `server/shortlink/interfaces/pom.xml`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/CreateShortLinkCommandHandlerTest.java`

- [ ] **Step 1: Add the new contract module to the reactor**

Update `server/pom.xml` so `contracts/platform` is a first-class module with dependency-management entries.

Run: `cd server && mvn -q -pl contracts/platform -am test`

Expected: PASS once the new module compiles on its own.

- [ ] **Step 2: Define the narrow `platform` contract**

Create `ApplicationScopePort` with only the use cases consumers currently need:

- `requireApplicationExists(long tenantId, long applicationId)`
- `requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId)`
- `Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId)`

`ApplicationQuotaView` should carry only stable read data used by consumers:

- `applicationId`
- `monthlyLinkLimit`
- `monthlyClickLimit`

Do not expose `PlatformControlPlaneService`, `ApplicationProvisioningService`, or `platform.domain.*` to consumer modules.

- [ ] **Step 3: Implement the provider-side adapter**

Create `PlatformApplicationScopeAdapter` in `platform.application` that implements `ApplicationScopePort` by delegating to repositories and existing provisioning logic.

Keep `PlatformControlPlaneService` available only for `platform`’s own controllers; it should stop being the cross-context entry point.

- [ ] **Step 4: Refactor consumers onto the contract**

Replace `PlatformControlPlaneService` dependencies in:

- `ApiKeyService`
- `CreateShortLinkCommandHandler`
- `ShortLinkController`
- `OpenApiShortLinkController`

Also remove unnecessary `platform.domain` imports from `shortlink.application` so consumers only see the contract module.

- [ ] **Step 5: Re-run focused verification**

Run: `cd server && mvn -q -pl app,accounts,shortlink,platform,contracts/platform -am -Dtest=ArchitectureTest,ApiKeyServiceTest,CreateShortLinkCommandHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `PlatformControlPlaneService` direct-dependency violations disappear; governance-related violations remain.

---

### Task 3: Publish a `governance` Language Instead of Injecting `GovernanceService`

**Files:**
- Create: `server/contracts/governance/pom.xml`
- Create: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalSubmissionPort.java`
- Create: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalRequestView.java`
- Create: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/SensitiveOperation.java`
- Modify: `server/pom.xml`
- Modify: `server/governance/application/pom.xml`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapter.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/shortlink/application/pom.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/analytics/interfaces/pom.xml`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/UpdateShortLinkCommandHandlerTest.java`
- Modify/Test: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`

- [ ] **Step 1: Add the `contracts/governance` module**

Update the Maven reactor and dependency management.

Run: `cd server && mvn -q -pl contracts/governance -am test`

Expected: PASS once the new contract compiles independently.

- [ ] **Step 2: Move approval vocabulary into the contract**

Define:

- `SensitiveOperation` enum for public approval-triggering operation types
- `ApprovalSubmissionPort`
- `ApprovalRequestView`

The contract should contain only the vocabulary external consumers need to request approval and receive a stable response.

- [ ] **Step 3: Implement provider-side translation in `governance.application`**

Create `GovernanceApprovalSubmissionAdapter` that translates:

- contract `SensitiveOperation` -> governance internal operation type
- governance internal DTO -> `ApprovalRequestView`

Keep approval-policy logic, repository access, and audit writes inside `GovernanceService`.

- [ ] **Step 4: Refactor consumers**

Replace `GovernanceService` direct injection with `ApprovalSubmissionPort` in:

- `UpdateShortLinkCommandHandler`
- `StatsController`

The consumer code should no longer construct `GovernanceService.SubmitApprovalRequest` or depend on `governance.domain.*`.

- [ ] **Step 5: Re-run focused verification**

Run: `cd server && mvn -q -pl app,shortlink,analytics,governance,contracts/governance -am -Dtest=ArchitectureTest,UpdateShortLinkCommandHandlerTest,StatsControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: direct `governance.application` dependency violations disappear.

---

### Task 4: Move Shared Role Vocabulary Out of `accounts`

**Files:**
- Create: `server/foundation/src/main/java/com/linkforge/foundation/security/StandardRoles.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/domain/Roles.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/AuthServiceTest.java`
- Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`

- [ ] **Step 1: Introduce one shared role-definition source**

Create `StandardRoles` under `foundation.security` with:

- `USER`
- `TENANT_ADMIN`
- `PLATFORM_ADMIN`
- `OPENAPI`

This vocabulary belongs to auth/runtime, not to the `accounts` bounded context domain model.

- [ ] **Step 2: Convert consumers**

Update `AuthService`, `UserAdminService`, and `GovernanceService` to use `StandardRoles`.

If you keep `accounts.domain.Roles`, make it a deprecated compatibility wrapper that delegates to `StandardRoles`; do not leave `governance` importing it.

- [ ] **Step 3: Re-run focused verification**

Run: `cd server && mvn -q -pl app,accounts,governance,foundation -am -Dtest=ArchitectureTest,AuthServiceTest,UserAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `governance -> accounts` dependency violation disappears.

---

### Task 5: Make `foundation` Shared-Library Boundaries Explicit

**Files:**
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/web/RequestIdFilter.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/persistence/IntegrationEventMybatisConfig.java`
- Modify: `docs/architecture.md`

- [ ] **Step 1: Tighten the foundation guardrail wording and test scope**

Keep the current split between shared-library packages and `foundation.runtime..`, but document it as an explicit architecture rule instead of an implementation workaround.

The test should make it obvious that:

- `foundation.config/id/tx/util` are pure shared-library packages
- `foundation.runtime..` is runtime infrastructure
- new runtime beans must not be added to the shared-library surface

- [ ] **Step 2: Normalize the runtime package story**

Review runtime-only beans already living under `foundation.runtime..` and make their package intent explicit with class-level Javadoc where the ownership is easy to misunderstand.

Start with:

- `RequestIdFilter`
- `IntegrationEventMybatisConfig`

- [ ] **Step 3: Update architecture documentation**

Update `docs/architecture.md` so it no longer describes `foundation` as one undifferentiated shared module. Call out the split between:

- pure shared library packages
- explicit runtime support packages

- [ ] **Step 4: Re-run focused verification**

Run: `cd server && mvn -q -pl foundation,app -am -Dtest=FoundationSharedArchitectureTest,ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with the new explanation and unchanged runtime behavior.

---

### Task 6: Delete Dead Redirect Boundary Surface

**Files:**
- Delete/Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/projection/LinkMetaProjectionPort.java`
- Delete/Modify: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionQueryService.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`

- [ ] **Step 1: Verify the projection query path is not part of the correctness chain**

Confirm via search and focused tests that production redirect resolution uses:

- `LinkCachePort`
- `LinkMetaSourcePort`
- negative cache write on miss

and does not use `LinkMetaProjectionPort`.

Run: `cd server && mvn -q -pl redirect/application,redirect/infrastructure -am -Dtest=RedirectServiceAuthoritativeFallbackTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with no dependency on the projection query abstraction.

- [ ] **Step 2: Remove or inline the dead abstraction**

Delete `LinkMetaProjectionPort` and `RedirectLinkProjectionQueryService` if they are not used after the search.

If you find a non-test usage, replace it with one of the already-declared runtime concepts:

- authoritative source
- projector job
- cache

Do not keep an empty read-model abstraction that is not in the live correctness path.

- [ ] **Step 3: Re-run redirect-focused verification**

Run: `cd server && mvn -q -pl redirect,shortlink,integration-tests -am -Dtest=RedirectServiceAuthoritativeFallbackTest,ShortLinkCacheAfterCommitIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with the same redirect behavior and less architectural dead code.

---

### Task 7: Full Focused Verification and Branch Gate

**Files:**
- No code changes required if previous tasks passed

- [ ] **Step 1: Run the focused architecture suite**

Run: `cd server && mvn -q -pl app,accounts,shortlink,analytics,platform,governance,foundation,contracts/platform,contracts/governance -am -Dtest=ArchitectureTest,AppModuleCompositionTest,FoundationSharedArchitectureTest,ApiKeyServiceTest,CreateShortLinkCommandHandlerTest,UpdateShortLinkCommandHandlerTest,StatsControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

- [ ] **Step 2: Run one cross-context integration slice**

Run: `cd server && mvn -q -pl integration-tests -P it -Dtest=ControlPlaneEndToEndIntegrationTest test`

Expected: PASS, proving the published-language substitutions did not break the control-plane + shortlink + governance workflow.

- [ ] **Step 3: Update architecture docs**

Before merging, ensure `docs/architecture.md` reflects:

- new `contracts/platform`
- new `contracts/governance`
- explicit `app` composition
- explicit `foundation` runtime/shared split

- [ ] **Step 4: Commit in logical slices**

Recommended commit order:

1. `test(architecture): cover platform and governance boundaries`
2. `refactor(platform): publish application scope contract`
3. `refactor(governance): publish approval submission contract`
4. `refactor(security): move shared role vocabulary to foundation`
5. `refactor(app): make module composition explicit`
6. `refactor(redirect): remove dead projection query surface`

---

## Notes for the Implementer

- Do not start by “cleaning up” filenames or package layout unrelated to the dependency graph.
- Prefer new contract modules over widening `contract-api`; `contract-api` should not become a junk drawer.
- Do not expose `platform.domain.*` or `governance.domain.*` to consumer contexts.
- Keep existing HTTP endpoints and runtime deployment shape unchanged.
- If a direct dependency turns out to be needed only inside the provider’s own controllers, leave it there; the target is cross-context code, not provider-internal code.

---

Plan complete and saved to `docs/superpowers/plans/2026-04-01-priority-architecture-boundary-remediation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

# DDD Aggressive Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the aggressive DDD target model from `docs/superpowers/specs/2026-04-30-ddd-aggressive-completion-design.md` without splitting LinkForge out of its modular-monolith runtime.

**Architecture:** Keep the existing Maven bounded-context layout and Spring Boot composition root. Implement the refactor as independent context slices: first strengthen dependency guardrails, then move business rules from procedural application services into domain aggregates, value objects, and domain policies. Keep `analytics` and `redirect` intentionally lightweight policy/read-model contexts rather than forcing aggregate-heavy designs.

**Tech Stack:** Java 17, Spring Boot 3.2, Maven reactor, JUnit 5, AssertJ, Mockito, ArchUnit, MyBatis, Redis adapters behind ports.

---

## Preconditions

- Start from a clean worktree.
- Keep public HTTP behavior stable unless a domain invariant explicitly requires a behavior correction.
- Preserve the single Spring Boot backend runtime.
- Add failing tests before production changes.
- Commit after each task.
- Keep domain modules free of Spring, Servlet/Jakarta runtime APIs, MyBatis, Redis, persistence entities, and foreign bounded-context internals.

## Scope Decomposition

The spec covers multiple bounded contexts, so this is a master implementation plan rather than one enormous code patch. Execute it phase by phase. If a phase grows beyond one day of work, split that phase into a context-specific sub-plan before editing production code.

Execution order:

1. Guardrails and documentation source of truth.
2. Accounts tactical model.
3. Platform domain completion.
4. Shortlink completion.
5. Governance completion.
6. Analytics and redirect boundary hardening.
7. Final documentation and verification.

## File Structure

### Architecture And Documentation

- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
  - Tighten architecture rules that prevent framework coupling, cross-context inner-layer imports, controller-to-infrastructure shortcuts, and contract leakage.
- Modify: `docs/architecture.md`
  - Update the concise source of truth after implementation slices land.
- Reference: `docs/superpowers/specs/2026-04-30-ddd-aggressive-completion-design.md`
  - Detailed target model.

### Accounts

- Create or modify: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/Tenant.java`
- Create or modify: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/AccountUser.java`
- Create or modify: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/ApiKey.java`
- Create or modify: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/RoleAssignment.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/EmailAddress.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/TenantName.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/RoleCode.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/ApiKeyName.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/TokenVersion.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/AccountStatusPolicy.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/RolePolicy.java`
- Create: `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/ApiKeyScopePolicy.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Test: `server/accounts/domain/src/test/java/com/linkforge/accounts/domain/*Test.java`
- Test: existing `server/accounts/application/src/test/java/com/linkforge/accounts/application/*Test.java`

### Platform

- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Application.java`
- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Domain.java`
- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationQuota.java`
- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationPolicy.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationKey.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainHostname.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/MonthlyLinkLimit.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationStatus.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainVerificationState.java`
- Create or modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/QuotaPolicy.java`
- Create or modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationPolicyDecisionService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformApplicationScopeAdapter.java`
- Test: `server/platform/domain/src/test/java/com/linkforge/platform/domain/*Test.java`
- Test: existing `server/platform/application/src/test/java/com/linkforge/platform/application/*Test.java`

### Shortlink

- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Create or modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/Tag.java`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLinkRevision.java`
- Create or modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/RedirectStatusCode.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortCodeAllocationPolicy.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/DestinationChangePolicy.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/LinkTagPolicy.java`
- Create or modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkDestinationChangeRequested.java`
- Create or modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkTagsChanged.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/*CommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutor.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java`
- Test: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/*Test.java`
- Test: existing `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/**/*Test.java`
- Test: existing `server/shortlink/infrastructure/src/test/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactoryTest.java`

### Governance

- Modify: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalRequest.java`
- Modify: `server/governance/domain/src/main/java/com/linkforge/governance/domain/AuditLog.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalDecisionReason.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalActor.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalSnapshot.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalMatrixPolicy.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/SensitiveOperationPolicy.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/AuditPolicy.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalApplicationService.java`
- Test: `server/governance/domain/src/test/java/com/linkforge/governance/domain/*Test.java`
- Test: existing `server/governance/application/src/test/java/com/linkforge/governance/application/*Test.java`

### Analytics And Redirect

- Create: `server/analytics/domain/src/main/java/com/linkforge/analytics/domain/VisitFact.java`
- Create: `server/analytics/domain/src/main/java/com/linkforge/analytics/domain/VisitDimension.java`
- Create: `server/analytics/domain/src/main/java/com/linkforge/analytics/domain/AggregationWindow.java`
- Create: `server/analytics/domain/src/main/java/com/linkforge/analytics/domain/AnalyticsExportRequest.java`
- Create: `server/analytics/domain/src/main/java/com/linkforge/analytics/domain/VisitNormalizationPolicy.java`
- Create: `server/analytics/domain/src/main/java/com/linkforge/analytics/domain/AggregationPolicy.java`
- Create: `server/analytics/domain/src/main/java/com/linkforge/analytics/domain/AnalyticsExportPolicy.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
- Create: `server/redirect/domain/src/main/java/com/linkforge/redirect/domain/RedirectLookupKey.java`
- Create: `server/redirect/domain/src/main/java/com/linkforge/redirect/domain/RedirectDecision.java`
- Create or modify: `server/redirect/domain/src/main/java/com/linkforge/redirect/domain/RedirectAvailabilityPolicy.java`
- Create: `server/redirect/domain/src/main/java/com/linkforge/redirect/domain/RedirectRiskPolicy.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Test: `server/analytics/domain/src/test/java/com/linkforge/analytics/domain/*Test.java`
- Test: existing `server/analytics/application/src/test/java/com/linkforge/analytics/application/*Test.java`
- Test: `server/redirect/domain/src/test/java/com/linkforge/redirect/domain/*Test.java`
- Test: existing `server/redirect/application/src/test/java/com/linkforge/redirect/application/*Test.java`

---

### Task 1: Lock Aggressive DDD Guardrails

**Files:**

- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

- [ ] **Step 1: Add source-level and ArchUnit guard tests**

Add tests that lock the aggressive DDD boundary before context refactors begin:

```java
@Test
void contracts_should_not_depend_on_bounded_context_inner_layers() {
    ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("com.linkforge.contract..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.linkforge.accounts.domain..",
                    "com.linkforge.accounts.application..",
                    "com.linkforge.accounts.infrastructure..",
                    "com.linkforge.accounts.interfaces..",
                    "com.linkforge.shortlink.domain..",
                    "com.linkforge.shortlink.application..",
                    "com.linkforge.shortlink.infrastructure..",
                    "com.linkforge.shortlink.interfaces..",
                    "com.linkforge.redirect.domain..",
                    "com.linkforge.redirect.application..",
                    "com.linkforge.redirect.infrastructure..",
                    "com.linkforge.redirect.interfaces..",
                    "com.linkforge.analytics.domain..",
                    "com.linkforge.analytics.application..",
                    "com.linkforge.analytics.infrastructure..",
                    "com.linkforge.analytics.interfaces..",
                    "com.linkforge.platform.domain..",
                    "com.linkforge.platform.application..",
                    "com.linkforge.platform.infrastructure..",
                    "com.linkforge.platform.interfaces..",
                    "com.linkforge.governance.domain..",
                    "com.linkforge.governance.application..",
                    "com.linkforge.governance.infrastructure..",
                    "com.linkforge.governance.interfaces.."
            );
    rule.check(CLASSES);
}

@Test
void controllers_should_not_depend_on_mappers_repositories_or_infrastructure_adapters() {
    ArchRule rule = noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .areAnnotatedWith(Controller.class)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..infrastructure..",
                    "..persistence.mapper..",
                    "..persistence.repository..",
                    "..repo.."
            );
    rule.check(CLASSES);
}
```

- [ ] **Step 2: Run focused architecture tests**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS. If it fails, the failure identifies an existing boundary leak; fix the leak or narrow the new rule only if the dependency is a deliberate same-context runtime module import.

- [ ] **Step 3: Commit guardrails**

Run:

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "test: tighten aggressive DDD architecture guardrails"
```

Expected: Commit succeeds.

---

### Task 2: Plan And Implement Accounts Tactical Model

**Files:**

- Create/Modify: accounts domain files listed in the Accounts file structure section.
- Modify: `AuthService`, `ApiKeyService`, `AccountStatusService`, `UserAdminService`.
- Test: accounts domain and application tests.

- [ ] **Step 1: Create the accounts domain test suite first**

Create focused tests before production code:

```java
// server/accounts/domain/src/test/java/com/linkforge/accounts/domain/EmailAddressTest.java
package com.linkforge.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void of_shouldNormalizeEmail() {
        assertThat(EmailAddress.of("  MEMBER@Example.COM ").value())
                .isEqualTo("member@example.com");
    }

    @Test
    void of_shouldRejectBlankEmail() {
        assertThatThrownBy(() -> EmailAddress.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }
}
```

```java
// server/accounts/domain/src/test/java/com/linkforge/accounts/domain/AccountStatusPolicyTest.java
package com.linkforge.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountStatusPolicyTest {

    private final AccountStatusPolicy policy = new AccountStatusPolicy();

    @Test
    void canAuthenticate_shouldAllowActiveTenantAndActiveUser() {
        Tenant tenant = Tenant.rehydrate(10L, TenantName.of("Tenant A"), AccountsConstants.STATUS_ACTIVE);
        AccountUser user = AccountUser.rehydrate(20L, 10L, EmailAddress.of("user@example.com"), AccountsConstants.STATUS_ACTIVE, TokenVersion.initial());

        assertThat(policy.canAuthenticate(tenant, user)).isTrue();
    }

    @Test
    void canAuthenticate_shouldRejectDisabledTenant() {
        Tenant tenant = Tenant.rehydrate(10L, TenantName.of("Tenant A"), "DISABLED");
        AccountUser user = AccountUser.rehydrate(20L, 10L, EmailAddress.of("user@example.com"), AccountsConstants.STATUS_ACTIVE, TokenVersion.initial());

        assertThat(policy.canAuthenticate(tenant, user)).isFalse();
    }
}
```

- [ ] **Step 2: Run accounts domain tests and confirm they fail**

Run:

```bash
cd server && mvn -q -pl accounts/domain -am -Dtest=EmailAddressTest,AccountStatusPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `EmailAddress`, `Tenant`, `TenantName`, `AccountUser`, `TokenVersion`, and `AccountStatusPolicy` do not exist yet or do not expose the planned methods.

- [ ] **Step 3: Implement minimal accounts domain types**

Create the value objects and aggregate shells needed by the tests. Keep all classes in `server/accounts/domain/src/main/java/com/linkforge/accounts/domain/` and avoid Spring annotations.

Required public API:

- `EmailAddress.of(String raw)` trims and lower-cases valid emails, rejects blank values, and exposes `value()`.
- `TenantName.of(String raw)` trims tenant names, rejects blank values, and exposes `value()`.
- `TokenVersion.initial()` returns version `0`; `incremented()` returns a new value with `value() + 1`.
- `Tenant.rehydrate(long id, TenantName name, String status)` preserves existing persisted state and exposes `active()`.
- `AccountUser.rehydrate(long id, long tenantId, EmailAddress email, String status, TokenVersion tokenVersion)` preserves existing persisted state and exposes `active()` and `logout()`.
- `AccountUser.logout()` returns a user state with incremented token version.
- `AccountStatusPolicy.canAuthenticate(Tenant tenant, AccountUser user)` returns true only when tenant and user are active.

- [ ] **Step 4: Run accounts domain tests**

Run:

```bash
cd server && mvn -q -pl accounts/domain -am -Dtest=EmailAddressTest,AccountStatusPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Refactor accounts application services one use case at a time**

Move status and token-version decisions out of application code:

- `AuthService.login(String email, String rawPassword)` loads store data, rehydrates `Tenant` and `AccountUser`, calls `AccountStatusPolicy.canAuthenticate(Tenant, AccountUser)`, then uses hasher/token ports.
- `AuthService.logout(long userId)` rehydrates `AccountUser`, calls `logout()`, persists the incremented token version, then evicts cache.
- `ApiKeyService` rehydrates `ApiKey` and delegates revocation/rotation/status checks to the aggregate or `ApiKeyScopePolicy`.
- `AccountStatusService` uses `AccountStatusPolicy`.
- `UserAdminService` uses `AccountUser` lifecycle methods for enable/disable.

- [ ] **Step 6: Run focused accounts tests**

Run:

```bash
cd server && mvn -q -pl accounts/domain,accounts/application -am -Dtest=EmailAddressTest,AccountStatusPolicyTest,AuthServiceTest,ApiKeyServiceTest,AccountStatusServiceTest,UserAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit accounts slice**

Run:

```bash
git add server/accounts/domain server/accounts/application
git commit -m "refactor: introduce accounts tactical domain model"
```

Expected: Commit succeeds.

---

### Task 3: Plan And Implement Platform Domain Completion

**Files:**

- Create/Modify: platform domain files listed in the Platform file structure section.
- Modify: platform application services listed above.

- [ ] **Step 1: Create platform domain tests first**

Create tests for value objects and policies:

```java
// server/platform/domain/src/test/java/com/linkforge/platform/domain/DomainHostnameTest.java
package com.linkforge.platform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainHostnameTest {

    @Test
    void of_shouldNormalizeHostname() {
        assertThat(DomainHostname.of(" EXAMPLE.COM ").value()).isEqualTo("example.com");
    }

    @Test
    void of_shouldRejectBlankHostname() {
        assertThatThrownBy(() -> DomainHostname.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostname");
    }
}
```

```java
// server/platform/domain/src/test/java/com/linkforge/platform/domain/ApplicationQuotaTest.java
package com.linkforge.platform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationQuotaTest {

    @Test
    void create_shouldAllowUnlimitedMonthlyLinksWithZeroLimit() {
        ApplicationQuota quota = ApplicationQuota.create(10L, 20L, MonthlyLinkLimit.unlimited());

        assertThat(quota.monthlyLinkLimit()).isZero();
    }

    @Test
    void create_shouldRejectNegativeMonthlyLimit() {
        assertThatThrownBy(() -> MonthlyLinkLimit.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monthlyLinkLimit");
    }
}
```

- [ ] **Step 2: Run platform domain tests and confirm they fail**

Run:

```bash
cd server && mvn -q -pl platform/domain -am -Dtest=DomainHostnameTest,ApplicationQuotaTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because value objects and behavior-owning `ApplicationQuota` APIs are missing.

- [ ] **Step 3: Implement platform value objects and domain behavior**

Implement:

- `ApplicationKey.of(String raw)` trims application keys, rejects blank values, and preserves existing case semantics unless current API tests require lower-casing.
- `DomainHostname.of(String raw)` trims and lower-cases hostnames, rejects blank values, and exposes `value()`.
- `MonthlyLinkLimit.unlimited()` returns `0`; `MonthlyLinkLimit.of(long value)` rejects negative values and exposes `value()`.

Update `Application`, `Domain`, `ApplicationQuota`, and `ApplicationPolicy` so new construction paths validate through value objects while preserving existing constructor/accessor compatibility needed by mappers.

- [ ] **Step 4: Refactor platform application services**

Move normalization and invariant checks from `ApplicationProvisioningService` and `PlatformControlPlaneService` into domain types and policies:

- application key validation through `ApplicationKey`
- hostname normalization through `DomainHostname`
- quota validation through `MonthlyLinkLimit`
- domain authorization through `DomainAuthorizationPolicy`
- approval gating through `ApplicationPolicyDecisionService`

- [ ] **Step 5: Run focused platform tests**

Run:

```bash
cd server && mvn -q -pl platform/domain,platform/application -am -Dtest=DomainHostnameTest,ApplicationQuotaTest,DomainAuthorizationPolicyTest,PlatformApplicationScopeAdapterTest,ApplicationProvisioningServiceTest,PlatformControlPlaneServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS. If a listed test class does not exist, keep `-Dsurefire.failIfNoSpecifiedTests=false` and add a focused test for the service being changed before continuing.

- [ ] **Step 6: Commit platform slice**

Run:

```bash
git add server/platform/domain server/platform/application
git commit -m "refactor: complete platform domain model"
```

Expected: Commit succeeds.

---

### Task 4: Plan And Implement Shortlink Completion

**Files:**

- Create/Modify: shortlink files listed in the Shortlink file structure section.

- [ ] **Step 1: Add shortlink domain tests for remaining policies**

Create tests for redirect status, tag policy, destination-change policy, and domain event origin:

```java
// server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/RedirectStatusCodeTest.java
package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedirectStatusCodeTest {

    @Test
    void of_shouldAllow301And302() {
        assertThat(RedirectStatusCode.of(301).value()).isEqualTo(301);
        assertThat(RedirectStatusCode.of(302).value()).isEqualTo(302);
    }

    @Test
    void of_shouldRejectUnsupportedStatus() {
        assertThatThrownBy(() -> RedirectStatusCode.of(307))
                .isInstanceOf(ShortLinkDomainException.class);
    }
}
```

- [ ] **Step 2: Run shortlink domain tests and confirm they fail**

Run:

```bash
cd server && mvn -q -pl shortlink/domain -am -Dtest=RedirectStatusCodeTest,ShortLinkTest,QueryForwardAllowlistTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL for the newly introduced `RedirectStatusCode` API until implemented.

- [ ] **Step 3: Implement shortlink completion types and policies**

Implement:

- `RedirectStatusCode`
- `ShortCodeAllocationPolicy`
- `DestinationChangePolicy`
- `LinkTagPolicy`
- `ShortLinkDestinationChangeRequested`
- `ShortLinkTagsChanged`

Keep collision checks behind application ports; domain policies can express decisions but cannot call repositories directly.

- [ ] **Step 4: Refactor shortlink command handlers**

Update command handlers so:

- create/update/archive/restore/delete mutation facts originate from aggregate methods
- tag assignment uses `LinkTagPolicy`
- destination change decisions use `DestinationChangePolicy`
- application handlers translate domain exceptions and persist aggregates
- `ShortLinkDomainEventDispatcher` translates internal events to integration publishers

- [ ] **Step 5: Run focused shortlink tests**

Run:

```bash
cd server && mvn -q -pl shortlink/domain,shortlink/application,shortlink/infrastructure -am -Dtest=RedirectStatusCodeTest,ShortLinkTest,ShortLinkEventFactoryTest,CreateShortLinkCommandHandlerTest,UpdateShortLinkCommandHandlerTest,SetLinkTagsCommandHandlerTest,LinkDestinationChangeApprovalExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit shortlink slice**

Run:

```bash
git add server/shortlink/domain server/shortlink/application server/shortlink/infrastructure
git commit -m "refactor: complete shortlink tactical DDD model"
```

Expected: Commit succeeds.

---

### Task 5: Plan And Implement Governance Completion

**Files:**

- Create/Modify: governance files listed in the Governance file structure section.

- [ ] **Step 1: Add governance domain tests first**

Create or extend `ApprovalRequestTest`:

```java
// server/governance/domain/src/test/java/com/linkforge/governance/domain/ApprovalRequestTest.java
package com.linkforge.governance.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalRequestTest {

    @Test
    void approve_shouldRejectSelfApproval() {
        ApprovalRequest request = pendingRequest(100L);

        assertThatThrownBy(() -> request.approve(100L, "owner@example.com", "ok", LocalDateTime.parse("2026-04-30T10:00:00")))
                .isInstanceOf(ApprovalDomainException.class);
    }

    @Test
    void markExecuted_shouldRequireApprovedStatus() {
        ApprovalRequest request = pendingRequest(100L);

        assertThatThrownBy(() -> request.markExecuted(LocalDateTime.parse("2026-04-30T10:00:00")))
                .isInstanceOf(ApprovalDomainException.class);
    }

    private static ApprovalRequest pendingRequest(long requestedByUserId) {
        return new ApprovalRequest(
                1L,
                10L,
                SensitiveOperationType.LINK_DESTINATION_CHANGE,
                20L,
                requestedByUserId,
                "owner@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                "{}",
                "{}",
                LocalDateTime.parse("2026-04-30T09:00:00"),
                null,
                null
        );
    }
}
```

- [ ] **Step 2: Run governance domain tests**

Run:

```bash
cd server && mvn -q -pl governance/domain -am -Dtest=ApprovalRequestTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS if the current aggregate already covers these cases; otherwise FAIL and implement the missing behavior.

- [ ] **Step 3: Add governance policy tests**

Add tests that cover these exact governance domain cases:

- `ApprovalMatrixPolicy`
- `SensitiveOperationPolicy`
- `AuditPolicy`
- `ApprovalDecisionReason`
- `ApprovalActor`
- `ApprovalSnapshot`

Each test should assert a named business decision, not persistence details.

- [ ] **Step 4: Implement governance policies and value objects**

Implement policy classes in `server/governance/domain/src/main/java/com/linkforge/governance/domain/`. Keep executor lookup and contract invocation in application services.

- [ ] **Step 5: Refactor governance application services**

Update:

- `GovernanceService.approveRequest(long tenantId, long requestId, String reason, UserActor actor, LocalDateTime requestedAt)` to call aggregate behavior and policy classes before conditional repository updates.
- `GovernanceApprovalApplicationService` to remain an application-facing adapter over governance use cases and published contract ports.

- [ ] **Step 6: Run focused governance tests**

Run:

```bash
cd server && mvn -q -pl governance/domain,governance/application -am -Dtest=ApprovalRequestTest,GovernanceServiceTest,GovernanceApprovalApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit governance slice**

Run:

```bash
git add server/governance/domain server/governance/application
git commit -m "refactor: complete governance approval domain model"
```

Expected: Commit succeeds.

---

### Task 6: Plan And Implement Analytics And Redirect Boundary Hardening

**Files:**

- Create/Modify: analytics and redirect files listed above.

- [ ] **Step 1: Add analytics domain policy tests**

Add analytics tests that cover these exact cases:

- `VisitFact`
- `VisitDimension`
- `AggregationWindow`
- `VisitNormalizationPolicy`
- `AggregationPolicy`
- `AnalyticsExportPolicy`

Example:

```java
// server/analytics/domain/src/test/java/com/linkforge/analytics/domain/AggregationWindowTest.java
package com.linkforge.analytics.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationWindowTest {

    @Test
    void of_shouldAcceptForwardWindow() {
        AggregationWindow window = AggregationWindow.of(
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z")
        );

        assertThat(window.toExclusive()).isAfter(window.fromInclusive());
    }

    @Test
    void of_shouldRejectBackwardWindow() {
        assertThatThrownBy(() -> AggregationWindow.of(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Add redirect policy tests**

Add redirect tests that cover these exact cases:

- `RedirectLookupKey`
- `RedirectDecision`
- `RedirectAvailabilityPolicy`
- `RedirectRiskPolicy`

Keep tests pure; do not require Redis, HTTP, or MyBatis.

- [ ] **Step 3: Run analytics and redirect domain tests and confirm expected failures**

Run:

```bash
cd server && mvn -q -pl analytics/domain,redirect/domain -am -Dtest=AggregationWindowTest,RedirectLookupKeyTest,RedirectDecisionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL for newly planned classes until implemented.

- [ ] **Step 4: Implement lightweight domain policy objects**

Implement analytics and redirect domain types without introducing persistence dependencies. Keep read models private and contract enrichment in application.

- [ ] **Step 5: Refactor application services to use policies**

Update:

- `AnalyticsVisitEventService`
- `AnalyticsReportingApplicationService`
- `AnalyticsExportRequestService`
- `RedirectService`

The goal is to make policy decisions testable without Redis, HTTP, or SQL.

- [ ] **Step 6: Run focused analytics and redirect tests**

Run:

```bash
cd server && mvn -q -pl analytics/domain,analytics/application,redirect/domain,redirect/application -am -Dtest=AggregationWindowTest,AnalyticsVisitEventServiceTest,AnalyticsReportingApplicationServiceTest,AnalyticsExportRequestServiceTest,RedirectServiceAuthoritativeFallbackTest,RedirectServiceTimezoneTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit analytics/redirect slice**

Run:

```bash
git add server/analytics/domain server/analytics/application server/redirect/domain server/redirect/application
git commit -m "refactor: harden analytics and redirect domain boundaries"
```

Expected: Commit succeeds.

---

### Task 7: Update Architecture Documentation

**Files:**

- Modify: `docs/architecture.md`

- [ ] **Step 1: Update DDD context map and tactical rules**

Update `docs/architecture.md` so it records:

- aggregate ownership for `accounts`, `platform`, `shortlink`, and `governance`
- lightweight policy/read-model ownership for `analytics` and `redirect`
- published contract rules
- internal domain events versus integration events
- application facade compatibility rules

- [ ] **Step 2: Review docs diff**

Run:

```bash
git diff -- docs/architecture.md
```

Expected: Diff contains documentation only and matches the implemented model.

- [ ] **Step 3: Commit docs**

Run:

```bash
git add docs/architecture.md
git commit -m "docs: document completed DDD model"
```

Expected: Commit succeeds.

---

### Task 8: Final Verification

**Files:**

- No production edits unless verification exposes a concrete bug.

- [ ] **Step 1: Run aggressive DDD focused verification**

Run:

```bash
cd server && mvn -q -pl app,accounts/domain,accounts/application,platform/domain,platform/application,shortlink/domain,shortlink/application,shortlink/infrastructure,governance/domain,governance/application,analytics/domain,analytics/application,redirect/domain,redirect/application -am -Dtest=ArchitectureTest,EmailAddressTest,AccountStatusPolicyTest,DomainHostnameTest,ApplicationQuotaTest,DomainAuthorizationPolicyTest,RedirectStatusCodeTest,ShortLinkTest,ShortLinkEventFactoryTest,ApprovalRequestTest,AggregationWindowTest,AuthServiceTest,ApiKeyServiceTest,AccountStatusServiceTest,PlatformApplicationScopeAdapterTest,CreateShortLinkCommandHandlerTest,UpdateShortLinkCommandHandlerTest,GovernanceServiceTest,GovernanceApprovalApplicationServiceTest,AnalyticsVisitEventServiceTest,AnalyticsReportingApplicationServiceTest,AnalyticsExportRequestServiceTest,RedirectServiceAuthoritativeFallbackTest,RedirectServiceTimezoneTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 2: Run full backend tests**

Run:

```bash
cd server && mvn -q test
```

Expected: PASS.

- [ ] **Step 3: Check final git status**

Run:

```bash
git status --short
```

Expected: no uncommitted files.

- [ ] **Step 4: Summarize completion**

Report:

```text
Completed aggressive DDD plan: architecture guardrails are tightened, accounts/platform/shortlink/governance now own tactical domain rules in domain code, analytics/redirect expose lightweight policy models, cross-context collaboration stays behind contracts, and backend verification passes.
```

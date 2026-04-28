# DDD Remaining Aggressive Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining DDD refactor design phases before creating one PR.

**Architecture:** Keep LinkForge as one modular monolith, but make the remaining DDD boundaries explicit and enforced. Non-shortlink contexts use `contract-shortlink` only. `app/security` depends on foundation-published security ports instead of accounts internals. Platform, governance, and analytics each gain one concrete domain/application boundary split that moves business rules away from broad facades.

**Tech Stack:** Java 17, Spring Boot 3.2, Maven reactor, JUnit 5, AssertJ, Mockito, ArchUnit.

---

## Preconditions

- Execute from `.worktrees/ddd-baseline-shortlink-pilot`.
- Keep existing HTTP APIs stable.
- Continue TDD: add failing tests before production code.
- Commit after each phase task.
- Keep DDD boundaries explicit: domain code cannot depend on Spring, persistence, servlet, or other bounded-context internals.

## File Structure

### Phase 3: Shortlink Published Contracts

- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
  - Add explicit non-shortlink import guard against shortlink inner layers.
- Create: `server/shortlink/infrastructure/src/test/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactoryTest.java`
  - Verify integration event payloads are mapped to `contract-shortlink` snapshots.

### Phase 4: Accounts Security Export

- Create: `server/foundation/core/src/main/java/com/linkforge/foundation/security/JwtPrincipalVerifier.java`
- Create: `server/foundation/core/src/main/java/com/linkforge/foundation/security/ApiKeyAuthenticator.java`
- Create: `server/foundation/core/src/main/java/com/linkforge/foundation/security/ApiKeyAuthenticationResult.java`
- Create: `server/foundation/core/src/main/java/com/linkforge/foundation/security/ApiKeyAuthenticationException.java`
- Create: `server/foundation/core/src/main/java/com/linkforge/foundation/security/AccountStatusVerifier.java`
- Modify: `server/accounts/infrastructure/src/main/java/com/linkforge/accounts/infrastructure/security/JwtService.java`
  - Implement `JwtPrincipalVerifier`.
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
  - Implement `ApiKeyAuthenticator` through `authenticateApiKey(...)`.
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
  - Implement `AccountStatusVerifier`.
- Modify: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Modify tests under `server/app/src/test/java/com/linkforge/app/security`.
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
  - Replace frozen-debt app/security guard with hard prohibition.

New foundation security exports:

```java
package com.linkforge.foundation.security;

import com.linkforge.contract.api.AppErrorCode;

public interface JwtPrincipalVerifier {
    AuthPrincipal parseToken(String token);
}

public interface AccountStatusVerifier {
    void requireActiveTenant(long tenantId);
    void requireActiveUserAndTenant(long userId, long tenantId);
    void requireActiveUserAndTenant(long userId, long tenantId, int tokenVersion);
}

public interface ApiKeyAuthenticator {
    ApiKeyAuthenticationResult authenticateApiKey(String apiKey);
}

public record ApiKeyAuthenticationResult(long tenantId, Long applicationId, long apiKeyId) {
}

public class ApiKeyAuthenticationException extends RuntimeException {
    private final AppErrorCode errorCode;

    public ApiKeyAuthenticationException(AppErrorCode errorCode) {
        super(errorCode == null ? null : errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppErrorCode errorCode() {
        return errorCode;
    }
}
```

Existing accounts services stay the implementation owner:

```java
public class JwtService implements JwtPrincipalVerifier { ... }
public class AccountStatusService implements AccountStatusVerifier { ... }
public class ApiKeyService implements ApiKeyAuthenticator {
    @Override
    public ApiKeyAuthenticationResult authenticateApiKey(String apiKey) {
        ApiKeyAuthResult r = authenticate(apiKey);
        return new ApiKeyAuthenticationResult(r.tenantId(), r.applicationId(), r.apiKeyId());
    }
}
```

### Phase 5A: Platform Domain Hardening

- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainAuthorizationPolicy.java`
- Create: `server/platform/domain/src/test/java/com/linkforge/platform/domain/DomainAuthorizationPolicyTest.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
  - Delegate domain-use decision to domain policy.

Domain policy shape:

```java
package com.linkforge.platform.domain;

public class DomainAuthorizationPolicy {
    public void requireApplicationCanUseDomain(
            long applicationId,
            Domain domain,
            boolean sharedDomainAuthorized
    ) {
        if (domain.scope() == DomainScope.APPLICATION_DEDICATED
                && (domain.applicationId() == null || domain.applicationId() != applicationId)) {
            throw new DomainAuthorizationException(DomainAuthorizationException.Reason.DEDICATED_DOMAIN_MISMATCH);
        }
        if (domain.scope() != DomainScope.APPLICATION_DEDICATED && !sharedDomainAuthorized) {
            throw new DomainAuthorizationException(DomainAuthorizationException.Reason.SHARED_DOMAIN_NOT_AUTHORIZED);
        }
    }
}
```

The application layer translates domain reasons to existing `BusinessException` messages so API behavior remains stable.

### Phase 5B: Governance Aggregate Hardening

- Modify: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalRequest.java`
  - Convert record to behavior-rich class with `approve(...)` and `markExecuted(...)`.
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalDomainException.java`
- Create: `server/governance/domain/src/test/java/com/linkforge/governance/domain/ApprovalRequestTest.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
  - Use aggregate behavior before repository state transitions.

Aggregate behavior shape:

```java
public ApprovalRequest approve(
        long approverUserId,
        String approverEmail,
        String decisionReason,
        LocalDateTime decidedAt
);

public ApprovalRequest markExecuted(LocalDateTime executedAt);
```

`approve(...)` rejects non-pending requests and self-approval. `markExecuted(...)` rejects any request that is not approved. Both methods return a new immutable `ApprovalRequest` instance with the same accessor API used by repositories.

### Phase 5C: Analytics Boundary Hardening

- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsLinkSummaryEnricher.java`
- Create: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsLinkSummaryEnricherTest.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`
  - Delegate shortlink contract enrichment to the dedicated application component.

Enricher shape:

```java
@Service
public class AnalyticsLinkSummaryEnricher {
    public List<TopLinkStat> enrich(long tenantId, List<TopLinkStat> rows) { ... }
}
```

`AnalyticsReportingApplicationService` depends on the enricher, while the enricher owns the `ShortLinkReadPort` contract dependency and fallback behavior for deleted/missing shortlinks.

---

## Task 1: Lock Shortlink Published Contract Boundary

- [ ] Add failing architecture test `non_shortlink_contexts_should_depend_on_shortlink_contracts_only`.
- [ ] Add failing `ShortLinkEventFactoryTest` for created and archived event snapshot mapping.
- [ ] Implement minimal test support if needed.
- [ ] Run:

```bash
cd server && mvn -q -pl app,shortlink/infrastructure -am -Dtest=ArchitectureTest,ShortLinkEventFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Commit:

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java \
  server/shortlink/infrastructure/src/test/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactoryTest.java
git commit -m "test: lock shortlink published contract boundary"
```

## Task 2: Export Accounts Security Ports

- [ ] Add failing app/security constructor tests proving filters/config use foundation security ports, not accounts classes.
- [ ] Add foundation security interfaces and records.
- [ ] Implement accounts adapters by having existing services implement the new ports.
- [ ] Refactor `JwtAuthenticationFilter`, `ApiKeyAuthenticationFilter`, and `SecurityConfig`.
- [ ] Update app/security tests.
- [ ] Harden `ArchitectureTest.app_security_source_should_not_gain_new_accounts_internal_imports()` into a no-exceptions guard.
- [ ] Run:

```bash
cd server && mvn -q -pl app,accounts/application,accounts/infrastructure -am -Dtest=ArchitectureTest,JwtAuthenticationFilterTest,ApiKeyAuthenticationFilterTest,SecurityConfigCsrfTest,ApiKeyServiceTest,AccountStatusServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Commit:

```bash
git add server/foundation/core/src/main/java/com/linkforge/foundation/security \
  server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyService.java \
  server/accounts/application/src/main/java/com/linkforge/accounts/application/AccountStatusService.java \
  server/accounts/infrastructure/src/main/java/com/linkforge/accounts/infrastructure/security/JwtService.java \
  server/app/src/main/java/com/linkforge/app/security \
  server/app/src/test/java/com/linkforge/app/security \
  server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "refactor: publish accounts security exports"
```

## Task 3: Move Platform Domain Authorization Into Domain Policy

- [ ] Add failing `DomainAuthorizationPolicyTest`.
- [ ] Implement `DomainAuthorizationPolicy`.
- [ ] Refactor `PlatformControlPlaneService.requireApplicationAndDomainAuthorized(...)` to call the policy.
- [ ] Run:

```bash
cd server && mvn -q -pl platform/domain,platform/application -am -Dtest=DomainAuthorizationPolicyTest,PlatformApplicationScopeAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Commit:

```bash
git add server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainAuthorizationPolicy.java \
  server/platform/domain/src/test/java/com/linkforge/platform/domain/DomainAuthorizationPolicyTest.java \
  server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java
git commit -m "refactor: move platform domain authorization policy to domain"
```

## Task 4: Move Governance Approval Transitions Into Aggregate

- [ ] Add failing `ApprovalRequestTest` for approve, self-approval rejection, non-pending rejection, and executed transition.
- [ ] Add `ApprovalDomainException`.
- [ ] Convert `ApprovalRequest` from record to class with same constructor/accessors plus behavior methods.
- [ ] Refactor `GovernanceService.approveRequest(...)` to call aggregate behavior before repository conditional updates.
- [ ] Run:

```bash
cd server && mvn -q -pl governance/domain,governance/application -am -Dtest=ApprovalRequestTest,GovernanceServiceTest,GovernanceApprovalApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Commit:

```bash
git add server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalRequest.java \
  server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalDomainException.java \
  server/governance/domain/src/test/java/com/linkforge/governance/domain/ApprovalRequestTest.java \
  server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java
git commit -m "refactor: move governance approval transitions to aggregate"
```

## Task 5: Isolate Analytics Shortlink Enrichment Boundary

- [ ] Add failing `AnalyticsLinkSummaryEnricherTest`.
- [ ] Implement `AnalyticsLinkSummaryEnricher`.
- [ ] Refactor `AnalyticsReportingApplicationService` to delegate enrichment.
- [ ] Run:

```bash
cd server && mvn -q -pl analytics/application -am -Dtest=AnalyticsLinkSummaryEnricherTest,AnalyticsReportingApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Commit:

```bash
git add server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsLinkSummaryEnricher.java \
  server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java \
  server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsLinkSummaryEnricherTest.java
git commit -m "refactor: isolate analytics shortlink enrichment"
```

## Task 6: Final Verification And PR

- [ ] Run focused DDD verification:

```bash
cd server && mvn -q -pl app,shortlink/infrastructure,accounts/application,accounts/infrastructure,platform/domain,platform/application,governance/domain,governance/application,analytics/application -am -Dtest=ArchitectureTest,ShortLinkEventFactoryTest,JwtAuthenticationFilterTest,ApiKeyAuthenticationFilterTest,SecurityConfigCsrfTest,ApiKeyServiceTest,AccountStatusServiceTest,DomainAuthorizationPolicyTest,PlatformApplicationScopeAdapterTest,ApprovalRequestTest,GovernanceServiceTest,GovernanceApprovalApplicationServiceTest,AnalyticsLinkSummaryEnricherTest,AnalyticsReportingApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Run full backend tests:

```bash
cd server && mvn -q test
```

- [ ] Check:

```bash
git status --short
```

- [ ] Push branch and create PR with `gh pr create`.

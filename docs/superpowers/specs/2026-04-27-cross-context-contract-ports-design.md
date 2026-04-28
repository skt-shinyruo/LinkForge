# Cross-Context Contract Ports Design

**Date:** 2026-04-27

## Problem Statement

LinkForge is a single-runtime modular monolith, but several bounded contexts now compile directly against other contexts' `application` modules:

- `redirect/application` depends on `linkforge-analytics-application` and `linkforge-shortlink-application`.
- `analytics/application` depends on `linkforge-shortlink-application`.
- `RedirectService` imports `AnalyticsVisitEventService`, `ApplicationClickUsagePort`, and `ShortLinkReadService` from other application packages.
- `AnalyticsExportRequestService` and `AnalyticsReportingApplicationService` import `ShortLinkReadService`.

The runtime behavior is intentional: redirect still needs synchronous authoritative link reads, visit recording, and quota checks inside the monolith. The problem is the compile-time direction. A consumer bounded context should depend on a published contract owned by the provider, not on the provider's application service package.

The existing ArchUnit guard only blocks cross-context dependencies into `domain`, `infrastructure`, `interfaces`, and `application.port`. It does not block dependencies on another context's `application` root package, so these edges can be reintroduced silently.

## Constraints

- Keep the deployment model as one Spring Boot application.
- Keep synchronous in-process calls for redirect correctness, analytics visit recording, and quota checks.
- Do not introduce HTTP/RPC adapters, message-only consistency, or service decomposition for this change.
- Contract modules must remain framework-light and must not depend on Spring, Servlet/Jakarta runtime APIs, persistence APIs, or transport-specific types.
- Redirect cache behavior and authoritative fallback behavior must remain unchanged.
- Visit recording must preserve current analytics dimensions: timestamp, tenant, link, application, domain, code, original URL, visit context, and tracking parameters.

## Goals

1. Remove cross-context application-module dependencies from `redirect/application` and `analytics/application`.
2. Publish the required synchronous ports from `contract-shortlink` and `contract-analytics`.
3. Keep provider implementations inside the owning bounded context.
4. Preserve existing redirect, quota, analytics export, and analytics reporting behavior.
5. Strengthen architecture tests so future cross-context `application` dependencies fail fast.

## Non-Goals

- Do not split the monolith into independently deployed services.
- Do not redesign redirect routing, redirect cache storage, analytics aggregation, or shortlink persistence.
- Do not change HTTP API behavior.
- Do not require external binary compatibility for current internal Java application APIs.
- Do not use this change to remove unrelated projections or legacy adapters unless they become unused after the contract migration.

## Recommended Approach

Use provider-owned published ports in `contracts/*`, with small adapter implementations in the provider bounded contexts.

This keeps the runtime call graph mostly the same while changing the compile-time graph:

```text
redirect/application  -> contract-shortlink
redirect/application  -> contract-analytics
analytics/application -> contract-shortlink

shortlink/*           -> contract-shortlink
analytics/*           -> contract-analytics
```

The application services in `redirect` and `analytics` will depend only on contract interfaces. Spring composition in `app` will still wire provider beans from `shortlink` and `analytics` into those contract interfaces.

## Alternatives Considered

### Option A: Move `ShortLinkReadService` As-Is Into `contract-shortlink`

This is the lowest-churn option because consumers and tests mostly change imports. It also matches the existing method set.

The downside is that it publishes a broad read service containing methods needed by different consumers. That makes the contract less explicit and encourages future consumers to reuse a general shortlink facade instead of declaring a narrow need.

### Option B: Publish Cross-Context-Only Contract Ports

This is the recommended option.

Publish only the read/query needs that cross bounded-context boundaries:

- `ShortLinkReadPort` for redirect lookup, link ownership lookup, and link summary lookup
- `VisitRecorderPort` for redirect visit recording
- `ApplicationClickUsagePort` for quota checks backed by analytics

This keeps contract count small while preventing the contract from becoming a full shortlink application facade. It is easier to test, easier to review, and less likely to expose command behavior or internal shortlink use cases.

### Option C: Reuse `contract-redirect/LinkMetaSourcePort` For Redirect Lookup

This is already present and usable for redirect's authoritative lookup. It is acceptable as a compatibility bridge, but it names the contract from the consumer side rather than publishing shortlink's read language from the provider side.

For this remediation, prefer moving the authoritative shortlink read contract into `contract-shortlink`. Keep `LinkMetaSourcePort` only if existing tests or adapters need a short transition period, then remove or deprecate it once production code no longer uses it.

## Target Contracts

### `contract-shortlink`

Create a shortlink-owned read contract for synchronous cross-context reads.

Preferred shape:

```java
package com.linkforge.contract.shortlink;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShortLinkReadPort {

    Optional<RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code);

    Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId);

    Map<Long, ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    record RedirectLinkView(
            long tenantId,
            long linkId,
            String code,
            String hostname,
            String originalUrl,
            boolean enabled,
            Instant expiresAtUtc,
            Integer redirectStatusCode,
            boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            String queryForwardAllowlist,
            Long applicationId,
            Long domainId,
            String lifecycleState
    ) {
        private static final String ACTIVE_LIFECYCLE_STATE = "ACTIVE";

        public RedirectLinkView {
            lifecycleState = normalizeLifecycleState(lifecycleState);
        }

        private static String normalizeLifecycleState(String raw) {
            if (raw == null || raw.trim().isBlank()) {
                return ACTIVE_LIFECYCLE_STATE;
            }
            return raw.trim().toUpperCase();
        }
    }

    record ShortLinkOwnership(Long applicationId, Long domainId) {
    }

    record ShortLinkSummary(long linkId, String code, String originalUrl, boolean deleted) {
    }
}
```

The existing `ShortLinkOwnershipLookupPort` should be retired after `analytics/application` moves to `ShortLinkReadPort.findOwnership(...)`. If it must stay for a transition commit, implement it as a compatibility adapter that delegates to the same provider and ensure Spring does not publish ambiguous ownership beans for the new API.

### `contract-analytics`

Move application click usage to analytics' published contract:

```java
package com.linkforge.contract.analytics;

import java.time.LocalDate;

public interface ApplicationClickUsagePort {

    long countApplicationClicks(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc
    );
}
```

Replace the current thin `VisitRecorderPort` method with a richer visit record so redirect does not import `AnalyticsVisitEventService.RedirectVisitEvent`:

```java
package com.linkforge.contract.analytics;

public interface VisitRecorderPort {

    void recordVisit(RedirectVisitRecord visit);
}
```

```java
package com.linkforge.contract.analytics;

public record RedirectVisitRecord(
        long tenantId,
        long linkId,
        long occurredAtMillis,
        Long applicationId,
        Long domainId,
        String code,
        String originalUrl,
        VisitContext visitContext
) {
}
```

Keep `VisitContext` as the transport-free visit context value object.

## Provider-Side Design

### Shortlink Provider

`shortlink` owns the implementation of `ShortLinkReadPort`.

Preferred implementation choices:

- update `ShortLinkReadApplicationService` to implement `ShortLinkReadPort`; or
- add a `ShortLinkReadPortAdapter` in `shortlink/application` that delegates to the existing `ShortLinkReadService`.

If the implementation plan keeps `ShortLinkReadService` temporarily, it must become internal to `shortlink` only. No other bounded context may import it after this remediation.

`ShortLinkReadRepository` should either return contract records directly or the provider adapter should map current application records to contract records. Returning contract records directly is cleaner once the consumer migration is complete.

### Analytics Provider

`analytics` owns implementations of:

- `VisitRecorderPort`
- `ApplicationClickUsagePort`

`AnalyticsVisitEventService` can implement `VisitRecorderPort` directly or be wrapped by an adapter. Direct implementation is acceptable because consumers only compile against `contract-analytics`.

The existing fail-open semantics belong in analytics. Redirect should not catch analytics infrastructure failures or know how analytics stores events.

`MybatisApplicationClickUsagePort` should implement `com.linkforge.contract.analytics.ApplicationClickUsagePort` instead of the current `analytics.application` interface.

## Consumer-Side Design

### Redirect Application

`RedirectService` should depend on:

- `LinkCachePort`
- `ShortLinkReadPort`
- `VisitRecorderPort`
- `ApplicationScopePort`
- `ApplicationClickUsagePort`
- `Clock`

It should no longer import:

- `com.linkforge.analytics.application.AnalyticsVisitEventService`
- `com.linkforge.analytics.application.ApplicationClickUsagePort`
- `com.linkforge.shortlink.application.ShortLinkReadService`

Redirect maps `ShortLinkReadPort.RedirectLinkView` to `contract-redirect/LinkMeta` for internal redirect resolution and cache writes.

Redirect maps `LinkMeta` plus `RedirectVisitInput` to `RedirectVisitRecord` for analytics. The record must include the current `Clock` timestamp and preserve application/domain ownership metadata from `LinkMeta`.

### Analytics Application

`AnalyticsExportRequestService` should use `ShortLinkReadPort.findOwnership(...)`, not `ShortLinkReadService`.

`AnalyticsReportingApplicationService` should use the published summary lookup method, not `ShortLinkReadService.listSummaries(...)`.

Analytics should have one shortlink contract dependency and no compile-time dependency on `shortlink/application`.

## Maven Dependency Design

After migration:

- `server/redirect/application/pom.xml` removes:
  - `linkforge-analytics-application`
  - `linkforge-shortlink-application`
- `server/redirect/application/pom.xml` adds:
  - `linkforge-contract-analytics`
  - `linkforge-contract-shortlink`
- `server/analytics/application/pom.xml` removes:
  - `linkforge-shortlink-application`
- `server/analytics/application/pom.xml` adds:
  - `linkforge-contract-shortlink`

Provider modules may depend on their own contracts:

- `analytics/application` already depends on `linkforge-contract-analytics`.
- `shortlink/application` should depend on `linkforge-contract-shortlink`.
- `analytics/infrastructure` should depend on `linkforge-contract-analytics` through its application or directly if the implementation class imports the contract type.

No consumer application module should depend on another bounded context's `*-application` artifact.

## Architecture Guardrail

Update the cross-context rule so a bounded context cannot depend on another bounded context's inner packages, including `application`:

```java
String[] innerLayerPatterns() {
    return new String[]{
            basePackage + ".domain..",
            basePackage + ".application..",
            basePackage + ".infrastructure..",
            basePackage + ".interfaces.."
    };
}
```

Because the rule skips `from == to`, same-context dependencies such as `shortlink.infrastructure -> shortlink.application` remain valid. Cross-context interactions must go through `com.linkforge.contract..` packages.

The guardrail should be added before the refactor so the current direct dependencies are visible as expected failures. It should pass after the contract migration.

## Migration Sequence

1. Expand the architecture test to include cross-context `application` packages and verify it fails on the current direct dependencies.
2. Add or update the analytics contract types: `VisitRecorderPort`, `RedirectVisitRecord`, and `ApplicationClickUsagePort`.
3. Add the shortlink published read contract in `contract-shortlink`.
4. Update provider-side analytics implementations to implement the contract interfaces.
5. Update provider-side shortlink implementation to expose the new shortlink read contract.
6. Refactor `RedirectService` and its tests to use `ShortLinkReadPort`, `VisitRecorderPort`, and contract `ApplicationClickUsagePort`.
7. Refactor analytics application services and tests to use the shortlink contract.
8. Remove direct Maven dependencies from consumer application modules.
9. Delete or restrict old application-layer interfaces that are no longer used outside their owning context.
10. Run focused module tests and architecture verification.

## Testing Strategy

Use test-driven migration for each boundary:

- Architecture test first: make the current cross-context `application` dependencies fail.
- Contract module tests: compile `contracts/analytics` and `contracts/shortlink` independently.
- Redirect application tests:
  - cache hit does not call shortlink read port
  - cache miss calls shortlink read port
  - negative cache behavior stays unchanged
  - visit recording receives the same ownership and visit metadata as before
  - quota checks call contract `ApplicationClickUsagePort`
- Analytics application tests:
  - export request validates link ownership through the shortlink contract
  - reporting enriches top links through the summary contract
- Provider tests:
  - shortlink read provider returns the same redirect metadata, ownership, and summaries as before
  - analytics visit recorder preserves fail-open behavior
  - click usage provider still returns monthly PV counts

Focused verification command:

```bash
cd server && mvn -q -pl app,redirect/application,redirect/interfaces,analytics/application,analytics/infrastructure,shortlink/application,shortlink/infrastructure,contracts/analytics,contracts/shortlink -am -Dtest=ArchitectureTest,RedirectServiceAuthoritativeFallbackTest,RedirectServiceTimezoneTest,RedirectControllerTimezoneTest,RedirectControllerExpiryBoundaryTest,AnalyticsVisitEventServiceTest,AnalyticsExportRequestServiceTest,AnalyticsReportingApplicationServiceTest,ShortLinkReadApplicationServiceTest,MybatisShortLinkReadRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Full backend verification remains:

```bash
cd server && mvn test
```

## Acceptance Criteria

- `redirect/application` has no Maven dependency on `linkforge-analytics-application` or `linkforge-shortlink-application`.
- `analytics/application` has no Maven dependency on `linkforge-shortlink-application`.
- Production code under `redirect/application/src/main/java` has no imports from `com.linkforge.analytics.application` or `com.linkforge.shortlink.application`.
- Production code under `analytics/application/src/main/java` has no imports from `com.linkforge.shortlink.application`.
- `VisitRecorderPort` carries enough data to preserve current analytics event fields.
- `ApplicationClickUsagePort` lives in `contract-analytics`.
- shortlink synchronous read data needed by redirect and analytics is available through `contract-shortlink`.
- Architecture tests fail if any bounded context imports another bounded context's `application`, `domain`, `infrastructure`, or `interfaces` package.
- Existing redirect, analytics export, analytics reporting, and quota tests pass after migration.

## Risks And Mitigations

- Risk: `VisitRecorderPort` loses analytics dimensions during simplification.
  - Mitigation: publish `RedirectVisitRecord` with all fields currently carried by `AnalyticsVisitEventService.RedirectVisitEvent`.
- Risk: moving `ApplicationClickUsagePort` creates duplicate beans or package ambiguity.
  - Mitigation: delete the old `analytics.application.ApplicationClickUsagePort` after updating imports, rather than keeping both.
- Risk: `ShortLinkReadPort` becomes a broad replacement for the application service.
  - Mitigation: publish only methods currently used across context boundaries. Keep command behavior and internal read use cases inside `shortlink.application`.
- Risk: the stronger ArchUnit rule catches unrelated existing dependencies.
  - Mitigation: treat those as legitimate architecture failures unless the dependency is from `app` composition or same-context code; do not add broad exceptions.
- Risk: provider adapters duplicate mapping logic during migration.
  - Mitigation: allow a short-lived adapter layer, then collapse repository return types to contract records once all consumers are migrated.

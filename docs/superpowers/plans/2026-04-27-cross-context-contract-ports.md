# Cross-Context Contract Ports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace direct bounded-context `application` dependencies between redirect, analytics, and shortlink with published contract ports while preserving the single-runtime synchronous behavior.

**Architecture:** Keep LinkForge as one Spring Boot modular monolith. Move the cross-context type surface to `contract-analytics` and `contract-shortlink`, let provider bounded contexts implement those contracts, and make consumers depend only on contracts. Strengthen ArchUnit so future cross-context dependencies into another bounded context's `application` package fail.

**Tech Stack:** Java 17, Spring Boot 3.2, Maven reactor, JUnit 5, Mockito, AssertJ, ArchUnit.

---

## Source Spec

Design spec: `docs/superpowers/specs/2026-04-27-cross-context-contract-ports-design.md`

## Scope

This plan only changes backend module boundaries and Java type dependencies. It does not change the deployment model, HTTP API behavior, redirect cache semantics, analytics aggregation semantics, or persistence schema.

## File/Module Map

### Architecture guardrail
- Modify/Test: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

### Analytics contract
- Create: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/ApplicationClickUsagePort.java`
- Create: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/RedirectVisitRecord.java`
- Modify: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/VisitRecorderPort.java`

### Shortlink contract and provider
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkReadPort.java`
- Modify: `server/shortlink/application/pom.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java`
- Delete: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkReadApplicationServiceTest.java`
- Modify/Test: `server/shortlink/infrastructure/src/test/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepositoryTest.java`

### Analytics provider and consumer
- Modify: `server/analytics/application/pom.xml`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`
- Delete: `server/analytics/application/src/main/java/com/linkforge/analytics/application/ApplicationClickUsagePort.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/MybatisApplicationClickUsagePort.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`
- Modify/Test: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsVisitEventServiceTest.java`
- Modify/Test: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java`
- Modify/Test: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsReportingApplicationServiceTest.java`

### Redirect consumer
- Modify: `server/redirect/application/pom.xml`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerTimezoneTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerExpiryBoundaryTest.java`

### Cleanup
- Delete: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkOwnershipLookupPort.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/ShortLinkOwnershipLookupAdapter.java`
- Modify/Test: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`

---

### Task 1: Freeze Cross-Context Application Boundaries

**Files:**
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

- [x] **Step 1: Make cross-context `application` dependencies visible**

Replace `innerLayerPatterns()` in `ArchitectureTest` with:

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

- [x] **Step 2: Run architecture test and confirm the current violation**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL with cross-context violations that include these dependencies:

```text
com.linkforge.redirect.application.RedirectService -> com.linkforge.analytics.application..
com.linkforge.redirect.application.RedirectService -> com.linkforge.shortlink.application..
com.linkforge.analytics.application.AnalyticsExportRequestService -> com.linkforge.shortlink.application..
com.linkforge.analytics.application.AnalyticsReportingApplicationService -> com.linkforge.shortlink.application..
```

- [x] **Step 3: Commit the failing guardrail**

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "test: guard cross-context application dependencies"
```

---

### Task 2: Publish Analytics Contract Ports

**Files:**
- Create: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/ApplicationClickUsagePort.java`
- Create: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/RedirectVisitRecord.java`
- Modify: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/VisitRecorderPort.java`

- [x] **Step 1: Add `ApplicationClickUsagePort` to `contract-analytics`**

Create `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/ApplicationClickUsagePort.java`:

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

- [x] **Step 2: Add the rich redirect visit record**

Create `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/RedirectVisitRecord.java`:

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

- [x] **Step 3: Replace the thin visit recorder signature**

Replace `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/VisitRecorderPort.java` with:

```java
package com.linkforge.contract.analytics;

/**
 * Outbound Port: 由 redirect/edge 侧调用，向 analytics 上报访问事件（PV/UV/维度/明细）。
 *
 * <p>说明：实现侧必须遵循“写统计不影响主链路”的降级原则。</p>
 */
public interface VisitRecorderPort {

    void recordVisit(RedirectVisitRecord visit);
}
```

- [x] **Step 4: Compile the contract module**

Run:

```bash
cd server && mvn -q -pl contracts/analytics -am test
```

Expected: PASS.

- [x] **Step 5: Commit analytics contracts**

```bash
git add server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/ApplicationClickUsagePort.java \
        server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/RedirectVisitRecord.java \
        server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/VisitRecorderPort.java
git commit -m "feat: publish analytics contract ports"
```

---

### Task 3: Publish Shortlink Read Contract And Provider

**Files:**
- Create: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkReadPort.java`
- Modify: `server/shortlink/application/pom.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java`
- Delete: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkReadApplicationServiceTest.java`
- Modify/Test: `server/shortlink/infrastructure/src/test/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepositoryTest.java`

- [x] **Step 1: Add the shortlink published read port**

Create `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkReadPort.java`:

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

        public RedirectLinkView(
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
                Long domainId
        ) {
            this(
                    tenantId,
                    linkId,
                    code,
                    hostname,
                    originalUrl,
                    enabled,
                    expiresAtUtc,
                    redirectStatusCode,
                    previewEnabled,
                    unavailableLandingUrl,
                    queryForwardMode,
                    queryForwardAllowlist,
                    applicationId,
                    domainId,
                    ACTIVE_LIFECYCLE_STATE
            );
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

- [x] **Step 2: Add the contract dependency to `shortlink/application`**

In `server/shortlink/application/pom.xml`, add this dependency beside the existing contract dependencies:

```xml
        <dependency>
            <groupId>com.linkforge</groupId>
            <artifactId>linkforge-contract-shortlink</artifactId>
        </dependency>
```

- [x] **Step 3: Move repository return types to the contract records**

Replace `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java` with:

```java
package com.linkforge.shortlink.application.port;

import com.linkforge.contract.shortlink.ShortLinkReadPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShortLinkReadRepository {

    Optional<ShortLinkReadPort.RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code);

    Optional<ShortLinkReadPort.ShortLinkOwnership> findOwnership(long tenantId, long linkId);

    Map<Long, ShortLinkReadPort.ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    List<Long> listLinkIdsByApplication(long tenantId, long applicationId);

    List<Long> listLinkIdsByDomain(long tenantId, long domainId);
}
```

- [x] **Step 4: Make the application service implement the contract**

Replace `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java` with:

```java
package com.linkforge.shortlink.application;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ShortLinkReadApplicationService implements ShortLinkReadPort {

    private final ShortLinkReadRepository shortLinkReadRepository;

    public ShortLinkReadApplicationService(ShortLinkReadRepository shortLinkReadRepository) {
        this.shortLinkReadRepository = shortLinkReadRepository;
    }

    @Override
    public Optional<RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
        return shortLinkReadRepository.findRedirectMetaByHostAndCode(host, code);
    }

    @Override
    public Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
        return shortLinkReadRepository.findOwnership(tenantId, linkId);
    }

    @Override
    public Map<Long, ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        return shortLinkReadRepository.listSummaries(tenantId, linkIds);
    }
}
```

- [x] **Step 5: Update MyBatis repository imports and record constructors**

In `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java`:

Replace:

```java
import com.linkforge.shortlink.application.ShortLinkReadService;
```

with:

```java
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Change method signatures and constructors:

```java
public Optional<ShortLinkReadPort.RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code)

public Optional<ShortLinkReadPort.ShortLinkOwnership> findOwnership(long tenantId, long linkId)

public Map<Long, ShortLinkReadPort.ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds)
```

Replace record construction with:

```java
new ShortLinkReadPort.ShortLinkOwnership(row.getApplicationId(), row.getDomainId())
```

```java
new ShortLinkReadPort.ShortLinkSummary(
        row.getId(),
        row.getCode(),
        row.getOriginalUrl(),
        false
)
```

```java
return new ShortLinkReadPort.RedirectLinkView(
        row.getTenantId() == null ? 0L : row.getTenantId(),
        row.getId() == null ? 0L : row.getId(),
        row.getCode(),
        row.getHostname(),
        row.getOriginalUrl(),
        Boolean.TRUE.equals(row.getEnabled()),
        toInstant(row.getExpiresAt()),
        row.getRedirectStatusCode(),
        Boolean.TRUE.equals(row.getPreviewEnabled()),
        row.getUnavailableLandingUrl(),
        row.getQueryForwardMode(),
        row.getQueryForwardAllowlist(),
        row.getApplicationId(),
        row.getDomainId(),
        row.getLifecycleState()
);
```

Keep existing normalization, SQL mapper calls, and `listLinkIdsByApplication` / `listLinkIdsByDomain` behavior unchanged.

- [x] **Step 6: Update shortlink read service tests**

In `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkReadApplicationServiceTest.java`:

Replace the import and service type usage with:

```java
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Use this pattern in tests:

```java
ShortLinkReadPort service = new ShortLinkReadApplicationService(repository);

ShortLinkReadPort.RedirectLinkView expected = new ShortLinkReadPort.RedirectLinkView(
        22L,
        11L,
        "abc123",
        "go.example.test",
        "https://example.com/live",
        true,
        Instant.parse("2026-03-18T10:15:30Z"),
        302,
        false,
        "https://example.com/unavailable",
        "ALLOWLIST",
        "utm_source,utm_medium",
        33L,
        44L
);
```

Replace ownership and summary records with:

```java
new ShortLinkReadPort.ShortLinkOwnership(33L, 44L)
```

```java
new ShortLinkReadPort.ShortLinkSummary(101L, "abc123", "https://example.com/a", false)
```

Remove the `scopeLookups_shouldDelegateToRepository` test from this service test, because `ShortLinkReadPort` does not publish the application/domain link-id list methods.

- [x] **Step 7: Update shortlink infrastructure tests**

In `server/shortlink/infrastructure/src/test/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepositoryTest.java`, replace `ShortLinkReadService` imports and record references with `ShortLinkReadPort`:

```java
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Use:

```java
Optional<ShortLinkReadPort.RedirectLinkView> actual =
        repository.findRedirectMetaByHostAndCode("Go.Example.Test:443", "abc123");
```

```java
assertThat(actual).contains(new ShortLinkReadPort.ShortLinkOwnership(33L, 44L));
```

```java
Map<Long, ShortLinkReadPort.ShortLinkSummary> actual = repository.listSummaries(22L, List.of(11L, 12L));
```

- [x] **Step 8: Delete the old application read interface**

Delete:

```text
server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java
```

- [x] **Step 9: Run shortlink focused tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application,shortlink/infrastructure,contracts/shortlink -am -Dtest=ShortLinkReadApplicationServiceTest,MybatisShortLinkReadRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [x] **Step 10: Commit shortlink contract provider**

```bash
git add server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkReadPort.java \
        server/shortlink/application/pom.xml \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java \
        server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java \
        server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkReadApplicationServiceTest.java \
        server/shortlink/infrastructure/src/test/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepositoryTest.java
git add -u server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java
git commit -m "feat: publish shortlink read contract"
```

---

### Task 4: Migrate Analytics Implementations To Contract Ports

**Files:**
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`
- Delete: `server/analytics/application/src/main/java/com/linkforge/analytics/application/ApplicationClickUsagePort.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/MybatisApplicationClickUsagePort.java`
- Modify/Test: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsVisitEventServiceTest.java`

- [x] **Step 1: Make `AnalyticsVisitEventService` implement `VisitRecorderPort`**

In `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`, add imports:

```java
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
```

Change the class declaration:

```java
public class AnalyticsVisitEventService implements VisitRecorderPort {
```

Add this method above `append(...)`:

```java
@Override
public void recordVisit(RedirectVisitRecord visit) {
    if (visit == null) {
        return;
    }
    VisitContext context = visit.visitContext();
    append(new RedirectVisitEvent(
            visit.tenantId(),
            visit.linkId(),
            visit.occurredAtMillis(),
            visit.applicationId(),
            visit.domainId(),
            visit.code(),
            visit.originalUrl(),
            context == null ? null : context.ip(),
            context == null ? null : context.userAgent(),
            context == null ? null : context.referer(),
            context == null ? null : context.acceptLanguage(),
            context == null ? null : context.trackingParams()
    ));
}
```

Keep the existing `append(RedirectVisitEvent event)` method and `RedirectVisitEvent` record so `AnalyticsVisitEventAppender` and `RedisAnalyticsVisitEventAppender` do not need a larger migration in this task.

- [x] **Step 2: Move click usage implementation to the contract import**

In `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/MybatisApplicationClickUsagePort.java`, replace:

```java
import com.linkforge.analytics.application.ApplicationClickUsagePort;
```

with:

```java
import com.linkforge.contract.analytics.ApplicationClickUsagePort;
```

- [x] **Step 3: Delete the old application-layer click usage interface**

Delete:

```text
server/analytics/application/src/main/java/com/linkforge/analytics/application/ApplicationClickUsagePort.java
```

- [x] **Step 4: Add a contract-path test for visit recording**

In `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsVisitEventServiceTest.java`, add imports:

```java
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
```

Add this test method:

```java
@Test
void recordVisit_should_convert_contract_record_and_delegate_to_appender() {
    AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
    AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, new AnalyticsProperties());

    service.recordVisit(new RedirectVisitRecord(
            1L,
            10L,
            1_710_000_000_000L,
            100L,
            200L,
            "abc123",
            "https://example.com/live",
            new VisitContext(
                    "1.2.3.4",
                    "Mozilla/5.0",
                    "https://example.com/ref",
                    "zh-CN,zh;q=0.9",
                    Map.of("utm_source", "newsletter")
            )
    ));

    verify(appender).append(new AnalyticsVisitEventService.RedirectVisitEvent(
            1L,
            10L,
            1_710_000_000_000L,
            100L,
            200L,
            "abc123",
            "https://example.com/live",
            "1.2.3.4",
            "Mozilla/5.0",
            "https://example.com/ref",
            "zh-CN,zh;q=0.9",
            Map.of("utm_source", "newsletter")
    ));
}
```

- [x] **Step 5: Run analytics provider focused tests**

Run:

```bash
cd server && mvn -q -pl analytics/application,analytics/infrastructure,contracts/analytics -am -Dtest=AnalyticsVisitEventServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [x] **Step 6: Commit analytics provider migration**

```bash
git add server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java \
        server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/MybatisApplicationClickUsagePort.java \
        server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsVisitEventServiceTest.java
git add -u server/analytics/application/src/main/java/com/linkforge/analytics/application/ApplicationClickUsagePort.java
git commit -m "feat: implement analytics contract ports"
```

---

### Task 5: Migrate Redirect Application To Contracts

**Files:**
- Modify: `server/redirect/application/pom.xml`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerTimezoneTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerExpiryBoundaryTest.java`

- [x] **Step 1: Update redirect Maven dependencies**

In `server/redirect/application/pom.xml`, remove:

```xml
        <dependency>
            <groupId>com.linkforge</groupId>
            <artifactId>linkforge-analytics-application</artifactId>
        </dependency>
        <dependency>
            <groupId>com.linkforge</groupId>
            <artifactId>linkforge-shortlink-application</artifactId>
        </dependency>
```

Add:

```xml
        <dependency>
            <groupId>com.linkforge</groupId>
            <artifactId>linkforge-contract-analytics</artifactId>
        </dependency>
        <dependency>
            <groupId>com.linkforge</groupId>
            <artifactId>linkforge-contract-shortlink</artifactId>
        </dependency>
```

- [x] **Step 2: Replace redirect service imports and fields**

In `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`, remove imports for:

```java
import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.analytics.application.ApplicationClickUsagePort;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
import com.linkforge.shortlink.application.ShortLinkReadService;
```

Add imports:

```java
import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Change fields:

```java
private final LinkCachePort linkCache;
private final ShortLinkReadPort shortLinkReadPort;
private final VisitRecorderPort visitRecorderPort;
private final Clock clock;
private final ApplicationScopePort applicationScopePort;
private final ApplicationClickUsagePort applicationClickUsagePort;
```

- [x] **Step 3: Replace constructors**

Use these constructors:

```java
@Autowired
public RedirectService(
        LinkCachePort linkCache,
        ShortLinkReadPort shortLinkReadPort,
        VisitRecorderPort visitRecorderPort,
        Clock clock,
        ApplicationScopePort applicationScopePort,
        ApplicationClickUsagePort applicationClickUsagePort
) {
    this.linkCache = linkCache;
    this.shortLinkReadPort = shortLinkReadPort;
    this.visitRecorderPort = visitRecorderPort;
    this.clock = clock;
    this.applicationScopePort = applicationScopePort == null ? noQuotaApplicationScopePort() : applicationScopePort;
    this.applicationClickUsagePort = applicationClickUsagePort == null ? noClickUsagePort() : applicationClickUsagePort;
}

public RedirectService(
        LinkCachePort linkCache,
        ShortLinkReadPort shortLinkReadPort,
        VisitRecorderPort visitRecorderPort,
        Clock clock
) {
    this(
            linkCache,
            shortLinkReadPort,
            visitRecorderPort,
            clock,
            noQuotaApplicationScopePort(),
            noClickUsagePort()
    );
}
```

Remove the constructors that accept `LinkMetaSourcePort`, and remove the `readServiceFrom(...)` and `toRedirectLinkMeta(...)` helpers.

- [x] **Step 4: Replace shortlink lookup mapping**

Change `findMeta(...)` to use `shortLinkReadPort`:

```java
LinkMeta meta = shortLinkReadPort.findRedirectMetaByHostAndCode(host, normalized)
        .map(RedirectService::toLinkMeta)
        .orElse(null);
```

Replace `toLinkMeta(...)` with:

```java
private static LinkMeta toLinkMeta(ShortLinkReadPort.RedirectLinkView meta) {
    return new LinkMeta(
            meta.linkId(),
            meta.tenantId(),
            meta.code(),
            meta.originalUrl(),
            meta.enabled(),
            toUtcLocalDateTime(meta.expiresAtUtc()),
            meta.redirectStatusCode(),
            meta.previewEnabled(),
            meta.unavailableLandingUrl(),
            meta.queryForwardMode(),
            meta.queryForwardAllowlist(),
            meta.hostname(),
            meta.applicationId(),
            meta.domainId(),
            meta.lifecycleState()
    );
}
```

- [x] **Step 5: Replace analytics recording**

Replace both `analyticsVisitEventService.append(...)` call sites with:

```java
visitRecorderPort.recordVisit(toRedirectVisitRecord(meta, request.visitInput()));
```

and:

```java
visitRecorderPort.recordVisit(toRedirectVisitRecord(meta, visitInput));
```

Replace `toRedirectVisitEvent(...)` with:

```java
private RedirectVisitRecord toRedirectVisitRecord(LinkMeta meta, RedirectVisitInput visitInput) {
    return new RedirectVisitRecord(
            meta.tenantId(),
            meta.id(),
            clock.instant().toEpochMilli(),
            meta.applicationId(),
            meta.domainId(),
            meta.code(),
            meta.originalUrl(),
            new VisitContext(
                    visitInput == null ? null : visitInput.ip(),
                    visitInput == null ? null : visitInput.userAgent(),
                    visitInput == null ? null : visitInput.referer(),
                    visitInput == null ? null : visitInput.acceptLanguage(),
                    visitInput == null ? null : visitInput.trackingParams()
            )
    );
}
```

- [x] **Step 6: Update redirect application tests to contract types**

In `RedirectServiceAuthoritativeFallbackTest` and `RedirectServiceTimezoneTest`, replace imports with:

```java
import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Remove old imports for:

```java
import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.shortlink.application.ShortLinkReadService;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
```

Use mocks and records like:

```java
ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
ShortLinkReadPort.RedirectLinkView authoritative = new ShortLinkReadPort.RedirectLinkView(
        22L,
        11L,
        "abc123",
        "go.example.test",
        "https://example.com/live",
        true,
        Instant.parse("2026-03-18T10:15:30Z"),
        302,
        false,
        "https://example.com/unavailable",
        "ALLOWLIST",
        "utm_source",
        33L,
        44L
);
```

Use a recording visit port instead of `AnalyticsVisitEventService`:

```java
AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
VisitRecorderPort visitRecorderPort = recorded::set;
```

Assert timezone visit recording with:

```java
assertThat(recorded.get()).isEqualTo(new RedirectVisitRecord(
        1L,
        1L,
        Instant.parse("2026-04-24T10:15:30Z").toEpochMilli(),
        null,
        null,
        "abc123",
        "https://example.com",
        new VisitContext(
                "1.2.3.4",
                "Mozilla/5.0",
                "https://ref.example.com/path",
                "zh-CN,zh;q=0.9",
                java.util.Map.of("utm_source", "newsletter")
        )
));
```

- [x] **Step 7: Update redirect interface tests**

In `RedirectControllerTimezoneTest` and `RedirectControllerExpiryBoundaryTest`, remove imports for:

```java
import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
```

Add imports for the contract test double:

```java
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.shortlink.ShortLinkReadPort;

import java.util.List;
import java.util.Map;
```

Replace constructor calls that pass `(LinkMetaSourcePort) code -> Optional.of(meta)` with a `ShortLinkReadPort` test double:

```java
ShortLinkReadPort shortLinkReadPort = new ShortLinkReadPort() {
    @Override
    public Optional<ShortLinkReadPort.RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
        return Optional.of(new ShortLinkReadPort.RedirectLinkView(
                meta.tenantId(),
                meta.id(),
                meta.code(),
                meta.hostname(),
                meta.originalUrl(),
                meta.enabled(),
                meta.expiresAt() == null ? null : meta.expiresAt().toInstant(ZoneOffset.UTC),
                meta.redirectStatusCode(),
                meta.previewEnabled(),
                meta.unavailableLandingUrl(),
                meta.queryForwardMode(),
                meta.queryForwardAllowlist(),
                meta.applicationId(),
                meta.domainId(),
                meta.lifecycleState()
        ));
    }

    @Override
    public Optional<ShortLinkReadPort.ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
        return Optional.empty();
    }

    @Override
    public Map<Long, ShortLinkReadPort.ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        return Map.of();
    }
};
```

Use:

```java
VisitRecorderPort visitRecorderPort = visit -> {
};
```

and construct `RedirectService` with `shortLinkReadPort` and `visitRecorderPort`.

- [x] **Step 8: Run redirect focused tests**

Run:

```bash
cd server && mvn -q -pl redirect/application,redirect/interfaces,contracts/analytics,contracts/shortlink -am -Dtest=RedirectServiceAuthoritativeFallbackTest,RedirectServiceTimezoneTest,RedirectControllerTimezoneTest,RedirectControllerExpiryBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [x] **Step 9: Commit redirect migration**

```bash
git add server/redirect/application/pom.xml \
        server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java \
        server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java \
        server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java \
        server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerTimezoneTest.java \
        server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerExpiryBoundaryTest.java
git commit -m "refactor: move redirect dependencies to contracts"
```

---

### Task 6: Migrate Analytics Application Consumers To Shortlink Contract

**Files:**
- Modify: `server/analytics/application/pom.xml`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`
- Modify/Test: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java`
- Modify/Test: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsReportingApplicationServiceTest.java`

- [x] **Step 1: Update analytics Maven dependencies**

In `server/analytics/application/pom.xml`, remove:

```xml
        <dependency>
            <groupId>com.linkforge</groupId>
            <artifactId>linkforge-shortlink-application</artifactId>
        </dependency>
```

Add:

```xml
        <dependency>
            <groupId>com.linkforge</groupId>
            <artifactId>linkforge-contract-shortlink</artifactId>
        </dependency>
```

- [x] **Step 2: Migrate `AnalyticsExportRequestService`**

In `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`, replace:

```java
import com.linkforge.shortlink.application.ShortLinkReadService;
```

with:

```java
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Change field and constructor parameter:

```java
private final ShortLinkReadPort shortLinkReadPort;
```

```java
public AnalyticsExportRequestService(
        ApprovalSubmissionPort approvalSubmissionPort,
        ShortLinkReadPort shortLinkReadPort,
        Clock clock
) {
    this.approvalSubmissionPort = approvalSubmissionPort;
    this.shortLinkReadPort = shortLinkReadPort;
    this.clock = clock;
}
```

Replace ownership type and lookup:

```java
ShortLinkReadPort.ShortLinkOwnership link = requireLinkScope(actor.tenantId(), linkId);
```

```java
private ShortLinkReadPort.ShortLinkOwnership requireLinkScope(long tenantId, long linkId) {
    return shortLinkReadPort.findOwnership(tenantId, linkId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "链接不存在"));
}
```

- [x] **Step 3: Migrate `AnalyticsReportingApplicationService`**

In `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`, replace:

```java
import com.linkforge.shortlink.application.ShortLinkReadService;
```

with:

```java
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Change field and constructor parameter:

```java
private final ShortLinkReadPort shortLinkReadPort;
```

```java
public AnalyticsReportingApplicationService(
        AnalyticsQueryService analyticsQueryService,
        ShortLinkReadPort shortLinkReadPort
) {
    this.analyticsQueryService = analyticsQueryService;
    this.shortLinkReadPort = shortLinkReadPort;
}
```

Change summaries map and method signature:

```java
Map<Long, ShortLinkReadPort.ShortLinkSummary> summaries = shortLinkReadPort.listSummaries(
        tenantId,
        List.copyOf(new LinkedHashSet<>(linkIds))
);
```

```java
private static TopLinkStat enrichRow(TopLinkStat row, ShortLinkReadPort.ShortLinkSummary summary) {
```

- [x] **Step 4: Update analytics application tests**

In `AnalyticsExportRequestServiceTest` and `AnalyticsReportingApplicationServiceTest`, replace:

```java
import com.linkforge.shortlink.application.ShortLinkReadService;
```

with:

```java
import com.linkforge.contract.shortlink.ShortLinkReadPort;
```

Use:

```java
ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
```

Replace records:

```java
new ShortLinkReadPort.ShortLinkOwnership(3001L, null)
```

```java
new ShortLinkReadPort.ShortLinkSummary(101L, "abc123", "https://example.com/a", false)
```

- [x] **Step 5: Run analytics consumer focused tests**

Run:

```bash
cd server && mvn -q -pl analytics/application,contracts/shortlink -am -Dtest=AnalyticsExportRequestServiceTest,AnalyticsReportingApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [x] **Step 6: Commit analytics consumer migration**

```bash
git add server/analytics/application/pom.xml \
        server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java \
        server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java \
        server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java \
        server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsReportingApplicationServiceTest.java
git commit -m "refactor: move analytics shortlink reads to contract"
```

---

### Task 7: Remove Retired Ownership Contract And Verify Boundaries

**Files:**
- Delete: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkOwnershipLookupPort.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/ShortLinkOwnershipLookupAdapter.java`
- Modify/Test: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`
- Verify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

- [x] **Step 1: Confirm no production code needs the old ownership lookup**

Run:

```bash
rg -n "ShortLinkOwnershipLookupPort|ShortLinkOwnershipLookupAdapter" server -g '*.java'
```

Expected before deletion: references only in the old contract, old adapter, and tests that assert bean absence or architecture wiring.

- [x] **Step 2: Delete old ownership lookup files**

Delete:

```text
server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkOwnershipLookupPort.java
server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/ShortLinkOwnershipLookupAdapter.java
```

- [x] **Step 3: Update `StatsControllerTest` if it references the old contract by class name**

In `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`, replace string or class references to:

```text
com.linkforge.contract.shortlink.ShortLinkOwnershipLookupPort
```

with:

```text
com.linkforge.contract.shortlink.ShortLinkReadPort
```

Keep the existing assertion intent: analytics interfaces should depend on contract-level shortlink APIs, not shortlink application APIs.

- [x] **Step 4: Prove no cross-context application imports remain**

Run:

```bash
rg -n "com\\.linkforge\\.(analytics|shortlink)\\.application" \
  server/redirect/application/src/main/java \
  server/redirect/application/src/test/java \
  server/analytics/application/src/main/java \
  server/analytics/application/src/test/java
```

Expected: no output for `server/redirect/application` and no `com.linkforge.shortlink.application` output for `server/analytics/application`.

- [x] **Step 5: Prove consumer POM dependencies are contract-only**

Run:

```bash
rg -n "linkforge-(analytics|shortlink)-application" \
  server/redirect/application/pom.xml \
  server/analytics/application/pom.xml
```

Expected: no output.

Run:

```bash
rg -n "linkforge-contract-(analytics|shortlink)" \
  server/redirect/application/pom.xml \
  server/analytics/application/pom.xml
```

Expected: `server/redirect/application/pom.xml` contains `linkforge-contract-analytics` and `linkforge-contract-shortlink`; `server/analytics/application/pom.xml` contains `linkforge-contract-shortlink` and keeps its existing `linkforge-contract-analytics` dependency.

- [x] **Step 6: Run focused architecture and module verification**

Run:

```bash
cd server && mvn -q -pl app,redirect/application,redirect/interfaces,analytics/application,analytics/infrastructure,shortlink/application,shortlink/infrastructure,contracts/analytics,contracts/shortlink -am -Dtest=ArchitectureTest,RedirectServiceAuthoritativeFallbackTest,RedirectServiceTimezoneTest,RedirectControllerTimezoneTest,RedirectControllerExpiryBoundaryTest,AnalyticsVisitEventServiceTest,AnalyticsExportRequestServiceTest,AnalyticsReportingApplicationServiceTest,ShortLinkReadApplicationServiceTest,MybatisShortLinkReadRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [x] **Step 7: Run full backend verification**

Run:

```bash
cd server && mvn test
```

Expected: PASS.

- [x] **Step 8: Commit cleanup and passing guardrail**

```bash
git add server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java
git add -u server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkOwnershipLookupPort.java \
           server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/ShortLinkOwnershipLookupAdapter.java
git commit -m "refactor: enforce contract-only bounded context reads"
```

---

## Final Acceptance Checklist

- [x] `server/redirect/application/pom.xml` has no `linkforge-analytics-application` dependency.
- [x] `server/redirect/application/pom.xml` has no `linkforge-shortlink-application` dependency.
- [x] `server/analytics/application/pom.xml` has no `linkforge-shortlink-application` dependency.
- [x] `server/redirect/application/src/main/java` has no imports from `com.linkforge.analytics.application` or `com.linkforge.shortlink.application`.
- [x] `server/analytics/application/src/main/java` has no imports from `com.linkforge.shortlink.application`.
- [x] `ApplicationClickUsagePort` lives only in `com.linkforge.contract.analytics`.
- [x] Redirect visit recording uses `VisitRecorderPort.recordVisit(RedirectVisitRecord)`.
- [x] Shortlink redirect metadata, ownership, and summary reads are exposed through `ShortLinkReadPort`.
- [x] `ArchitectureTest` fails on future cross-context `application`, `domain`, `infrastructure`, and `interfaces` dependencies.
- [x] Focused verification and `cd server && mvn test` pass.

## Plan Self-Review

- Spec coverage: Tasks 1-7 cover the architecture guardrail, analytics contracts, shortlink contract/provider, analytics provider, redirect consumer migration, analytics consumer migration, old ownership contract cleanup, dependency checks, and focused/full verification.
- Placeholder scan: no red-flag placeholder language remains in this plan.
- Type consistency: the plan consistently uses `ShortLinkReadPort.RedirectLinkView`, `ShortLinkReadPort.ShortLinkOwnership`, `ShortLinkReadPort.ShortLinkSummary`, `RedirectVisitRecord`, `VisitRecorderPort`, and contract `ApplicationClickUsagePort`.
- Execution note: Task 1 intentionally makes `ArchitectureTest` fail to expose the existing violations. The expected steady state is restored by Tasks 2-7.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-27-cross-context-contract-ports.md`.

1. **Subagent-Driven (recommended)** - dispatch a fresh worker per task, review between tasks, and keep commits aligned with task boundaries.
2. **Inline Execution** - execute tasks in this session with checkpoints after each task and focused verification before moving forward.

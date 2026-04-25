# Single-Runtime Architecture Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify LinkForge's backend to optimize for a long-lived monolith by removing redirect projection machinery, moving analytics work off the redirect hot path, replacing generic governance contracts with typed application APIs, and deleting low-value service-boundary abstractions.

**Architecture:** Keep the existing bounded contexts and layered module ownership, but stop treating them as proto-services. `shortlink` becomes the single source of truth for link metadata, `redirect` becomes pure read-through cache logic, `analytics` moves to event-append plus async aggregation, and `governance` exposes typed internal use cases instead of a generic submission protocol.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, MyBatis, Redis, Maven reactor, ArchUnit, JUnit 5, Mockito, Testcontainers.

---

## Scope Check

This remains one plan, not separate subsystem plans, because the four changes are coupled by the same architectural decision:

- direct in-process application-service calls are now allowed where they reduce indirection
- redirect correctness must be simplified before redirect projection can be deleted
- analytics hot-path changes depend on redirect using the new direct read path
- governance cleanup and final contract removal depend on the new monolith-first dependency shape

Each task below still produces a coherent, testable increment.

## Execution Setup

- Execute this plan in a fresh git worktree before changing code.
- Run commands from `/home/feng/code/project/LinkForge`.
- Keep commits small and aligned with the task boundaries below.

## File Structure

### New shortlink internal read API

- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java`
  - exported monolith-facing read API for redirect metadata, ownership lookup, and analytics summaries
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java`
  - application-layer implementation of the new read API
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java`
  - application port used by the new read API
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java`
  - MyBatis-backed implementation of the new read port
- Create: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkReadApplicationServiceTest.java`
  - unit coverage for redirect metadata, ownership, and summary lookups

### Redirect simplification

- Modify: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMeta.java`
  - carry `applicationId` and `domainId` so redirect cache hits can feed analytics ownership metadata
- Modify: `server/redirect/application/pom.xml`
  - add direct dependency on `linkforge-shortlink-application`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
  - replace `LinkMetaSourcePort` with `ShortLinkReadService`
- Modify: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
  - assert redirect cache miss uses the new shortlink read API
- Modify: `server/integration-tests/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java`
  - lock read-through cache refill behavior
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjection.java`
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.java`
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectProjectionMybatisConfig.java`
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java`
- Delete: `server/redirect/infrastructure/src/main/resources/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.xml`
- Delete: `server/redirect/infrastructure/src/test/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJobTest.java`
- Modify: `server/redirect/infrastructure/pom.xml`
  - drop projection-only dependencies

### Analytics hot-path simplification

- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`
  - internal monolith-facing API for appending lightweight redirect visit events
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/port/AnalyticsVisitEventAppender.java`
  - application port for writing visit events
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java`
  - Redis Stream writer for lightweight visit events
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java`
  - async consumer that derives PV/UV/dimension dirty markers and link catalog snapshots from visit events
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedisAggregateWriter.java`
  - extracted aggregation helper reused by the new projector job
- Create: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsVisitEventServiceTest.java`
- Create: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJobTest.java`
- Delete: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java`
- Delete: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/VisitRecorderServiceTest.java`
- Modify: `server/redirect/application/pom.xml`
  - add direct dependency on `linkforge-analytics-application`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
  - replace `VisitRecorderPort` with `AnalyticsVisitEventService`
- Modify: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java`
  - assert redirect only appends events through the new analytics service

### Analytics query and ownership cleanup

- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java`
  - add ownership and summary lookup methods needed by analytics
- Modify: `server/analytics/application/pom.xml`
  - depend on `linkforge-shortlink-application`
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingService.java`
  - application facade that enriches analytics top-link results from shortlink summaries
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`
  - implementation for top-links enrichment
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
  - replace `ShortLinkOwnershipLookupPort` with `ShortLinkReadService`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
  - use the new reporting service for top-link responses
- Modify: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java`
- Create: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsReportingApplicationServiceTest.java`
- Delete: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java`
- Delete: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJobTest.java`
- Modify: `server/analytics/infrastructure/pom.xml`
  - remove the shortlink-event contract dependency after the catalog projector is gone

### Governance cleanup

- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceRequestService.java`
  - typed approval API for monolith callers
- Create: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceRequestServiceTest.java`
- Modify: `server/shortlink/application/pom.xml`
  - depend on `linkforge-governance-application`
- Modify: `server/analytics/application/pom.xml`
  - replace governance contract dependency with `linkforge-governance-application`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
  - use typed governance request API
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
  - use typed governance request API
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
- Modify: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java`
- Delete: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapter.java`
- Delete: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapterTest.java`

### Final contract and rule cleanup

- Delete: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaSourcePort.java`
- Delete: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/VisitRecorderPort.java`
- Delete: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkOwnershipLookupPort.java`
- Delete: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalSubmissionPort.java`
- Delete: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalRequestView.java`
- Delete: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkEventPublisher.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventAppender.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactory.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventPublisherAdapter.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/ShortLinkOwnershipLookupAdapter.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
- Delete: `server/integration-tests/src/test/java/com/linkforge/ShortLinkIntegrationEventAppendIntegrationTest.java`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
  - remove blanket bounded-context prohibition and replace it with layer-focused cross-context guards

## Task 1: Switch Redirect Cache Misses To `ShortLinkReadService`

**Files:**
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java`
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java`
- Create: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkReadApplicationServiceTest.java`
- Modify: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMeta.java`
- Modify: `server/redirect/application/pom.xml`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java`

- [ ] **Step 1: Write the failing shortlink read-service unit test**

```java
class ShortLinkReadApplicationServiceTest {

    @Test
    void findRedirectMetaByHostAndCode_returnsRedirectFieldsFromRepository() {
        ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
        ShortLinkReadService service = new ShortLinkReadApplicationService(repository);

        ShortLinkReadService.RedirectLinkMeta expected = new ShortLinkReadService.RedirectLinkMeta(
                11L,
                22L,
                "AbC123",
                "go.example.com",
                "https://example.com/live",
                true,
                Instant.parse("2026-04-24T12:00:00Z"),
                302,
                false,
                null,
                null,
                List.of(),
                101L,
                202L
        );

        when(repository.findRedirectMetaByHostAndCode("go.example.com", "AbC123"))
                .thenReturn(Optional.of(expected));

        Optional<ShortLinkReadService.RedirectLinkMeta> actual =
                service.findRedirectMetaByHostAndCode("go.example.com", "AbC123");

        assertThat(actual).contains(expected);
    }
}
```

- [ ] **Step 2: Run the new unit test to verify it fails**

Run: `cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkReadApplicationServiceTest test`
Expected: FAIL with missing `ShortLinkReadService`, `ShortLinkReadApplicationService`, or `ShortLinkReadRepository`.

- [ ] **Step 3: Rewrite the redirect fallback test against the new read API**

```java
@Test
void resolve_shouldUseShortLinkReadServiceOnCacheMiss() {
    RecordingLinkCache cache = new RecordingLinkCache();
    ShortLinkReadService readService = mock(ShortLinkReadService.class);

    ShortLinkReadService.RedirectLinkMeta readMeta = new ShortLinkReadService.RedirectLinkMeta(
            11L, 22L, "abc123", null, "https://example.com/live",
            true, LocalDateTime.parse("2026-03-18T10:15:30").toInstant(ZoneOffset.UTC),
            302, false, null, null, List.of(), null, null
    );

    when(readService.findRedirectMetaByHostAndCode(null, "abc123"))
            .thenReturn(Optional.of(readMeta));

    RedirectService service = new RedirectService(cache, readService, (tenantId, linkId, visit) -> {}, Clock.systemUTC());

    RedirectResolution resolution = service.resolve(new ResolveRedirectRequest("abc123", null, false, false, null));

    assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.REDIRECT);
    assertThat(cache.cachedMeta.code()).isEqualTo("abc123");
}
```

- [ ] **Step 4: Run the redirect application test to verify it fails**

Run: `cd server && mvn -q -pl redirect/application -am -Dtest=RedirectServiceAuthoritativeFallbackTest test`
Expected: FAIL because `RedirectService` still expects `LinkMetaSourcePort`.

- [ ] **Step 5: Implement the shortlink read API and repository adapter**

```java
public interface ShortLinkReadService {

    Optional<RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code);

    record RedirectLinkMeta(
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
            List<String> queryForwardAllowlist,
            Long applicationId,
            Long domainId
    ) {
    }
}

@Service
public class ShortLinkReadApplicationService implements ShortLinkReadService {

    private final ShortLinkReadRepository repository;

    public ShortLinkReadApplicationService(ShortLinkReadRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code) {
        return repository.findRedirectMetaByHostAndCode(host, code);
    }
}

@Component
public class MybatisShortLinkReadRepository implements ShortLinkReadRepository {

    private final ShortLinkQueryMapper queryMapper;
    private final CoreProperties coreProperties;

    @Override
    public Optional<ShortLinkReadService.RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code) {
        String normalizedCode = normalizeCode(code);
        String normalizedHost = normalizeHost(host);
        if (normalizedCode == null) {
            return Optional.empty();
        }
        ShortLinkEntity row = normalizedHost == null
                ? queryMapper.findActiveByCode(normalizedCode)
                : queryMapper.findActiveByHostnameAndCode(normalizedHost, normalizedCode);
        if (row == null && normalizedHost != null && isLegacyBaseHost(normalizedHost)) {
            row = queryMapper.findActiveByLegacyBaseHostAndCode(normalizedHost, normalizedCode);
        }
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new ShortLinkReadService.RedirectLinkMeta(
                row.getTenantId(),
                row.getId(),
                row.getCode(),
                row.getHostname(),
                row.getOriginalUrl(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getExpiresAt() == null ? null : row.getExpiresAt().toInstant(ZoneOffset.UTC),
                row.getRedirectStatusCode(),
                Boolean.TRUE.equals(row.getPreviewEnabled()),
                row.getUnavailableLandingUrl(),
                row.getQueryForwardMode(),
                parseAllowlist(row.getQueryForwardAllowlist()),
                row.getApplicationId(),
                row.getDomainId()
        ));
    }
}
```

- [ ] **Step 6: Wire `RedirectService` to the new shortlink read API**

```java
@Service
public class RedirectService {

    private final LinkCachePort linkCache;
    private final ShortLinkReadService shortLinkReadService;
    private final VisitRecorderPort visitRecorder;
    private final Clock clock;

    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadService shortLinkReadService,
            VisitRecorderPort visitRecorder,
            Clock clock
    ) {
        this.linkCache = linkCache;
        this.shortLinkReadService = shortLinkReadService;
        this.visitRecorder = visitRecorder;
        this.clock = clock;
    }

    private LinkMeta findMeta(String host, String normalized) {
        LinkCachePort.LookupResult cached = linkCache.lookup(host, normalized);
        if (cached.notFound()) {
            return null;
        }
        if (cached.hit()) {
            return cached.meta();
        }

        ShortLinkReadService.RedirectLinkMeta readMeta =
                shortLinkReadService.findRedirectMetaByHostAndCode(host, normalized).orElse(null);
        if (readMeta != null) {
            LinkMeta meta = new LinkMeta(
                    readMeta.linkId(),
                    readMeta.tenantId(),
                    readMeta.code(),
                    readMeta.originalUrl(),
                    readMeta.enabled(),
                    readMeta.expiresAtUtc() == null ? null : LocalDateTime.ofInstant(readMeta.expiresAtUtc(), ZoneOffset.UTC),
                    readMeta.redirectStatusCode(),
                    readMeta.previewEnabled(),
                    readMeta.unavailableLandingUrl(),
                    readMeta.queryForwardMode(),
                    readMeta.queryForwardAllowlist(),
                    readMeta.hostname(),
                    readMeta.applicationId(),
                    readMeta.domainId()
            );
            linkCache.tryPut(host, meta);
            return meta;
        }

        linkCache.markNotFound(host, normalized);
        return null;
    }
}
```

- [ ] **Step 7: Update the negative-cache integration test to prove read-through refill still works**

```java
@Test
void redirect_shouldRefillCacheFromShortlinkReadServiceAfterCacheEviction() {
    String code = createLinkAndReturnCode();

    ResponseEntity<String> first = restTemplate.getForEntity("/r/" + code, String.class);
    assertThat(first.getStatusCode().is3xxRedirection()).isTrue();

    linkCacheService.tryEvict("localhost", code);

    ResponseEntity<String> second = restTemplate.getForEntity("/r/" + code, String.class);
    assertThat(second.getStatusCode().is3xxRedirection()).isTrue();
    assertThat(linkCacheService.lookup("localhost", code).hit()).isTrue();
}
```

- [ ] **Step 8: Run focused verification**

Run: `cd server && mvn -q -pl shortlink/application,shortlink/infrastructure,redirect/application,integration-tests -am -Dtest=ShortLinkReadApplicationServiceTest,RedirectServiceAuthoritativeFallbackTest,RedirectNegativeCacheIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java \
        server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java \
        server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkReadApplicationServiceTest.java \
        server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMeta.java \
        server/redirect/application/pom.xml \
        server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java \
        server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java \
        server/integration-tests/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java
git commit -m "refactor: route redirect cache misses through shortlink read service"
```

## Task 2: Delete Redirect Projection Machinery

**Files:**
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjection.java`
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.java`
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectProjectionMybatisConfig.java`
- Delete: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java`
- Delete: `server/redirect/infrastructure/src/main/resources/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.xml`
- Delete: `server/redirect/infrastructure/src/test/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJobTest.java`
- Modify: `server/redirect/infrastructure/pom.xml`
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java`

- [ ] **Step 1: Strengthen the after-commit cache integration test before deleting projection code**

```java
@Test
void update_shouldEvictRedirectCacheAndAllowReadThroughRefillWithoutProjectionTable() {
    long linkId = createLinkAndPrimeRedirectCache();

    shortLinkService.update(TENANT_ID, linkId, updateRequest("https://example.com/updated"), actor(), nowUtc());

    assertThat(linkCacheService.lookup("localhost", existingCode(linkId)).miss()).isTrue();

    ResponseEntity<Void> redirected = restTemplate.getForEntity("/r/" + existingCode(linkId), Void.class);
    assertThat(redirected.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com/updated"));
}
```

- [ ] **Step 2: Run the integration test to confirm the current behavior is covered**

Run: `cd server && mvn -q -pl integration-tests -am -Dtest=ShortLinkCacheAfterCommitIntegrationTest test`
Expected: PASS before the deletion work begins.

- [ ] **Step 3: Remove the projection classes and trim infrastructure dependencies**

```xml
<dependencies>
    <dependency>
        <groupId>com.linkforge</groupId>
        <artifactId>linkforge-redirect-application</artifactId>
    </dependency>
    <dependency>
        <groupId>com.linkforge</groupId>
        <artifactId>linkforge-foundation-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.linkforge</groupId>
        <artifactId>linkforge-contract-api</artifactId>
    </dependency>
    <dependency>
        <groupId>com.linkforge</groupId>
        <artifactId>linkforge-contract-redirect</artifactId>
    </dependency>
    <dependency>
        <groupId>com.linkforge</groupId>
        <artifactId>linkforge-contract-analytics</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

- [ ] **Step 4: Remove the old projector-focused tests and run focused redirect verification**

Run: `cd server && mvn -q -pl redirect/infrastructure,redirect/interfaces,integration-tests -am -Dtest=RedirectControllerExpiryBoundaryTest,RedirectControllerTimezoneTest,ShortLinkCacheAfterCommitIntegrationTest,RedirectNegativeCacheIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with no projection classes on the classpath.

- [ ] **Step 5: Commit**

```bash
git add server/redirect/infrastructure/pom.xml \
        server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java
git rm server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjection.java \
       server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.java \
       server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectProjectionMybatisConfig.java \
       server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java \
       server/redirect/infrastructure/src/main/resources/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.xml \
       server/redirect/infrastructure/src/test/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJobTest.java
git commit -m "refactor: remove redirect projection runtime"
```

## Task 3: Append Lightweight Analytics Events And Aggregate Them Asynchronously

**Files:**
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/port/AnalyticsVisitEventAppender.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedisAggregateWriter.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java`
- Create: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsVisitEventServiceTest.java`
- Create: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJobTest.java`
- Modify: `server/redirect/application/pom.xml`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java`
- Delete: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java`
- Delete: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/VisitRecorderServiceTest.java`

- [ ] **Step 1: Write the failing analytics application-service test**

```java
class AnalyticsVisitEventServiceTest {

    @Test
    void appendRedirectVisit_shouldPublishOneLightweightEvent() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender);

        service.appendRedirectVisit(
                new AnalyticsVisitEventService.RedirectVisitEvent(
                        1L,
                        101L,
                        1001L,
                        2002L,
                        "abc123",
                        "go.example.com",
                        "https://example.com/live",
                        VisitContextFactory.sample()
                )
        );

        verify(appender).append(argThat(event ->
                event.tenantId() == 1L
                        && event.linkId() == 101L
                        && event.applicationId().equals(1001L)
                        && event.domainId().equals(2002L)
        ));
    }
}
```

- [ ] **Step 2: Run the application test to verify it fails**

Run: `cd server && mvn -q -pl analytics/application -am -Dtest=AnalyticsVisitEventServiceTest test`
Expected: FAIL with missing `AnalyticsVisitEventService` or `AnalyticsVisitEventAppender`.

- [ ] **Step 3: Add a failing projector-job test that consumes one visit event and updates aggregate keys**

```java
@Test
void project_shouldConvertVisitEventIntoPvUvAndDirtyMembers() {
    StringRedisTemplate redis = redisTemplate();
    AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
            redis,
            analyticsProperties(),
            new AnalyticsRedisAggregateWriter(redis, analyticsProperties())
    );

    appendVisitEvent(redis, Map.of(
            "tenantId", "1",
            "linkId", "101",
            "applicationId", "1001",
            "domainId", "2002",
            "code", "abc123",
            "originalUrl", "https://example.com/live",
            "uaRaw", "Mozilla/5.0"
    ));

    job.project();

    assertThat(redis.opsForValue().get(AnalyticsKeys.pvKey(1L, 101L, LocalDate.now(ZoneOffset.UTC))))
            .isEqualTo("1");
    assertThat(redis.opsForSet().members(AnalyticsKeys.activeSetKey(LocalDate.now(ZoneOffset.UTC))))
            .contains("1:101");
}
```

- [ ] **Step 4: Run the projector test to verify it fails**

Run: `cd server && mvn -q -pl analytics/infrastructure -am -Dtest=AnalyticsRedirectEventProjectorJobTest test`
Expected: FAIL because the projector job and aggregate writer do not exist yet.

- [ ] **Step 5: Implement the lightweight analytics event append path**

```java
@Service
public class AnalyticsVisitEventService {

    private final AnalyticsVisitEventAppender appender;

    public AnalyticsVisitEventService(AnalyticsVisitEventAppender appender) {
        this.appender = appender;
    }

    public void appendRedirectVisit(RedirectVisitEvent event) {
        if (event == null || event.tenantId() <= 0 || event.linkId() <= 0) {
            return;
        }
        appender.append(event);
    }

    public record RedirectVisitEvent(
            long tenantId,
            long linkId,
            Long applicationId,
            Long domainId,
            String code,
            String hostname,
            String originalUrl,
            VisitContext visitContext
    ) {
    }
}

@Component
public class RedisAnalyticsVisitEventAppender implements AnalyticsVisitEventAppender {
    @Override
    public void append(AnalyticsVisitEventService.RedirectVisitEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tenantId", String.valueOf(event.tenantId()));
        fields.put("linkId", String.valueOf(event.linkId()));
        putIfPresent(fields, "applicationId", event.applicationId());
        putIfPresent(fields, "domainId", event.domainId());
        putIfPresent(fields, "code", event.code());
        putIfPresent(fields, "hostname", event.hostname());
        putIfPresent(fields, "originalUrl", event.originalUrl());
        putIfPresent(fields, "ip", event.visitContext() == null ? null : event.visitContext().ip());
        putIfPresent(fields, "userAgent", event.visitContext() == null ? null : event.visitContext().userAgent());
        putIfPresent(fields, "referer", event.visitContext() == null ? null : event.visitContext().referer());
        putIfPresent(fields, "acceptLanguage", event.visitContext() == null ? null : event.visitContext().acceptLanguage());
        redis.opsForStream().add(StreamRecords.newRecord().in(AnalyticsKeys.visitEventStreamKey()).ofStrings(fields));
    }
}
```

- [ ] **Step 6: Implement the async projector and remove the old synchronous recorder**

```java
@Component
public class AnalyticsRedirectEventProjectorJob {

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_REDIRECT_EVENT_PROJECTOR_DELAY_MS:1000}")
    public void project() {
        List<MapRecord<String, Object, Object>> records =
                redis.opsForStream().read(Consumer.from("lf-redirect-aggregate", consumerName),
                        StreamReadOptions.empty().count(200),
                        StreamOffset.create(AnalyticsKeys.visitEventStreamKey(), ReadOffset.lastConsumed()));

        for (MapRecord<String, Object, Object> record : safe(records)) {
            aggregateWriter.apply(record.getValue());
            ack(record.getId());
        }
    }
}

public class AnalyticsRedisAggregateWriter {

    public void apply(Map<Object, Object> fields) {
        long tenantId = parseLong(fields.get("tenantId"));
        long linkId = parseLong(fields.get("linkId"));
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        VisitContext visitContext = toVisitContext(fields);
        String visitor = VisitorFingerprint.fingerprint(day, visitContext, analyticsProperties.getSalt());

        redis.opsForValue().increment(AnalyticsKeys.pvKey(tenantId, linkId, day));
        redis.opsForHyperLogLog().add(AnalyticsKeys.uvKey(tenantId, linkId, day), visitor);
        redis.opsForSet().add(AnalyticsKeys.activeSetKey(day), AnalyticsKeys.activeMember(tenantId, linkId));
        redis.opsForStream().add(StreamRecords.newRecord()
                .in(AnalyticsKeys.statsDirtyStreamKey(day))
                .ofStrings(Map.of("member", AnalyticsKeys.activeMember(tenantId, linkId))));

        VisitDimensionNormalizer.Normalized normalized = VisitDimensionNormalizer.normalize(
                visitContext,
                VisitDimensionNormalizer.DEFAULT_MAX_DIM_VALUE_LEN,
                analyticsProperties.getEvents().getMaxUserAgentLength(),
                analyticsProperties.getEvents().getMaxTrackingValueLength()
        );
        recordDimensions(tenantId, linkId, day, normalized, visitor);
    }
}
```

- [ ] **Step 7: Rewire `RedirectService` to use the analytics application service**

```java
public class RedirectService {

    private final LinkCachePort linkCache;
    private final ShortLinkReadService shortLinkReadService;
    private final AnalyticsVisitEventService analyticsVisitEventService;
    private final Clock clock;

    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadService shortLinkReadService,
            AnalyticsVisitEventService analyticsVisitEventService,
            Clock clock
    ) {
        this.linkCache = linkCache;
        this.shortLinkReadService = shortLinkReadService;
        this.analyticsVisitEventService = analyticsVisitEventService;
        this.clock = clock;
    }

    public void recordVisitIfAvailable(LinkMeta meta, RedirectVisitInput visitInput) {
        if (!isAvailable(meta)) {
            return;
        }
        analyticsVisitEventService.appendRedirectVisit(
                new AnalyticsVisitEventService.RedirectVisitEvent(
                        meta.tenantId(),
                        meta.id(),
                        meta.applicationId(),
                        meta.domainId(),
                        meta.code(),
                        meta.hostname(),
                        meta.originalUrl(),
                        toVisitContext(visitInput)
                )
        );
    }
}
```

- [ ] **Step 8: Run focused verification**

Run: `cd server && mvn -q -pl analytics/application,analytics/infrastructure,redirect/application -am -Dtest=AnalyticsVisitEventServiceTest,AnalyticsRedirectEventProjectorJobTest,AnalyticsEventIngestJobPoisonIsolationTest,RedirectServiceTimezoneTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS, and `VisitRecorderService` is no longer compiled or referenced.

- [ ] **Step 9: Commit**

```bash
git add server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java \
        server/analytics/application/src/main/java/com/linkforge/analytics/application/port/AnalyticsVisitEventAppender.java \
        server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java \
        server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedisAggregateWriter.java \
        server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java \
        server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsVisitEventServiceTest.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJobTest.java \
        server/redirect/application/pom.xml \
        server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java \
        server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceTimezoneTest.java
git rm server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java \
       server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/VisitRecorderServiceTest.java
git commit -m "refactor: move analytics aggregation off redirect hot path"
```

## Task 4: Remove The Shortlink Catalog Projector And Enrich Analytics Through `ShortLinkReadService`

**Files:**
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java`
- Modify: `server/analytics/application/pom.xml`
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingService.java`
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java`
- Create: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsReportingApplicationServiceTest.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
- Modify: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Delete: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java`
- Delete: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJobTest.java`
- Modify: `server/analytics/infrastructure/pom.xml`

- [ ] **Step 1: Extend the shortlink read-service test with ownership and summary lookups**

```java
@Test
void listSummaries_returnsCodeAndOriginalUrlByLinkId() {
    ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
    ShortLinkReadService service = new ShortLinkReadApplicationService(repository);

    when(repository.listSummaries(1L, List.of(101L, 102L))).thenReturn(Map.of(
            101L, new ShortLinkReadService.LinkSummary(101L, "abc123", "https://example.com/a", false),
            102L, new ShortLinkReadService.LinkSummary(102L, "xyz789", "https://example.com/b", true)
    ));

    Map<Long, ShortLinkReadService.LinkSummary> actual = service.listSummaries(1L, List.of(101L, 102L));

    assertThat(actual).containsKey(101L).containsKey(102L);
}
```

- [ ] **Step 2: Run the shortlink application tests to verify the new API surface fails**

Run: `cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkReadApplicationServiceTest test`
Expected: FAIL because `LinkSummary` and ownership methods do not exist yet.

- [ ] **Step 3: Add a failing analytics reporting-service test**

```java
class AnalyticsReportingApplicationServiceTest {

    @Test
    void topLinks_shouldEnrichRepositoryRowsWithShortlinkSummaries() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsReportingService service = new AnalyticsReportingApplicationService(queryService, shortLinkReadService);

        when(queryService.topLinks(1L, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-24"), 10, TopSortBy.PV))
                .thenReturn(List.of(new TopLinkStat(101L, null, null, 50L, 40L, false)));
        when(shortLinkReadService.listSummaries(1L, List.of(101L)))
                .thenReturn(Map.of(101L, new ShortLinkReadService.LinkSummary(101L, "abc123", "https://example.com/a", false)));

        List<TopLinkStat> actual = service.topLinks(1L, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-24"), 10, TopSortBy.PV);

        assertThat(actual).containsExactly(new TopLinkStat(101L, "abc123", "https://example.com/a", 50L, 40L, false));
    }
}
```

- [ ] **Step 4: Run the analytics application tests to verify they fail**

Run: `cd server && mvn -q -pl analytics/application -am -Dtest=AnalyticsReportingApplicationServiceTest,AnalyticsExportRequestServiceTest test`
Expected: FAIL because the reporting service and shortlink read methods are missing.

- [ ] **Step 5: Implement ownership, summary lookup, and analytics reporting**

```java
public interface ShortLinkReadService {

    Optional<RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code);

    Optional<LinkOwnership> findOwnership(long tenantId, long linkId);

    Map<Long, LinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    record LinkOwnership(Long applicationId, Long domainId) {
    }

    record LinkSummary(long linkId, String code, String originalUrl, boolean deleted) {
    }
}

@Service
public class AnalyticsReportingApplicationService implements AnalyticsReportingService {

    private final AnalyticsQueryService queryService;
    private final ShortLinkReadService shortLinkReadService;

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        List<TopLinkStat> raw = queryService.topLinks(tenantId, from, to, limit, sortBy);
        List<Long> linkIds = raw.stream().map(TopLinkStat::linkId).toList();
        Map<Long, ShortLinkReadService.LinkSummary> summaries = shortLinkReadService.listSummaries(tenantId, linkIds);

        return raw.stream()
                .map(row -> {
                    ShortLinkReadService.LinkSummary summary = summaries.get(row.linkId());
                    if (summary == null) {
                        return new TopLinkStat(row.linkId(), null, null, row.pv(), row.uv(), true);
                    }
                    return new TopLinkStat(row.linkId(), summary.code(), summary.originalUrl(), row.pv(), row.uv(), summary.deleted());
                })
                .toList();
    }
}
```

- [ ] **Step 6: Replace `ShortLinkOwnershipLookupPort` usage and delete the shortlink-event catalog projector**

```java
public class AnalyticsExportRequestService {

    private final GovernanceRequestService governanceRequestService;
    private final ShortLinkReadService shortLinkReadService;

    private ShortLinkReadService.LinkOwnership requireLinkScope(long tenantId, long linkId) {
        return shortLinkReadService.findOwnership(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "链接不存在"));
    }
}
```

- [ ] **Step 7: Run focused verification**

Run: `cd server && mvn -q -pl shortlink/application,shortlink/infrastructure,analytics/application,analytics/interfaces,analytics/infrastructure -am -Dtest=ShortLinkReadApplicationServiceTest,AnalyticsReportingApplicationServiceTest,AnalyticsExportRequestServiceTest,StatsControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS with no references to `ShortLinkCatalogProjectorJob`.

- [ ] **Step 8: Commit**

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadService.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkReadApplicationService.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkReadRepository.java \
        server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/MybatisShortLinkReadRepository.java \
        server/analytics/application/pom.xml \
        server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingService.java \
        server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsReportingApplicationService.java \
        server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java \
        server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java \
        server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsReportingApplicationServiceTest.java \
        server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java \
        server/analytics/infrastructure/pom.xml
git rm server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java \
       server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJobTest.java
git commit -m "refactor: remove shortlink catalog projector from analytics"
```

## Task 5: Replace Generic Governance Submission With Typed Application APIs

**Files:**
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceRequestService.java`
- Create: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceRequestServiceTest.java`
- Modify: `server/shortlink/application/pom.xml`
- Modify: `server/analytics/application/pom.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
- Modify: `server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java`
- Delete: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapter.java`
- Delete: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapterTest.java`

- [ ] **Step 1: Write the failing governance typed-service test**

```java
class GovernanceRequestServiceTest {

    @Test
    void requestLinkDestinationChange_shouldPersistApprovalAndReturnResult() {
        GovernanceService governanceService = mock(GovernanceService.class);
        GovernanceRequestService service = new GovernanceRequestService(governanceService);

        when(governanceService.submitRequest(eq(1L), any()))
                .thenReturn(new GovernanceService.ApprovalRequestDto(
                        9001L, 1L, SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                        1001L, 42L, "owner@example.com", ApprovalStatus.PENDING_APPROVAL,
                        null, null, null
                ));

        GovernanceRequestService.ApprovalRequestResult actual =
                service.requestLinkDestinationChange(
                        1L, 1001L, "https://before.example", "https://after.example",
                        new UserActor(1L, 42L, "owner@example.com", Set.of(StandardRoles.TENANT_ADMIN)),
                        LocalDateTime.parse("2026-04-24T12:00:00")
                );

        assertThat(actual.id()).isEqualTo(9001L);
        assertThat(actual.operation()).isEqualTo("PUBLIC_LINK_DESTINATION_CHANGE");
    }
}
```

- [ ] **Step 2: Run the governance application test to verify it fails**

Run: `cd server && mvn -q -pl governance/application -am -Dtest=GovernanceRequestServiceTest test`
Expected: FAIL because `GovernanceRequestService` does not exist.

- [ ] **Step 3: Implement the typed governance request API**

```java
@Service
public class GovernanceRequestService {

    private final GovernanceService governanceService;

    public GovernanceRequestService(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    public ApprovalRequestResult requestLinkDestinationChange(
            long tenantId,
            Long applicationId,
            String beforeUrl,
            String afterUrl,
            UserActor actor,
            LocalDateTime requestedAt
    ) {
        GovernanceService.ApprovalRequestDto dto = governanceService.submitRequest(
                tenantId,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                        applicationId,
                        "originalUrl=" + beforeUrl,
                        "originalUrl=" + afterUrl,
                        actor,
                        requestedAt
                )
        );
        return ApprovalRequestResult.from(dto);
    }

    public ApprovalRequestResult requestAnalyticsDetailExport(
            long tenantId,
            Long applicationId,
            long linkId,
            LocalDateTime from,
            LocalDateTime to,
            UserActor actor,
            LocalDateTime requestedAt
    ) {
        GovernanceService.ApprovalRequestDto dto = governanceService.submitRequest(
                tenantId,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                        applicationId,
                        null,
                        "linkId=" + linkId + ",from=" + from + ",to=" + to,
                        actor,
                        requestedAt
                )
        );
        return ApprovalRequestResult.from(dto);
    }

    public record ApprovalRequestResult(long id, String operation, String status) {
        static ApprovalRequestResult from(GovernanceService.ApprovalRequestDto dto) {
            return new ApprovalRequestResult(dto.id(), dto.operationType().name(), dto.status().name());
        }
    }
}
```

- [ ] **Step 4: Migrate `shortlink` and `analytics` callers off the generic contract**

```java
governanceRequestService.requestLinkDestinationChange(
        tenantId,
        link.applicationId(),
        link.originalUrl().value(),
        req.originalUrl(),
        actor,
        requestedAt
);
```

```java
return governanceRequestService.requestAnalyticsDetailExport(
        actor.tenantId(),
        ownership.applicationId(),
        linkId,
        effectiveFrom,
        effectiveTo,
        actor,
        nowUtc()
);
```

- [ ] **Step 5: Run focused verification**

Run: `cd server && mvn -q -pl governance/application,shortlink/application,analytics/application -am -Dtest=GovernanceRequestServiceTest,UpdateShortLinkCommandHandlerTest,AnalyticsExportRequestServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS, and no code path still references `ApprovalSubmissionPort`.

- [ ] **Step 6: Commit**

```bash
git add server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceRequestService.java \
        server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceRequestServiceTest.java \
        server/shortlink/application/pom.xml \
        server/analytics/application/pom.xml \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java \
        server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java \
        server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java \
        server/analytics/application/src/test/java/com/linkforge/analytics/application/AnalyticsExportRequestServiceTest.java
git rm server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapter.java \
       server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceApprovalSubmissionAdapterTest.java
git commit -m "refactor: replace generic approval submission with typed governance APIs"
```

## Task 6: Remove Low-Value Contracts, Eventing, And Blanket Context Rules

**Files:**
- Delete: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaSourcePort.java`
- Delete: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/VisitRecorderPort.java`
- Delete: `server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkOwnershipLookupPort.java`
- Delete: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalSubmissionPort.java`
- Delete: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalRequestView.java`
- Delete: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkEventPublisher.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventAppender.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactory.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventPublisherAdapter.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java`
- Delete: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/ShortLinkOwnershipLookupAdapter.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
- Delete: `server/integration-tests/src/test/java/com/linkforge/ShortLinkIntegrationEventAppendIntegrationTest.java`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Modify: `server/redirect/application/pom.xml`
- Modify: `server/analytics/application/pom.xml`
- Modify: `server/shortlink/application/pom.xml`

- [ ] **Step 1: Remove shortlink event-publisher expectations from command tests**

```java
verify(postCommitHookPort).run(any());
verify(shortLinkRepository).update(link);
verifyNoInteractions(shortLinkEventPublisher);
```

- [ ] **Step 2: Run the shortlink command tests to verify they fail before the cleanup**

Run: `cd server && mvn -q -pl shortlink/application -am -Dtest=CreateShortLinkCommandHandlerTest,UpdateShortLinkCommandHandlerTest test`
Expected: FAIL until constructors and mocks stop referencing `ShortLinkEventPublisher`.

- [ ] **Step 3: Remove dead contracts and shortlink integration-event plumbing**

```java
public class CreateShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final SetLinkTagsCommandHandler setLinkTagsHandler;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDtoMapper dtoMapper;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;

    public LinkDto handle(long tenantId, CreatedBy createdBy, CreateLinkRequest req) {
        shortLinkRepository.insert(link);
        ShortLink persisted = shortLinkRepository.findByTenantIdAndId(tenantId, id).orElse(link);
        setLinkTagsHandler.handle(tenantId, id, req.tags());
        postCommitHookPort.run(() -> redirectCacheSync.evict(persisted.tenantId(), persisted.domainId(), persisted.code().value()));
        return dtoMapper.toDto(persisted, tags);
    }
}
```

- [ ] **Step 4: Replace the blanket bounded-context rule with layer-focused guards**

```java
private static final List<BoundedContext> BOUNDED_CONTEXTS = List.of(
        new BoundedContext("accounts", "com.linkforge.accounts"),
        new BoundedContext("shortlink", "com.linkforge.shortlink"),
        new BoundedContext("redirect", "com.linkforge.redirect"),
        new BoundedContext("analytics", "com.linkforge.analytics"),
        new BoundedContext("platform", "com.linkforge.platform"),
        new BoundedContext("governance", "com.linkforge.governance")
);

@Test
void applications_may_depend_on_foreign_application_exports_but_not_foreign_internal_layers() {
    for (BoundedContext from : BOUNDED_CONTEXTS) {
        String[] forbiddenTargets = BOUNDED_CONTEXTS.stream()
                .filter(to -> !to.name().equals(from.name()))
                .flatMap(to -> Stream.of(
                        to.basePackage() + ".domain..",
                        to.basePackage() + ".infrastructure..",
                        to.basePackage() + ".interfaces.."
                ))
                .toArray(String[]::new);

        noClasses()
                .that().resideInAnyPackage(from.basePackage() + ".application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(forbiddenTargets)
                .because("application code may call exported foreign application services, but not foreign internal layers")
                .check(CLASSES);
    }
}

@Test
void infrastructure_should_not_depend_on_other_contexts_infrastructure_or_interfaces() {
    for (BoundedContext from : BOUNDED_CONTEXTS) {
        String[] forbiddenTargets = BOUNDED_CONTEXTS.stream()
                .filter(to -> !to.name().equals(from.name()))
                .flatMap(to -> Stream.of(
                        to.basePackage() + ".infrastructure..",
                        to.basePackage() + ".interfaces.."
                ))
                .toArray(String[]::new);

        noClasses()
                .that().resideInAnyPackage(from.basePackage() + ".infrastructure..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(forbiddenTargets)
                .check(CLASSES);
    }
}

private record BoundedContext(String name, String basePackage) {
}
```

- [ ] **Step 5: Run full backend verification**

Run: `cd server && mvn -q test`
Expected: PASS.

Run: `cd server && mvn -q -Pit test`
Expected: PASS when Docker/Testcontainers are available.

- [ ] **Step 6: Commit**

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java \
        server/redirect/application/pom.xml \
        server/analytics/application/pom.xml \
        server/shortlink/application/pom.xml \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java \
        server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java \
        server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java \
        server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java
git rm server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaSourcePort.java \
       server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/VisitRecorderPort.java \
       server/contracts/shortlink/src/main/java/com/linkforge/contract/shortlink/ShortLinkOwnershipLookupPort.java \
       server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalSubmissionPort.java \
       server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalRequestView.java \
       server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkEventPublisher.java \
       server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventAppender.java \
       server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventFactory.java \
       server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/eventing/ShortLinkEventPublisherAdapter.java \
       server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java \
       server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/ShortLinkOwnershipLookupAdapter.java \
       server/integration-tests/src/test/java/com/linkforge/ShortLinkIntegrationEventAppendIntegrationTest.java
git commit -m "refactor: remove monolith-internal boundary indirection"
```

## Self-Review

### Spec Coverage

- Redirect read-through simplification: covered by Tasks 1-2.
- Redirect hot-path analytics reduction: covered by Task 3.
- Analytics enrichment through shortlink read API: covered by Task 4.
- Governance typed APIs: covered by Task 5.
- Contract and architecture rule cleanup: covered by Task 6.

No approved spec section is left without an implementation task.

### Placeholder Scan

- No `TODO`, `TBD`, or "implement later" placeholders remain.
- All code-changing steps include concrete code blocks.
- All test steps include concrete test code or exact commands.

### Type Consistency

- `ShortLinkReadService` is the single internal read API name used across redirect and analytics tasks.
- `AnalyticsVisitEventService` is the single redirect-to-analytics hot-path service name used across redirect and analytics tasks.
- `GovernanceRequestService` is the single typed approval API used across shortlink and analytics tasks.

## Final Verification Checklist

- Redirect requests no longer reference redirect projection classes.
- Redirect cache misses call `ShortLinkReadService`.
- Redirect request handling appends one lightweight analytics event instead of running full aggregation writes.
- Analytics no longer runs a shortlink-event-driven catalog projector.
- Approval callers no longer use `ApprovalSubmissionPort`.
- Shortlink mutation handlers no longer append integration events for removed in-process consumers.
- `ArchitectureTest` protects layer boundaries without forbidding intentional direct application-service calls across bounded contexts.

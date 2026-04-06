# LinkForge Backend Architecture Phased Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the backend into a stricter modular monolith by removing hidden runtime-context dependencies from application code, slimming controllers into adapters, splitting `foundation` and `accounts` into real Maven module boundaries, and replacing broad package-scan composition with explicit module exports.

**Architecture:** Work boundary-first and phase-by-phase. First replace runtime thread-context coupling with explicit application inputs and ports. Then move controller orchestration into use cases and remove transport leakage from application APIs. After the logical boundaries hold, perform the physical module split for `foundation` and `accounts`, then tighten `app` into the only explicit composition root.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, MyBatis, Redis, Maven reactor, ArchUnit, JUnit 5, Testcontainers.

---

## Scope

This plan covers `server/` only. `web/` is intentionally out of scope.

Breaking changes are allowed for:

- internal Java APIs
- HTTP API shape
- configuration names
- persistence shape

The deliverable is five independently verifiable backend phases:

1. remove implicit runtime context from application
2. move business orchestration out of controllers and remove transport leakage from application APIs
3. split `foundation` into library/runtime modules
4. split `accounts` into layered Maven modules
5. replace broad bounded-context scans with explicit module exports

## File/Module Map

### Phase 1: Explicit application context and post-commit hooks
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/context/ApplicationActor.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/context/UserActor.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/context/ApiKeyActor.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/tx/PostCommitHookPort.java`
- Create: `server/app/src/main/java/com/linkforge/app/tx/SpringPostCommitHookAdapter.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ImportShortLinksCsvCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/SearchShortLinksQueryHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/GetShortLinkDetailQueryHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/ListTagsQueryHandler.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/LegacyApplicationBindingService.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Create: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectVisitInput.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
- Modify/Test: `server/platform/application/src/test/java/com/linkforge/platform/application/PlatformApplicationScopeAdapterTest.java`

### Phase 2: Controller slimming and transport leak removal
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/csv/ShortLinkCsvImportRow.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/csv/ShortLinkCsvExportRow.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/csv/ShortLinkCsvExport.java`
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
- Create: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectResolution.java`
- Create: `server/redirect/application/src/main/java/com/linkforge/redirect/application/ResolveRedirectRequest.java`
- Create: `server/app/src/main/java/com/linkforge/app/security/PrincipalActorMapper.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Modify: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkCsvHttpMapper.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHttpRequestMapper.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHttpResponseWriter.java`
- Modify/Test: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`
- Modify/Test: `server/shortlink/interfaces/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkHttpMapperTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerPreviewConfirmHrefLimitTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerExpiryBoundaryTest.java`

### Phase 3: Split foundation into library/runtime modules
- Modify: `server/foundation/pom.xml`
- Create: `server/foundation/core/pom.xml`
- Create: `server/foundation/runtime-web/pom.xml`
- Create: `server/foundation/runtime-security/pom.xml`
- Create: `server/foundation/runtime-persistence/pom.xml`
- Create: `server/foundation/runtime-tx/pom.xml`
- Create: `server/foundation/runtime-startup/pom.xml`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/config/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/id/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/util/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/persistence/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/StandardRoles.java`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthContext.java`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/security/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/web/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/persistence/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/time/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/startup/*`
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`
- Modify: `server/pom.xml`
- Modify: context POMs under `server/app`, `server/shortlink/*`, `server/platform/*`, `server/governance/*`, `server/redirect/*`, `server/analytics/*`, `server/accounts/*`

### Phase 4: Split accounts into layered Maven modules
- Modify: `server/accounts/pom.xml`
- Create: `server/accounts/domain/pom.xml`
- Create: `server/accounts/application/pom.xml`
- Create: `server/accounts/infrastructure/pom.xml`
- Create: `server/accounts/interfaces/pom.xml`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/domain/*`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/*`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/port/*`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/**/*`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/interfaces/web/*`
- Move/Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/*`
- Move/Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/interfaces/web/*`
- Modify: `server/app/pom.xml`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

### Phase 5: Explicit module export and composition root
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/FoundationModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/AccountsModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/ShortlinkModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/RedirectModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/AnalyticsModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/PlatformModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/GovernanceModule.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/ShortlinkInterfacesConfig.java`
- Create: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/ShortlinkInfrastructureConfig.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/ShortlinkRuntimeModule.java`
- Create: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/AccountsInterfacesConfig.java`
- Create: `server/accounts/infrastructure/src/main/java/com/linkforge/accounts/infrastructure/AccountsInfrastructureConfig.java`
- Create: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/AccountsRuntimeModule.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/RedirectInterfacesConfig.java`
- Create: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/RedirectInfrastructureConfig.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/RedirectRuntimeModule.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/AnalyticsInterfacesConfig.java`
- Create: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/AnalyticsInfrastructureConfig.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/AnalyticsRuntimeModule.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/PlatformInterfacesConfig.java`
- Create: `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/PlatformInfrastructureConfig.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/PlatformRuntimeModule.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/GovernanceInterfacesConfig.java`
- Create: `server/governance/infrastructure/src/main/java/com/linkforge/governance/infrastructure/GovernanceInfrastructureConfig.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/GovernanceRuntimeModule.java`
- Modify: `server/app/src/test/java/com/linkforge/architecture/AppModuleCompositionTest.java`
- Modify: `docs/architecture.md`

---

### Task 1: Remove Hidden Runtime Context From Application

**Files:**
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/context/ApplicationActor.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/context/UserActor.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/context/ApiKeyActor.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/tx/PostCommitHookPort.java`
- Create: `server/app/src/main/java/com/linkforge/app/tx/SpringPostCommitHookAdapter.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
- Create: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectVisitInput.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java`
- Modify/Test: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
- Modify/Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectServiceAuthoritativeFallbackTest.java`

- [ ] **Step 1: Write failing architecture guards**

Add rules to `ArchitectureTest` that fail when `..application..` depends on runtime thread-context helpers:

```java
@Test
void application_should_not_depend_on_runtime_context_helpers() {
    noClasses()
            .that().resideInAnyPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.linkforge.foundation.security.AuthContext",
                    "com.linkforge.foundation.web..",
                    "com.linkforge.foundation.runtime..",
                    "org.springframework.transaction.support..",
                    "org.springframework.security.core.context.."
            )
            .check(CLASSES);
}
```

Run: `cd server && mvn -q -pl app -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL on `shortlink.application`, `platform.application`, `governance.application`, and `redirect.application`.

- [ ] **Step 2: Introduce explicit actor and post-commit abstractions**

Create small framework-free input and hook types:

```java
public sealed interface ApplicationActor permits UserActor, ApiKeyActor {
    long tenantId();
}

public record UserActor(long tenantId, long userId, String email, Set<String> roles) implements ApplicationActor {}

public record ApiKeyActor(long tenantId, long apiKeyId, Long applicationId) implements ApplicationActor {}

public interface PostCommitHookPort {
    void run(Runnable action);
}
```

Back the hook with a Spring adapter:

```java
@Component
public final class SpringPostCommitHookAdapter implements PostCommitHookPort {
    @Override
    public void run(Runnable action) {
        AfterCommit.run(action);
    }
}
```

- [ ] **Step 3: Refactor application services onto explicit inputs**

Change targeted services so they take explicit actors, tenant IDs, request times, and post-commit hooks instead of reading runtime state:

```java
@Transactional
public LinkDto handle(UserActor actor, CreateLinkRequest req) {
    long tenantId = actor.tenantId();
    // ...
    postCommitHook.run(() -> redirectCacheSync.evict(...));
}
```

```java
@Transactional
public ApprovalRequestDto submitRequest(UserActor actor, SubmitApprovalRequest request, Instant requestedAt) {
    LocalDateTime now = LocalDateTime.ofInstant(requestedAt, ZoneOffset.UTC);
    // ...
}
```

```java
public void recordVisitIfAvailable(LinkMeta meta, RedirectVisitInput visit, Instant occurredAt) {
    if (isAvailable(meta, occurredAt)) {
        visitRecorder.recordVisit(meta.tenantId(), meta.id(), visit.toContext());
    }
}
```

- [ ] **Step 4: Update unit tests to compile and pass with explicit inputs**

Refactor tests to construct actors and clocks explicitly instead of relying on hidden runtime:

```java
UserActor actor = new UserActor(tenantId, userId, "admin@example.com", Set.of("TENANT_ADMIN"));
CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
        idGenerator, repository, setTags, linkTags, eventPublisher, redirectCacheSync,
        dtoMapper, clock, applicationScopePort, postCommitHook
);
```

Run: `cd server && mvn -q -pl app,shortlink,platform,governance,redirect -am -Dtest=ArchitectureTest,CreateShortLinkCommandHandlerTest,UpdateShortLinkCommandHandlerTest,RedirectServiceAuthoritativeFallbackTest,PlatformApplicationScopeAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java \
        server/foundation/src/main/java/com/linkforge/foundation/context \
        server/foundation/src/main/java/com/linkforge/foundation/tx/PostCommitHookPort.java \
        server/app/src/main/java/com/linkforge/app/tx/SpringPostCommitHookAdapter.java \
        server/shortlink/application \
        server/platform/application \
        server/governance/application \
        server/redirect/application
git commit -m "refactor: remove hidden runtime context from application"
```

---

### Task 2: Move Controller Orchestration Into Use Cases And Remove Application Transport Leakage

**Files:**
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/csv/ShortLinkCsvImportRow.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/csv/ShortLinkCsvExportRow.java`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/csv/ShortLinkCsvExport.java`
- Create: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
- Create: `server/redirect/application/src/main/java/com/linkforge/redirect/application/ResolveRedirectRequest.java`
- Create: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectResolution.java`
- Create: `server/app/src/main/java/com/linkforge/app/security/PrincipalActorMapper.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Modify: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
- Create: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkCsvHttpMapper.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHttpRequestMapper.java`
- Create: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHttpResponseWriter.java`
- Modify/Test: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`
- Modify/Test: `server/shortlink/interfaces/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkHttpMapperTest.java`
- Modify/Test: `server/redirect/interfaces/src/test/java/com/linkforge/redirect/interfaces/web/RedirectControllerPreviewConfirmHrefLimitTest.java`

- [ ] **Step 1: Write failing transport-boundary tests**

Extend architecture coverage so application service interfaces can no longer expose stream or servlet types, and add focused controller tests that lock current behavior before moving logic:

```java
@Test
void application_service_signatures_should_not_expose_stream_or_servlet_types() {
    noClasses()
            .that().resideInAnyPackage("..application..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("java.io.InputStream");
}
```

Run: `cd server && mvn -q -pl app,analytics/interfaces,shortlink/interfaces,redirect/interfaces -am -Dtest=ArchitectureTest,StatsControllerTest,ShortLinkHttpMapperTest,RedirectControllerPreviewConfirmHrefLimitTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL because `ShortLinkService` still exposes stream I/O and controllers still own orchestration logic.

- [ ] **Step 2: Replace application stream APIs with explicit CSV models**

Change `ShortLinkService` so it consumes and returns pure application models:

```java
ImportResult importCsv(UserActor actor, List<ShortLinkCsvImportRow> rows);

ShortLinkCsvExport exportCsv(UserActor actor, ShortLinkSearchQuery query, PageQuery pageQuery);
```

Keep actual CSV parsing and response streaming in interfaces:

```java
public final class ShortLinkCsvHttpMapper {
    List<ShortLinkCsvImportRow> parse(MultipartFile file) { ... }
    void write(ShortLinkCsvExport export, HttpServletResponse response) { ... }
}
```

- [ ] **Step 3: Create application use cases for controller-owned orchestration**

Introduce explicit use cases for analytics export approval and redirect resolution:

```java
public final class AnalyticsExportRequestService {
    public ApprovalRequestView requestLinkEventExport(UserActor actor, long linkId, LocalDateTime from, LocalDateTime to) { ... }
}
```

```java
public record RedirectResolution(RedirectKind kind, LinkMeta meta, UnavailableReason unavailableReason, boolean countVisit) {}

public RedirectResolution resolve(ResolveRedirectRequest request) { ... }
```

- [ ] **Step 4: Slim controllers into protocol adapters**

Refactor target controllers so they only decode transport, map principal to actor, call one use case, and encode HTTP:

```java
@PostMapping("/stats/links/{id}/events/export-requests")
public ApiResponse<ApprovalRequestView> requestEventExport(...) {
    UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
    return ApiResponse.ok(exportRequestService.requestLinkEventExport(actor, linkId, effectiveFrom, effectiveTo), RequestId.get());
}
```

```java
@GetMapping("/{code}")
public ResponseEntity<?> redirect(...) {
    ResolveRedirectRequest appRequest = redirectHttpRequestMapper.fromHttp(code, request);
    RedirectResolution resolution = redirectService.resolve(appRequest);
    return redirectHttpResponseWriter.write(resolution, request);
}
```

- [ ] **Step 5: Run focused verification**

Run: `cd server && mvn -q -pl app,analytics/interfaces,shortlink/interfaces,redirect/interfaces,shortlink/application,redirect/application -am -Dtest=ArchitectureTest,StatsControllerTest,ShortLinkHttpMapperTest,RedirectControllerPreviewConfirmHrefLimitTest,RedirectControllerExpiryBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/shortlink/application \
        server/shortlink/interfaces \
        server/analytics/application \
        server/analytics/interfaces \
        server/redirect/application \
        server/redirect/interfaces \
        server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "refactor: move controller orchestration into use cases"
```

---

### Task 3: Split Foundation Into Library And Runtime Maven Modules

**Files:**
- Modify: `server/foundation/pom.xml`
- Create: `server/foundation/core/pom.xml`
- Create: `server/foundation/runtime-web/pom.xml`
- Create: `server/foundation/runtime-security/pom.xml`
- Create: `server/foundation/runtime-persistence/pom.xml`
- Create: `server/foundation/runtime-tx/pom.xml`
- Create: `server/foundation/runtime-startup/pom.xml`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/config/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/id/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/util/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/persistence/*`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/StandardRoles.java`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthContext.java`
- Move/Modify: `server/foundation/src/main/java/com/linkforge/foundation/runtime/**/*`
- Modify: `server/foundation/src/test/java/com/linkforge/foundation/architecture/FoundationSharedArchitectureTest.java`
- Modify: `server/pom.xml`
- Modify: backend module POMs consuming foundation

- [ ] **Step 1: Write the failing module-boundary tests**

Broaden `FoundationSharedArchitectureTest` so the pure library surface fails if it depends on Spring Web, Spring Security, or MyBatis:

```java
noClasses()
        .that().resideInAnyPackage("com.linkforge.foundation.config..", "com.linkforge.foundation.id..", "com.linkforge.foundation.util..", "com.linkforge.foundation.persistence..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..", "org.springframework.security..", "org.mybatis..");
```

Run: `cd server && mvn -q -pl foundation -am -Dtest=FoundationSharedArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL until the split is in place.

- [ ] **Step 2: Convert `server/foundation` into an aggregator**

Change `server/foundation/pom.xml` to `packaging=pom` and add child modules:

```xml
<modules>
  <module>core</module>
  <module>runtime-web</module>
  <module>runtime-security</module>
  <module>runtime-persistence</module>
  <module>runtime-tx</module>
  <module>runtime-startup</module>
</modules>
```

- [ ] **Step 3: Move pure library code into `foundation/core` and runtime adapters into dedicated modules**

Use `git mv` to relocate files and then update package imports and POM dependencies. The intended ownership is:

```text
foundation/core            -> config, id, util, persistence models, actor records, shared roles
foundation/runtime-web     -> RequestId, RequestIdFilter, CorsConfig
foundation/runtime-security-> AuthContext and runtime principal helpers
foundation/runtime-persistence -> IntegrationEvent* MyBatis wiring and eventing adapters
foundation/runtime-tx      -> SpringPostCommitHookAdapter and transaction glue
foundation/runtime-startup -> StartupCheck and startup validation wiring
```

- [ ] **Step 4: Rewire backend modules to narrow foundation dependencies**

Example dependency split:

```xml
<dependency>
  <groupId>com.linkforge</groupId>
  <artifactId>linkforge-foundation-core</artifactId>
</dependency>
<dependency>
  <groupId>com.linkforge</groupId>
  <artifactId>linkforge-foundation-runtime-security</artifactId>
</dependency>
```

Only runtime-facing modules should depend on runtime artifacts by default.

- [ ] **Step 5: Run focused verification**

Run: `cd server && mvn -q -pl foundation,app,shortlink,redirect,platform,governance,analytics -am -Dtest=FoundationSharedArchitectureTest,ArchitectureTest,ApiStartupValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/foundation server/pom.xml server/app server/shortlink server/redirect server/platform server/governance server/analytics
git commit -m "refactor: split foundation into library and runtime modules"
```

---

### Task 4: Split Accounts Into Domain/Application/Infrastructure/Interfaces Modules

**Files:**
- Modify: `server/accounts/pom.xml`
- Create: `server/accounts/domain/pom.xml`
- Create: `server/accounts/application/pom.xml`
- Create: `server/accounts/infrastructure/pom.xml`
- Create: `server/accounts/interfaces/pom.xml`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/domain/AccountsConstants.java`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AccountStatusService.java`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/port/*`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/**/*`
- Move/Modify: `server/accounts/src/main/java/com/linkforge/accounts/interfaces/web/*`
- Move/Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/application/*`
- Move/Modify/Test: `server/accounts/src/test/java/com/linkforge/accounts/interfaces/web/*`
- Modify: `server/app/pom.xml`
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

- [ ] **Step 1: Write the failing compile-time and architecture checks**

Update `ArchitectureTest` so `accounts.application` is no longer allowed to see `accounts.infrastructure`, and add module build execution that proves the split is real:

```java
@Test
void accounts_application_should_not_depend_on_accounts_infrastructure() {
    noClasses()
            .that().resideInAnyPackage("com.linkforge.accounts.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.linkforge.accounts.infrastructure..")
            .check(CLASSES);
}
```

Run: `cd server && mvn -q -pl app,accounts -am -Dtest=ArchitectureTest,AuthServiceTest,ApiKeyServiceTest,UserAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL until files are physically split.

- [ ] **Step 2: Convert `server/accounts` into a Maven aggregator**

Update `server/accounts/pom.xml`:

```xml
<packaging>pom</packaging>
<modules>
  <module>domain</module>
  <module>application</module>
  <module>infrastructure</module>
  <module>interfaces</module>
</modules>
```

Create child POMs mirroring the structure already used by `shortlink`, `platform`, `redirect`, `analytics`, and `governance`.

- [ ] **Step 3: Move accounts code into the new physical layers**

Use `git mv` to relocate source and test files. Keep the dependency shape:

```text
accounts/domain         -> constants and domain vocabulary
accounts/application    -> services and application ports
accounts/infrastructure -> persistence/cache/security adapters
accounts/interfaces     -> HTTP controllers
```

Only `accounts.interfaces` should depend on Spring Web/Security transport APIs; only `accounts.infrastructure` should depend on MyBatis/Redis/JWT implementation types.

- [ ] **Step 4: Update app and tests to the new artifact layout**

Adjust `server/app/pom.xml`, bean wiring, and relocated tests so the application still boots with the split modules on the classpath.

Run: `cd server && mvn -q -pl app,accounts -am -Dtest=ArchitectureTest,AuthServiceTest,ApiKeyServiceTest,UserAdminServiceTest,AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/accounts server/app/pom.xml server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "refactor: split accounts into layered modules"
```

---

### Task 5: Replace Broad Package Scans With Explicit Module Exports

**Files:**
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/FoundationModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/AccountsModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/ShortlinkModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/RedirectModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/AnalyticsModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/PlatformModule.java`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/GovernanceModule.java`
- Create: explicit export configs under each bounded context
- Modify: `server/app/src/test/java/com/linkforge/architecture/AppModuleCompositionTest.java`
- Modify: `docs/architecture.md`

- [ ] **Step 1: Write the failing composition test**

Expand `AppModuleCompositionTest` so it proves `app` imports explicit context modules instead of relying on broad context scans:

```java
@Test
void app_should_import_explicit_context_exports_only() {
    assertThat(LinkForgeApplication.class.getAnnotation(Import.class)).isNotNull();
    // assert imported classes are explicit runtime configs, not scan-all wrappers
}
```

Run: `cd server && mvn -q -pl app -am -Dtest=AppModuleCompositionTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL while broad scan wrappers are still the bootstrap mechanism.

- [ ] **Step 2: Add explicit export configs to each bounded context**

Create narrow runtime export entry points such as `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/ShortlinkRuntimeModule.java`:

```java
@Configuration
@Import({
        ShortlinkApplicationConfig.class,
        ShortlinkInfrastructureConfig.class,
        ShortlinkInterfacesConfig.class
})
public class ShortlinkRuntimeModule {}
```

Each child config may use targeted `@ComponentScan(basePackageClasses = MarkerType.class)` for its own layer, but `app` must stop scanning an entire bounded context package.

- [ ] **Step 3: Rewire `LinkForgeApplication` to import only explicit exports**

The boot app should look like:

```java
@Import({
        FoundationRuntimeModule.class,
        AccountsRuntimeModule.class,
        ShortlinkRuntimeModule.class,
        RedirectRuntimeModule.class,
        AnalyticsRuntimeModule.class,
        PlatformRuntimeModule.class,
        GovernanceRuntimeModule.class
})
```

At this point the old broad-scan compose wrappers should either be deleted or reduced to explicit imports only.

- [ ] **Step 4: Run full backend verification**

Run: `cd server && mvn -q -f server/pom.xml test`
Expected: PASS.

Run: `cd server && mvn -q -pl integration-tests -P it -Dtest=ControlPlaneEndToEndIntegrationTest,RedirectRiskControlIntegrationTest test`
Expected: PASS.

- [ ] **Step 5: Update architecture documentation**

Align `docs/architecture.md` with the final backend module graph, the new foundation split, the accounts split, and the explicit runtime export pattern.

- [ ] **Step 6: Commit**

```bash
git add server/app server/shortlink server/redirect server/analytics server/platform server/governance server/accounts docs/architecture.md
git commit -m "refactor: make module composition explicit"
```

---

## Final Verification Checklist

- [ ] `cd server && mvn -q -pl app -am -Dtest=ArchitectureTest,AppModuleCompositionTest,FoundationSharedArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test`
- [ ] `cd server && mvn -q -f server/pom.xml test`
- [ ] `cd server && mvn -q -pl integration-tests -P it -Dtest=ControlPlaneEndToEndIntegrationTest,RedirectRiskControlIntegrationTest test`
- [ ] Confirm `application` packages no longer import `AuthContext`, `RequestId`, `VisitInfo`, `foundation.runtime..`, or Spring transaction/security context holders
- [ ] Confirm `ShortLinkService` and peer application APIs no longer expose stream/servlet transport types
- [ ] Confirm `accounts` and `foundation` reactor structures match the documented architecture
- [ ] Confirm `LinkForgeApplication` imports explicit runtime module exports instead of broad bounded-context scans

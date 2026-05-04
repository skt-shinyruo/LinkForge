# LinkForge Backend Architecture Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor LinkForge backend boundaries so HTTP contracts, application use cases, domain rules, contracts, foundation security, and bootstrap wiring match the approved modular-monolith architecture.

**Architecture:** Keep one Spring Boot modular monolith. Make `interfaces` own HTTP DTOs, make `application` own use-case orchestration and top-level request/result models, move `platform` invariants into `domain`, keep `foundation` technical, keep `contracts` narrow, and enforce the result with ArchUnit checks.

**Tech Stack:** Java 17, Spring Boot 3.2, Maven multi-module, ArchUnit, JUnit 5, Mockito, MyBatis.

---

## File Structure Map

**Architecture guardrails**

- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
- Test command: `mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test`

**Platform HTTP DTO cleanup**

- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/ApplicationHttpResponse.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/DomainHttpResponse.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/PlatformHttpMapper.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminApplicationController.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminDomainController.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/PlatformAdminController.java`

**Governance HTTP DTO cleanup and application result extraction**

- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/SubmitApprovalRequest.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/ApprovalRequestResult.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/AuditLogResult.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/ApprovalRequestHttpResponse.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/AuditLogHttpResponse.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/GovernanceHttpMapper.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalApplicationService.java`
- Modify: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceServiceTest.java`
- Modify: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/ApprovalController.java`
- Modify: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/AuditController.java`

**Analytics HTTP DTO cleanup**

- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/DailyStatHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/TopLinkStatHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/DimensionStatHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/VisitEventHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/AnalyticsHttpMapper.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`

**Accounts HTTP DTO cleanup and application result extraction**

- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/CreateUserCommand.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/AuthResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/UserResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/CreatedApiKeyResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyInfoResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyAuthResult.java`
- Create: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/UserHttpResponse.java`
- Create: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/AccountsHttpMapper.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/application/src/test/java/com/linkforge/accounts/application/AuthServiceTest.java`
- Modify: `server/accounts/application/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`
- Modify: `server/accounts/application/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Modify: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/AuthController.java`
- Modify: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/UserAdminController.java`
- Modify: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/ApiKeyAdminController.java`

**Platform domain hardening**

- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Hostname.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/PlatformDefaults.java`
- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainAuthorizationPolicy.java`
- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainAuthorizationException.java`
- Modify: `server/platform/domain/src/test/java/com/linkforge/platform/domain/DomainAuthorizationPolicyTest.java`
- Create: `server/platform/domain/src/test/java/com/linkforge/platform/domain/HostnameTest.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
- Modify: `server/platform/application/src/test/java/com/linkforge/platform/application/ApplicationProvisioningServiceTest.java`

**Foundation identity narrowing**

- Create: `server/foundation/core/src/main/java/com/linkforge/foundation/security/ApiKeyAuthenticationDetails.java`
- Modify: `server/foundation/core/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Modify: `server/foundation/core/src/main/java/com/linkforge/foundation/security/ApiKeyAuthenticationResult.java`
- Modify: `server/foundation/runtime-security/src/main/java/com/linkforge/foundation/runtime/security/PrincipalActorMapper.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- Modify: related tests under `server/app/src/test/java/com/linkforge/app/security/`

**Contracts and bootstrap cleanup**

- Create or modify narrow governance payload files under `server/contracts/governance/src/main/java/com/linkforge/contract/governance/`
- Modify: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalPayloads.java`
- Modify: `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalPayloadCodec.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Modify or create focused config classes under `server/app/src/main/java/com/linkforge/app/security/`
- Modify: `server/app/src/main/java/com/linkforge/app/api/error/GlobalExceptionHandler.java` only if handler scope must change after tests

---

### Task 1: Add Architecture Guardrails That Expose Current Drift

**Files:**
- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

- [ ] **Step 1: Add a generic controller-return-type guard**

Add this test method to `ArchitectureTest` near `shortlink_controllers_should_not_expose_application_link_dto_as_http_contract()`:

```java
    @Test
    void controllers_should_not_expose_application_dtos_as_http_contracts() {
        List<String> violations = CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .map(ArchitectureTest::loadClass)
                .flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods()))
                .filter(method -> typeMentionsPackage(method.getGenericReturnType(), ".application."))
                .map(method -> method.getDeclaringClass().getName() + "#" + method.getName())
                .sorted()
                .toList();

        assertThat(violations)
                .withFailMessage("Controllers must map application results to interface-owned HTTP DTOs: %s", violations)
                .isEmpty();
    }
```

Add this helper near `typeMentions(...)`:

```java
    private static boolean typeMentionsPackage(Type type, String packageSegment) {
        if (type == null) {
            return false;
        }
        if (type instanceof Class<?> clazz) {
            return clazz.getName().contains(packageSegment);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (typeMentionsPackage(parameterizedType.getRawType(), packageSegment)) {
                return true;
            }
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(t -> typeMentionsPackage(t, packageSegment));
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return typeMentionsPackage(genericArrayType.getGenericComponentType(), packageSegment);
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds()).anyMatch(t -> typeMentionsPackage(t, packageSegment))
                    || Arrays.stream(wildcardType.getLowerBounds()).anyMatch(t -> typeMentionsPackage(t, packageSegment));
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            return Arrays.stream(typeVariable.getBounds()).anyMatch(t -> typeMentionsPackage(t, packageSegment));
        }
        return false;
    }
```

- [ ] **Step 2: Add a guard for nested public application DTO records**

Add this test method to `ArchitectureTest`:

```java
    @Test
    void application_services_should_not_own_nested_public_dto_records() {
        List<String> violations = CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().contains(".application"))
                .filter(javaClass -> javaClass.getName().matches(".*\\$.*(Dto|Result|Request|Command|Info|Created).*"))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(violations)
                .withFailMessage("Application DTO/request/result models must be top-level types: %s", violations)
                .isEmpty();
    }
```

- [ ] **Step 3: Run the architecture tests and verify they fail**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test
```

Expected: FAIL. The failure list should include controllers such as `TenantAdminApplicationController`, `TenantAdminDomainController`, `PlatformAdminController`, `ApprovalController`, `AuditController`, `StatsController`, and `UserAdminController`, plus nested application records such as `AuthService$AuthResult`, `GovernanceService$SubmitApprovalRequest`, `GovernanceService$ApprovalRequestDto`, `GovernanceService$AuditLogDto`, `ApplicationProvisioningService$CreateApplicationRequest`, `ApplicationProvisioningService$ApplicationDto`, `ApplicationProvisioningService$DomainDto`, `UserAdminService$CreateUserRequest`, `UserAdminService$UserDto`, `ApiKeyService$CreatedApiKey`, `ApiKeyService$ApiKeyAuthResult`, and `ApiKeyService$ApiKeyInfo`.

- [ ] **Step 4: Commit the failing guardrails**

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "test: add backend architecture drift guardrails"
```

### Task 2: Move Platform HTTP Responses Into Interfaces

**Files:**
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/ApplicationHttpResponse.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/DomainHttpResponse.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/PlatformHttpMapper.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminApplicationController.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminDomainController.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/PlatformAdminController.java`

- [ ] **Step 1: Create platform HTTP DTOs**

Create `ApplicationHttpResponse.java`:

```java
package com.linkforge.platform.interfaces.web;

public record ApplicationHttpResponse(
        long id,
        long tenantId,
        String applicationKey,
        String displayName
) {
}
```

Create `DomainHttpResponse.java`:

```java
package com.linkforge.platform.interfaces.web;

public record DomainHttpResponse(
        long id,
        long tenantId,
        Long applicationId,
        String hostname,
        String scope
) {
}
```

- [ ] **Step 2: Create platform mapper**

Create `PlatformHttpMapper.java`:

```java
package com.linkforge.platform.interfaces.web;

import com.linkforge.platform.application.ApplicationProvisioningService;

final class PlatformHttpMapper {

    private PlatformHttpMapper() {
    }

    static ApplicationHttpResponse toApplicationResponse(ApplicationProvisioningService.ApplicationDto dto) {
        return new ApplicationHttpResponse(
                dto.id(),
                dto.tenantId(),
                dto.applicationKey(),
                dto.displayName()
        );
    }

    static DomainHttpResponse toDomainResponse(ApplicationProvisioningService.DomainDto dto) {
        return new DomainHttpResponse(
                dto.id(),
                dto.tenantId(),
                dto.applicationId(),
                dto.hostname(),
                dto.scope() == null ? null : dto.scope().name()
        );
    }
}
```

- [ ] **Step 3: Update platform controllers to return HTTP DTOs**

In `TenantAdminApplicationController`, change return signatures and mappings:

```java
    public ApiResponse<List<ApplicationHttpResponse>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(
                platformControlPlaneService.listApplications(tenantId).stream()
                        .map(PlatformHttpMapper::toApplicationResponse)
                        .toList(),
                RequestId.get()
        );
    }
```

```java
    public ApiResponse<ApplicationHttpResponse> create(@Valid @RequestBody CreateApplicationRequest req) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        return ApiResponse.ok(
                PlatformHttpMapper.toApplicationResponse(platformControlPlaneService.createApplication(
                        principal.getTenantId(),
                        actor,
                        new ApplicationProvisioningService.CreateApplicationRequest(req.applicationKey(), req.displayName())
                )),
                RequestId.get()
        );
    }
```

In `TenantAdminDomainController`, map every domain response through `PlatformHttpMapper::toDomainResponse`.

In `PlatformAdminController`, map both `listApplications()` and `listDomains()` through the new mapper.

- [ ] **Step 4: Run architecture tests and verify platform controller violations are gone**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test
```

Expected: still FAIL because other contexts remain, but no failure lines for `com.linkforge.platform.interfaces.web.*Controller` return types.

- [ ] **Step 5: Commit**

```bash
git add server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web
git commit -m "refactor: isolate platform http response contracts"
```

### Task 3: Extract Governance Application Models And HTTP Responses

**Files:**
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/SubmitApprovalRequest.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/ApprovalRequestResult.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/AuditLogResult.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/ApprovalRequestHttpResponse.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/AuditLogHttpResponse.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/GovernanceHttpMapper.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalApplicationService.java`
- Modify: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceServiceTest.java`
- Modify: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/ApprovalController.java`
- Modify: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/AuditController.java`

- [ ] **Step 1: Create top-level governance application result models**

Create `SubmitApprovalRequest.java`:

```java
package com.linkforge.governance.application;

import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.SensitiveOperationType;

import java.time.LocalDateTime;

public record SubmitApprovalRequest(
        SensitiveOperationType operationType,
        Long targetApplicationId,
        String beforeSnapshot,
        String afterSnapshot,
        UserActor actor,
        LocalDateTime requestedAt
) {
}
```

Create `ApprovalRequestResult.java`:

```java
package com.linkforge.governance.application;

import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;

public record ApprovalRequestResult(
        long id,
        long tenantId,
        SensitiveOperationType operationType,
        Long targetApplicationId,
        long requestedByUserId,
        String requestedByEmail,
        ApprovalStatus status,
        Long approverUserId,
        String approverEmail,
        String decisionReason
) {
}
```

Create `AuditLogResult.java`:

```java
package com.linkforge.governance.application;

import java.time.LocalDateTime;

public record AuditLogResult(
        long id,
        long tenantId,
        long actorUserId,
        String actorEmail,
        String actionType,
        String resourceType,
        String resourceId,
        Long requestId,
        String beforeSnapshot,
        String afterSnapshot,
        LocalDateTime createdAt
) {
}
```

- [ ] **Step 2: Replace nested governance records**

In `GovernanceService`, change method signatures:

```java
    public ApprovalRequestResult submitRequest(long tenantId, SubmitApprovalRequest request)
```

```java
    public ApprovalRequestResult approveRequest(long tenantId, long requestId, String reason, UserActor actor, LocalDateTime requestedAt)
```

```java
    public List<ApprovalRequestResult> listRequests(long tenantId, UserActor actor)
```

```java
    public List<AuditLogResult> listAuditLogs(long tenantId, UserActor actor)
```

Replace `toDto` with:

```java
    private ApprovalRequestResult toResult(ApprovalRequest request) {
        return new ApprovalRequestResult(
                request.id(),
                request.tenantId(),
                request.operationType(),
                request.targetApplicationId(),
                request.requestedByUserId(),
                request.requestedByEmail(),
                request.status(),
                request.approverUserId(),
                request.approverEmail(),
                request.decisionReason()
        );
    }
```

Replace audit mapping with `new AuditLogResult(...)`.

Delete the nested `SubmitApprovalRequest`, `ApprovalRequestDto`, and `AuditLogDto` records from `GovernanceService`.

- [ ] **Step 3: Update governance approval adapter**

In `GovernanceApprovalApplicationService`, replace:

```java
GovernanceService.ApprovalRequestDto dto
new GovernanceService.SubmitApprovalRequest(...)
```

with:

```java
ApprovalRequestResult dto
new SubmitApprovalRequest(...)
```

Update the `toResult` parameter:

```java
    private static ApprovalRequestView toResult(ApprovalRequestResult dto) {
```

- [ ] **Step 4: Create governance HTTP DTOs and mapper**

Create `ApprovalRequestHttpResponse.java`:

```java
package com.linkforge.governance.interfaces.web;

public record ApprovalRequestHttpResponse(
        long id,
        long tenantId,
        String operationType,
        Long targetApplicationId,
        long requestedByUserId,
        String requestedByEmail,
        String status,
        Long approverUserId,
        String approverEmail,
        String decisionReason
) {
}
```

Create `AuditLogHttpResponse.java`:

```java
package com.linkforge.governance.interfaces.web;

import java.time.LocalDateTime;

public record AuditLogHttpResponse(
        long id,
        long tenantId,
        long actorUserId,
        String actorEmail,
        String actionType,
        String resourceType,
        String resourceId,
        Long requestId,
        String beforeSnapshot,
        String afterSnapshot,
        LocalDateTime createdAt
) {
}
```

Create `GovernanceHttpMapper.java`:

```java
package com.linkforge.governance.interfaces.web;

import com.linkforge.governance.application.ApprovalRequestResult;
import com.linkforge.governance.application.AuditLogResult;

final class GovernanceHttpMapper {

    private GovernanceHttpMapper() {
    }

    static ApprovalRequestHttpResponse toApprovalResponse(ApprovalRequestResult result) {
        return new ApprovalRequestHttpResponse(
                result.id(),
                result.tenantId(),
                result.operationType() == null ? null : result.operationType().name(),
                result.targetApplicationId(),
                result.requestedByUserId(),
                result.requestedByEmail(),
                result.status() == null ? null : result.status().name(),
                result.approverUserId(),
                result.approverEmail(),
                result.decisionReason()
        );
    }

    static AuditLogHttpResponse toAuditLogResponse(AuditLogResult result) {
        return new AuditLogHttpResponse(
                result.id(),
                result.tenantId(),
                result.actorUserId(),
                result.actorEmail(),
                result.actionType(),
                result.resourceType(),
                result.resourceId(),
                result.requestId(),
                result.beforeSnapshot(),
                result.afterSnapshot(),
                result.createdAt()
        );
    }
}
```

- [ ] **Step 5: Update governance controllers**

In `ApprovalController`, use:

```java
    public ApiResponse<List<ApprovalRequestHttpResponse>> list()
```

and map:

```java
governanceService.listRequests(principal.getTenantId(), actor).stream()
        .map(GovernanceHttpMapper::toApprovalResponse)
        .toList()
```

For `approve`, use:

```java
    public ApiResponse<ApprovalRequestHttpResponse> approve(...)
```

and map `governanceService.approveRequest(...)` through `GovernanceHttpMapper.toApprovalResponse(...)`.

In `AuditController`, use:

```java
    public ApiResponse<List<AuditLogHttpResponse>> list()
```

and map through `GovernanceHttpMapper::toAuditLogResponse`.

- [ ] **Step 6: Update governance tests**

In `GovernanceServiceTest`, replace `GovernanceService.ApprovalRequestDto` with `ApprovalRequestResult`.

Run:

```bash
mvn -pl governance/application test
```

Expected: PASS.

- [ ] **Step 7: Run architecture tests**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test
```

Expected: still FAIL because other contexts remain, but no governance nested DTO or controller-return-type violations remain.

- [ ] **Step 8: Commit**

```bash
git add server/governance server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "refactor: separate governance application and http models"
```

### Task 4: Move Analytics HTTP Responses Into Interfaces

**Files:**
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/DailyStatHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/TopLinkStatHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/DimensionStatHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/VisitEventHttpResponse.java`
- Create: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/AnalyticsHttpMapper.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify: `server/analytics/interfaces/src/test/java/com/linkforge/analytics/interfaces/web/StatsControllerTest.java`

- [ ] **Step 1: Create analytics HTTP DTOs**

Create `DailyStatHttpResponse.java`:

```java
package com.linkforge.analytics.interfaces.web;

import java.time.LocalDate;

public record DailyStatHttpResponse(LocalDate day, long pv, long uv) {
}
```

Create `TopLinkStatHttpResponse.java`:

```java
package com.linkforge.analytics.interfaces.web;

public record TopLinkStatHttpResponse(
        long linkId,
        String code,
        String shortUrl,
        String originalUrl,
        long pv,
        long uv,
        boolean deleted
) {
}
```

Create `DimensionStatHttpResponse.java`:

```java
package com.linkforge.analytics.interfaces.web;

public record DimensionStatHttpResponse(String value, long pv, long uv, double ratio) {
}
```

Create `VisitEventHttpResponse.java`:

```java
package com.linkforge.analytics.interfaces.web;

import java.time.LocalDateTime;

public record VisitEventHttpResponse(
        LocalDateTime occurredAt,
        String requestId,
        String ipHash,
        String userAgentRaw,
        String userAgentFamily,
        String osFamily,
        String deviceType,
        String refererDomain,
        String language,
        String utmSource,
        String utmMedium,
        String utmCampaign
) {
}
```

- [ ] **Step 2: Create analytics HTTP mapper**

Create `AnalyticsHttpMapper.java`:

```java
package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsQueryService;

final class AnalyticsHttpMapper {

    private AnalyticsHttpMapper() {
    }

    static DailyStatHttpResponse toDailyStatResponse(AnalyticsQueryService.DailyStat stat) {
        return new DailyStatHttpResponse(stat.day(), stat.pv(), stat.uv());
    }

    static TopLinkStatHttpResponse toTopLinkStatResponse(AnalyticsQueryService.TopLinkStat stat) {
        return new TopLinkStatHttpResponse(
                stat.linkId(),
                stat.code(),
                stat.shortUrl(),
                stat.originalUrl(),
                stat.pv(),
                stat.uv(),
                stat.deleted()
        );
    }

    static DimensionStatHttpResponse toDimensionStatResponse(AnalyticsQueryService.DimensionStat stat) {
        return new DimensionStatHttpResponse(stat.value(), stat.pv(), stat.uv(), stat.ratio());
    }

    static VisitEventHttpResponse toVisitEventResponse(AnalyticsQueryService.VisitEvent event) {
        return new VisitEventHttpResponse(
                event.occurredAt(),
                event.requestId(),
                event.ipHash(),
                event.userAgentRaw(),
                event.userAgentFamily(),
                event.osFamily(),
                event.deviceType(),
                event.refererDomain(),
                event.language(),
                event.utmSource(),
                event.utmMedium(),
                event.utmCampaign()
        );
    }
}
```

- [ ] **Step 3: Update `StatsController` return types**

For daily endpoints, return `ApiResponse<List<DailyStatHttpResponse>>` and map results with:

```java
queryService.linkDaily(p.getTenantId(), linkId, from, to).stream()
        .map(AnalyticsHttpMapper::toDailyStatResponse)
        .toList()
```

For top-link endpoints, return `ApiResponse<List<TopLinkStatHttpResponse>>` and map with `AnalyticsHttpMapper::toTopLinkStatResponse`.

For dimensions, return `ApiResponse<List<DimensionStatHttpResponse>>`.

For events, return `ApiResponse<List<VisitEventHttpResponse>>`.

Keep `ApprovalRequestView` return types unchanged because that is already a published contract DTO.

- [ ] **Step 4: Update `StatsControllerTest`**

Replace assertions expecting exact same application DTO instances with field-based assertions. For example, update `topLinks_shouldDelegateToAnalyticsReportingService` to assert:

```java
ApiResponse<List<TopLinkStatHttpResponse>> response = controller.topLinks(from, to, null, null);

assertThat(response.getData()).containsExactly(new TopLinkStatHttpResponse(
        101L,
        "abc123",
        null,
        "https://example.com/a",
        50L,
        40L,
        false
));
```

Update `linkEvents_shouldDelegateToApplicationServiceWithoutControllerDefaulting` to use `ApiResponse<List<VisitEventHttpResponse>>`.

- [ ] **Step 5: Run analytics interface tests**

Run:

```bash
mvn -pl analytics/interfaces test
```

Expected: PASS.

- [ ] **Step 6: Run architecture tests**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test
```

Expected: still FAIL because accounts/platform application nested records remain, but no analytics controller-return-type violations remain.

- [ ] **Step 7: Commit**

```bash
git add server/analytics
git commit -m "refactor: isolate analytics http response contracts"
```

### Task 5: Extract Accounts Application Models And HTTP Responses

**Files:**
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/CreateUserCommand.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/AuthResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/UserResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/CreatedApiKeyResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyInfoResult.java`
- Create: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyAuthResult.java`
- Create: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/UserHttpResponse.java`
- Create: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/AccountsHttpMapper.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/application/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/application/src/test/java/com/linkforge/accounts/application/AuthServiceTest.java`
- Modify: `server/accounts/application/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`
- Modify: `server/accounts/application/src/test/java/com/linkforge/accounts/application/ApiKeyServiceTest.java`
- Modify: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/AuthController.java`
- Modify: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/UserAdminController.java`
- Modify: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/ApiKeyAdminController.java`

- [ ] **Step 1: Create top-level accounts application models**

Create `CreateUserCommand.java`:

```java
package com.linkforge.accounts.application;

import java.util.Set;

public record CreateUserCommand(String email, String password, Set<String> roles) {
}
```

Create `AuthResult.java`:

```java
package com.linkforge.accounts.application;

import com.linkforge.foundation.security.AuthPrincipal;

public record AuthResult(String token, AuthPrincipal principal) {
}
```

Create `UserResult.java`:

```java
package com.linkforge.accounts.application;

import java.util.Set;

public record UserResult(long id, long tenantId, String email, String status, Set<String> roles) {
}
```

Create `CreatedApiKeyResult.java`:

```java
package com.linkforge.accounts.application;

public record CreatedApiKeyResult(long id, String name, String apiKey) {
}
```

Create `ApiKeyInfoResult.java`:

```java
package com.linkforge.accounts.application;

import java.time.LocalDateTime;

public record ApiKeyInfoResult(long id, Long applicationId, String name, String status, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
}
```

Create `ApiKeyAuthResult.java`:

```java
package com.linkforge.accounts.application;

public record ApiKeyAuthResult(long tenantId, Long applicationId, long apiKeyId) {
}
```

- [ ] **Step 2: Update `UserAdminService` to use top-level models**

Change:

```java
public List<UserDto> list(long tenantId)
public UserDto create(long tenantId, CreateUserRequest req)
public UserDto disable(...)
public UserDto enable(...)
public UserDto resetPassword(...)
```

to:

```java
public List<UserResult> list(long tenantId)
public UserResult create(long tenantId, CreateUserCommand req)
public UserResult disable(...)
public UserResult enable(...)
public UserResult resetPassword(...)
```

Replace `new UserDto(...)` with `new UserResult(...)`.

Delete nested `CreateUserRequest` and `UserDto`.

- [ ] **Step 3: Update `AuthService` and `ApiKeyService` to use top-level models**

In `AuthService`, keep the existing method names but return the new top-level result:

```java
public AuthResult register(String tenantName, String email, String rawPassword)
public AuthResult login(String email, String rawPassword)
```

Delete nested `AuthResult`.

Update `AuthServiceTest` and `AuthController` to import `com.linkforge.accounts.application.AuthResult` or rely on same-package resolution.

Then update `ApiKeyService`.

Change method signatures:

```java
public CreatedApiKeyResult create(long tenantId, String name)
public CreatedApiKeyResult create(long tenantId, long applicationId, String name)
public ApiKeyAuthResult authenticate(String apiKey)
public List<ApiKeyInfoResult> list(long tenantId)
public List<ApiKeyInfoResult> list(long tenantId, Long applicationId)
public ApiKeyInfoResult disable(long tenantId, long apiKeyId)
public ApiKeyInfoResult enable(long tenantId, long apiKeyId)
public CreatedApiKeyResult rotate(long tenantId, long apiKeyId)
```

Replace constructors accordingly and delete nested `CreatedApiKey`, `ApiKeyAuthResult`, and `ApiKeyInfo`.

Keep `ApiKeyAuthException` nested for now because it is an exception type, not a DTO/result model.

- [ ] **Step 4: Create accounts HTTP DTO and mapper**

Create `UserHttpResponse.java`:

```java
package com.linkforge.accounts.interfaces.web;

import java.util.Set;

public record UserHttpResponse(long id, long tenantId, String email, String status, Set<String> roles) {
}
```

Create `AccountsHttpMapper.java`:

```java
package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.UserResult;

final class AccountsHttpMapper {

    private AccountsHttpMapper() {
    }

    static UserHttpResponse toUserResponse(UserResult result) {
        return new UserHttpResponse(
                result.id(),
                result.tenantId(),
                result.email(),
                result.status(),
                result.roles()
        );
    }
}
```

- [ ] **Step 5: Update accounts controllers**

In `UserAdminController`, use `ApiResponse<List<UserHttpResponse>>` and `ApiResponse<UserHttpResponse>` and map through `AccountsHttpMapper`.

Change create command construction to:

```java
new CreateUserCommand(req.email(), req.password(), req.roles())
```

In `ApiKeyAdminController`, replace references to `ApiKeyService.CreatedApiKey` and `ApiKeyService.ApiKeyInfo` with top-level `CreatedApiKeyResult` and `ApiKeyInfoResult`. The existing interface-local `CreateApiKeyResponse` and `ApiKeyDto` records can remain.

In `AuthController`, replace:

```java
AuthService.AuthResult r = authService.register(...)
AuthService.AuthResult r = authService.login(...)
```

with:

```java
AuthResult r = authService.register(...)
AuthResult r = authService.login(...)
```

- [ ] **Step 6: Run accounts tests**

Run:

```bash
mvn -pl accounts/application,accounts/interfaces test
```

Expected: PASS.

- [ ] **Step 7: Run architecture tests**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test
```

Expected: still FAIL if platform application nested records remain, but no accounts nested result or controller-return-type violations remain.

- [ ] **Step 8: Commit**

```bash
git add server/accounts
git commit -m "refactor: separate accounts application and http models"
```

### Task 6: Extract Platform Application Models

**Files:**
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/CreateApplicationCommand.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationResult.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/DomainResult.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Modify: `server/platform/application/src/test/java/com/linkforge/platform/application/ApplicationProvisioningServiceTest.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/PlatformHttpMapper.java`
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminApplicationController.java`

- [ ] **Step 1: Create platform top-level application models**

Create `CreateApplicationCommand.java`:

```java
package com.linkforge.platform.application;

public record CreateApplicationCommand(String applicationKey, String displayName) {
}
```

Create `ApplicationResult.java`:

```java
package com.linkforge.platform.application;

public record ApplicationResult(long id, long tenantId, String applicationKey, String displayName) {
}
```

Create `DomainResult.java`:

```java
package com.linkforge.platform.application;

import com.linkforge.platform.domain.DomainScope;

public record DomainResult(long id, long tenantId, Long applicationId, String hostname, DomainScope scope) {
}
```

- [ ] **Step 2: Update platform application services**

Change `ApplicationProvisioningService` signatures:

```java
public ApplicationResult createApplication(long tenantId, UserActor actor, CreateApplicationCommand request)
public DomainResult createTenantSharedDomain(long tenantId, UserActor actor, String hostname)
public DomainResult createApplicationDedicatedDomain(long tenantId, UserActor actor, long applicationId, String hostname)
```

Change `PlatformControlPlaneService` signatures:

```java
public ApplicationResult createApplication(...)
public DomainResult createTenantSharedDomain(...)
public DomainResult createApplicationDedicatedDomain(...)
public List<ApplicationResult> listApplications(long tenantId)
public List<DomainResult> listDomains(long tenantId)
public List<DomainResult> listDomainsForApplication(long tenantId, long applicationId)
public List<ApplicationResult> listAllApplications()
public List<DomainResult> listAllDomains()
```

Replace all nested `ApplicationProvisioningService.ApplicationDto`, `DomainDto`, and `CreateApplicationRequest` references with top-level models.

Delete the nested records from `ApplicationProvisioningService`.

- [ ] **Step 3: Update platform interfaces**

Update `PlatformHttpMapper` imports and method parameters:

```java
import com.linkforge.platform.application.ApplicationResult;
import com.linkforge.platform.application.DomainResult;
```

Use:

```java
static ApplicationHttpResponse toApplicationResponse(ApplicationResult result)
static DomainHttpResponse toDomainResponse(DomainResult result)
```

In `TenantAdminApplicationController`, construct:

```java
new CreateApplicationCommand(req.applicationKey(), req.displayName())
```

- [ ] **Step 4: Update platform tests**

In `ApplicationProvisioningServiceTest`, replace `ApplicationProvisioningService.DomainDto` with `DomainResult`.

Run:

```bash
mvn -pl platform/application,platform/domain test
```

Expected: PASS.

- [ ] **Step 5: Run architecture tests to verify DTO guardrails pass so far**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test
```

Expected: PASS for the two new guardrails unless remaining nested application models such as `AuthService.AuthResult`, `AnalyticsVisitEventService.RedirectVisitEvent`, or `VisitDimensionNormalizer.Normalized` are also matched. If they are matched, adjust the regex only to block `Dto`, `Result`, `Request`, `Command`, `Info`, and `Created` public nested API models that are referenced outside their enclosing class; do not block private helper records used only inside a class.

- [ ] **Step 6: Commit**

```bash
git add server/platform server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "refactor: separate platform application models"
```

### Task 7: Move Platform Domain Rules Into Domain

**Files:**
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Hostname.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/PlatformDefaults.java`
- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainAuthorizationPolicy.java`
- Modify: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainAuthorizationException.java`
- Create: `server/platform/domain/src/test/java/com/linkforge/platform/domain/HostnameTest.java`
- Modify: `server/platform/domain/src/test/java/com/linkforge/platform/domain/DomainAuthorizationPolicyTest.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
- Modify: `server/platform/application/src/test/java/com/linkforge/platform/application/ApplicationProvisioningServiceTest.java`

- [ ] **Step 1: Add `Hostname` value object**

Create `Hostname.java`:

```java
package com.linkforge.platform.domain;

import java.net.IDN;
import java.util.Locale;

public record Hostname(String value) {

    public Hostname {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hostname 不能为空");
        }
        value = normalize(value);
    }

    public static Hostname parse(String raw) {
        return new Hostname(raw);
    }

    private static String normalize(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank() || hasInvalidHostnameCharacters(value)) {
            throw invalid();
        }
        String ascii;
        try {
            ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
        if (ascii.length() > 253
                || ascii.startsWith(".")
                || ascii.endsWith(".")
                || ascii.contains("..")
                || "localhost".equals(ascii)
                || ascii.endsWith(".localhost")
                || looksLikeIpv4Address(ascii)) {
            throw invalid();
        }
        String[] labels = ascii.split("\\.");
        if (labels.length < 2) {
            throw invalid();
        }
        for (String label : labels) {
            if (!isValidHostnameLabel(label)) {
                throw invalid();
            }
        }
        return ascii;
    }

    private static boolean hasInvalidHostnameCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '/' || ch == '\\' || ch == '@' || ch == '*') {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeIpv4Address(String value) {
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static boolean isValidHostnameLabel(String label) {
        if (label.isBlank() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("hostname 不合法");
    }
}
```

- [ ] **Step 2: Add hostname domain tests**

Create `HostnameTest.java`:

```java
package com.linkforge.platform.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostnameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "example.com:443",
            "example.com/path",
            "exa mple.com",
            "bad_host.example.com",
            "*.example.com",
            "localhost",
            "127.0.0.1",
            "::1",
            "example.com."
    })
    void parse_shouldRejectInvalidHostnames(String hostname) {
        assertThatThrownBy(() -> Hostname.parse(hostname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostname");
    }

    @ParameterizedTest
    @ValueSource(strings = {" Go.Example.COM ", "go.example.com"})
    void parse_shouldNormalizeHostname(String hostname) {
        assertThat(Hostname.parse(hostname).value()).isEqualTo("go.example.com");
    }
}
```

- [ ] **Step 3: Add platform defaults**

Create `PlatformDefaults.java`:

```java
package com.linkforge.platform.domain;

public final class PlatformDefaults {

    public static final String APPLICATION_STATUS_ACTIVE = "ACTIVE";
    public static final long MONTHLY_LINK_LIMIT = 10_000L;
    public static final long MONTHLY_CLICK_LIMIT = 1_000_000L;
    public static final int REDIRECT_STATUS_CODE = 302;
    public static final boolean PREVIEW_ENABLED = false;
    public static final DomainScope DEFAULT_DOMAIN_SCOPE = DomainScope.TENANT_SHARED;

    private PlatformDefaults() {
    }
}
```

- [ ] **Step 4: Tighten domain authorization policy for inactive domains**

In `DomainAuthorizationException.Reason`, add:

```java
DOMAIN_NOT_ACTIVE
```

In `DomainAuthorizationPolicy.requireApplicationCanUseDomain`, add before scope checks:

```java
        if (domain.status() != DomainStatus.ACTIVE) {
            throw new DomainAuthorizationException(DomainAuthorizationException.Reason.DOMAIN_NOT_ACTIVE);
        }
```

Add test:

```java
    @Test
    void inactiveDomain_shouldBeRejected() {
        Domain domain = domain(DomainScope.TENANT_SHARED, null, DomainStatus.DISABLED);

        assertThatThrownBy(() -> policy.requireApplicationCanUseDomain(2001L, domain, true))
                .isInstanceOf(DomainAuthorizationException.class)
                .extracting("reason")
                .isEqualTo(DomainAuthorizationException.Reason.DOMAIN_NOT_ACTIVE);
    }
```

Add an overloaded helper in the test:

```java
    private static Domain domain(DomainScope scope, Long applicationId, DomainStatus status) {
        return new Domain(
                3001L,
                1L,
                applicationId,
                "go.example.com",
                scope,
                status,
                TargetTrustClass.FIRST_PARTY,
                LocalDateTime.parse("2026-04-28T10:00:00"),
                LocalDateTime.parse("2026-04-28T10:00:00")
        );
    }
```

and make the existing helper call it with `DomainStatus.ACTIVE`.

- [ ] **Step 5: Use domain rules from application service**

In `ApplicationProvisioningService`, remove private hostname normalization helper methods and use:

```java
String normalizedHostname = Hostname.parse(hostname).value();
```

Catch `IllegalArgumentException` at the use site:

```java
        String normalizedHostname;
        try {
            normalizedHostname = Hostname.parse(hostname).value();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
```

Replace defaults with `PlatformDefaults` constants.

In `PlatformControlPlaneService.domainAuthorizationMessage`, handle `DOMAIN_NOT_ACTIVE`:

```java
        if (e != null && e.reason() == DomainAuthorizationException.Reason.DOMAIN_NOT_ACTIVE) {
            return "域名未启用";
        }
```

- [ ] **Step 6: Run platform tests**

Run:

```bash
mvn -pl platform/domain,platform/application test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add server/platform
git commit -m "refactor: move platform domain rules into domain"
```

### Task 8: Narrow Foundation API Key Identity Semantics

**Files:**
- Create: `server/foundation/core/src/main/java/com/linkforge/foundation/security/ApiKeyAuthenticationDetails.java`
- Modify: `server/foundation/core/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Modify: `server/foundation/core/src/main/java/com/linkforge/foundation/security/ApiKeyAuthenticationResult.java`
- Modify: `server/foundation/runtime-security/src/main/java/com/linkforge/foundation/runtime/security/PrincipalActorMapper.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- Modify: `server/app/src/test/java/com/linkforge/app/security/ApiKeyAuthenticationFilterTest.java`

- [ ] **Step 1: Add API key authentication details**

Create `ApiKeyAuthenticationDetails.java`:

```java
package com.linkforge.foundation.security;

public record ApiKeyAuthenticationDetails(long apiKeyId, Long applicationId) {
}
```

- [ ] **Step 2: Remove API key fields from `AuthPrincipal`**

In `AuthPrincipal`, keep only:

```java
    private final long userId;
    private final long tenantId;
    private final String email;
    private final Set<String> roles;
    private final int tokenVersion;
```

Keep constructors:

```java
    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles) {
        this(userId, tenantId, email, roles, 0);
    }

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, int tokenVersion) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.tokenVersion = tokenVersion;
    }
```

Delete `getApiKeyId()` and `getApplicationId()` from `AuthPrincipal`.

- [ ] **Step 3: Keep API key metadata in authentication details**

In `ApiKeyAuthenticationFilter`, construct principal without API key fields:

```java
            AuthPrincipal principal = new AuthPrincipal(
                    0L,
                    r.tenantId(),
                    null,
                    Set.of(StandardRoles.OPENAPI)
            );
```

Set details on the authentication token:

```java
            UsernamePasswordAuthenticationToken at = new UsernamePasswordAuthenticationToken(
                    principal,
                    "N/A",
                    Set.of(new SimpleGrantedAuthority("ROLE_" + StandardRoles.OPENAPI))
            );
            at.setDetails(r.toDetails());
```

Add import:

```java
import com.linkforge.foundation.security.ApiKeyAuthenticationDetails;
```

- [ ] **Step 4: Update principal actor mapper**

In `PrincipalActorMapper.requireApiKey`, read `SecurityContextHolder` authentication details instead of `AuthPrincipal` fields:

```java
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth == null ? null : auth.getDetails();
        if (!(details instanceof ApiKeyAuthenticationDetails apiKeyDetails) || apiKeyDetails.apiKeyId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new ApiKeyActor(principal.getTenantId(), apiKeyDetails.apiKeyId(), apiKeyDetails.applicationId());
```

Add imports:

```java
import com.linkforge.foundation.security.ApiKeyAuthenticationDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
```

- [ ] **Step 5: Update tests using old constructors**

Search:

```bash
rg "new AuthPrincipal\\([^\\n]*apiKey|new AuthPrincipal\\([^\\n]*applicationId|getApiKeyId|getApplicationId" server -g '*.java'
```

Update tests to use `UsernamePasswordAuthenticationToken#setDetails(new ApiKeyAuthenticationDetails(...))` when they need API key metadata.

- [ ] **Step 6: Run foundation and app security tests**

Run:

```bash
mvn -pl foundation/core,foundation/runtime-security,app -Dtest='*Auth*Test,*Security*Test,*ApiKey*Test' test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add server/foundation server/app
git commit -m "refactor: keep api key metadata out of auth principal"
```

### Task 9: Split Broad Governance Contract Payloads

**Files:**
- Create or modify files under `server/contracts/governance/src/main/java/com/linkforge/contract/governance/`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalApplicationService.java`
- Modify: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceServiceTest.java`

- [ ] **Step 1: Create narrower payload records**

Create `LinkDestinationChangeApprovalPayload.java`:

```java
package com.linkforge.contract.governance;

public record LinkDestinationChangeApprovalPayload(
        String type,
        int version,
        long linkId,
        String originalUrl
) {
    public static LinkDestinationChangeApprovalPayload v1(long linkId, String originalUrl) {
        return new LinkDestinationChangeApprovalPayload(ApprovalPayloadTypes.LINK_DESTINATION_CHANGE, ApprovalPayloadTypes.VERSION_1, linkId, originalUrl);
    }
}
```

Create `AnalyticsDetailExportApprovalPayload.java`:

```java
package com.linkforge.contract.governance;

import java.time.LocalDateTime;

public record AnalyticsDetailExportApprovalPayload(
        String type,
        int version,
        long linkId,
        String from,
        String to
) {
    public static AnalyticsDetailExportApprovalPayload v1(long linkId, LocalDateTime from, LocalDateTime to) {
        return new AnalyticsDetailExportApprovalPayload(
                ApprovalPayloadTypes.ANALYTICS_DETAIL_EXPORT,
                ApprovalPayloadTypes.VERSION_1,
                linkId,
                from == null ? null : from.toString(),
                to == null ? null : to.toString()
        );
    }
}
```

Create `ApplicationQuotaIncreaseApprovalPayload.java`:

```java
package com.linkforge.contract.governance;

public record ApplicationQuotaIncreaseApprovalPayload(
        String type,
        int version,
        Long monthlyLinkLimit,
        Long monthlyClickLimit
) {
    public static ApplicationQuotaIncreaseApprovalPayload v1(Long monthlyLinkLimit, Long monthlyClickLimit) {
        return new ApplicationQuotaIncreaseApprovalPayload(
                ApprovalPayloadTypes.APPLICATION_QUOTA_INCREASE,
                ApprovalPayloadTypes.VERSION_1,
                monthlyLinkLimit,
                monthlyClickLimit
        );
    }
}
```

Create `ApprovalPayloadTypes.java`:

```java
package com.linkforge.contract.governance;

public final class ApprovalPayloadTypes {
    public static final int VERSION_1 = 1;
    public static final String LINK_DESTINATION_CHANGE = "linkDestinationChange";
    public static final String ANALYTICS_DETAIL_EXPORT = "analyticsDetailExport";
    public static final String APPLICATION_QUOTA_INCREASE = "applicationQuotaIncrease";

    private ApprovalPayloadTypes() {
    }
}
```

- [ ] **Step 2: Replace `ApprovalPayloads.*` usages**

Replace:

```java
ApprovalPayloads.LinkDestinationChangePayload.v1(...)
ApprovalPayloads.AnalyticsDetailExportPayload.v1(...)
ApprovalPayloads.ApplicationQuotaIncreasePayload.class
ApprovalPayloads.APPLICATION_QUOTA_INCREASE
ApprovalPayloads.VERSION_1
```

with:

```java
LinkDestinationChangeApprovalPayload.v1(...)
AnalyticsDetailExportApprovalPayload.v1(...)
ApplicationQuotaIncreaseApprovalPayload.class
ApprovalPayloadTypes.APPLICATION_QUOTA_INCREASE
ApprovalPayloadTypes.VERSION_1
```

- [ ] **Step 3: Deprecate or remove `ApprovalPayloads`**

If no production code uses `ApprovalPayloads`, delete it. If compatibility is needed for tests or downstream code, replace contents with deprecated aliases:

```java
@Deprecated(forRemoval = true)
public final class ApprovalPayloads {
    private ApprovalPayloads() {
    }
}
```

Prefer deletion if the Maven reactor compiles without it.

- [ ] **Step 4: Run governance contract and application tests**

Run:

```bash
mvn -pl contracts/governance,governance/application test
```

Expected: PASS.

- [ ] **Step 5: Run architecture tests**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/contracts/governance server/governance
git commit -m "refactor: split governance approval payload contracts"
```

### Task 10: Split App Security Configuration By Request Space

**Files:**
- Create: `server/app/src/main/java/com/linkforge/app/security/OpenApiSecurityConfig.java`
- Create: `server/app/src/main/java/com/linkforge/app/security/ApiSecurityConfig.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Modify: `server/app/src/test/java/com/linkforge/app/security/SecurityConfigCsrfTest.java`
- Modify: other app security tests if constructor/config class references change

- [ ] **Step 1: Move OpenAPI chain into its own config**

Create `OpenApiSecurityConfig.java` and move the current `openApiSecurityFilterChain(...)` bean into it:

```java
package com.linkforge.app.security;

import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.foundation.security.AccountStatusVerifier;
import com.linkforge.foundation.security.ApiKeyAuthenticator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class OpenApiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain openApiSecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            ApiKeyAuthenticator apiKeyService,
            AccountStatusVerifier accountStatusService,
            ApiErrorResponseWriter errorResponseWriter
    ) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                errorResponseWriter
        );
        http
                .securityMatcher("/api/v1/open/**")
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(reg -> reg.anyRequest().authenticated())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

- [ ] **Step 2: Move JWT API chain into its own config**

Create `ApiSecurityConfig.java` and move `apiSecurityFilterChain(...)` into it. Keep the current CSRF behavior unchanged.

Add:

```java
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfig {
```

Use `@Order(2)` on the bean.

- [ ] **Step 3: Leave `SecurityConfig` as method-security only**

Replace `SecurityConfig` contents with:

```java
package com.linkforge.app.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {
}
```

- [ ] **Step 4: Update tests**

If tests import or instantiate `SecurityConfig` expecting filter-chain beans, update them to import `ApiSecurityConfig` and/or `OpenApiSecurityConfig`.

Run:

```bash
mvn -pl app -Dtest='com.linkforge.app.security.*Test,com.linkforge.architecture.AppModuleCompositionTest' test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/app/src/main/java/com/linkforge/app/security server/app/src/test/java/com/linkforge/app/security
git commit -m "refactor: split app security chains by request space"
```

### Task 11: Final Architecture Verification And Documentation Sync

**Files:**
- Modify: `docs/architecture.md` if wording needs to reflect new DTO/model rules
- Modify: `docs/superpowers/specs/2026-05-04-linkforge-backend-architecture-remediation-design.md` only if implementation forced a deliberate scope change

- [ ] **Step 1: Run architecture tests**

Run:

```bash
mvn -pl app -Dtest=com.linkforge.architecture.ArchitectureTest,com.linkforge.architecture.AppModuleCompositionTest,com.linkforge.architecture.PersistenceMigrationGuardTest test
```

Expected: PASS.

- [ ] **Step 2: Run targeted module tests**

Run:

```bash
mvn -pl accounts/application,accounts/interfaces,platform/domain,platform/application,platform/interfaces,governance/application,governance/interfaces,analytics/interfaces test
```

Expected: PASS.

- [ ] **Step 3: Run full backend tests**

Run:

```bash
mvn test
```

Expected: PASS. If infrastructure-dependent integration tests fail due local Docker/MySQL/Redis availability, capture the exact failing module and run the closest non-integration module set from Step 2.

- [ ] **Step 4: Check for forbidden public signatures**

Run:

```bash
rg "ApiResponse<.*application\\.|Service\\.[A-Z].*(Dto|Result|Request|Info|Created)" server/*/interfaces/src/main/java server/*/application/src/main/java -g '*.java'
```

Expected: no matches for public controller return types or nested application DTO/result/request references.

- [ ] **Step 5: Update docs if needed**

If the implementation introduced new conventions such as `*HttpResponse` and `*Result`, add a short note to `docs/architecture.md` under Tactical DDD Rules:

```markdown
- Application-facing request/result records are top-level application types. HTTP-only records live in `interfaces` and use transport-oriented names such as `*HttpResponse`.
```

- [ ] **Step 6: Commit docs and final cleanup**

```bash
git add docs server
git commit -m "docs: sync backend architecture rules"
```

- [ ] **Step 7: Final status**

Run:

```bash
git status --short
```

Expected: no uncommitted changes.

# Internal Shortlink Control Plane Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn LinkForge from a tenant-scoped shortlink MVP into a production-grade internal control plane with `tenant = business line`, `application = consuming system`, domain-aware routing, approval/audit governance, and a self-service admin console.

**Architecture:** Implement this as a phased modular-monolith evolution rather than a repo-wide rewrite. Introduce new control-plane entities and policies first, migrate existing data into deterministic default ownership, then switch redirect/routing to `host + code`, then layer new application-scoped APIs and self-service UI on top while preserving current shortlink/analytics correctness during the transition.

**Tech Stack:** Java 21+, Spring Boot 3.2, Spring Security, MyBatis, Flyway, Redis, MySQL, JUnit 5, Mockito, Testcontainers, Vue 3, TypeScript, Vite, Pinia, Vue Router.

---

## File Structure

### Backend: platform control-plane model

- Create: `server/app/src/main/resources/db/migration/V11__platform_applications_domains.sql`
- Create: `server/app/src/main/resources/db/migration/V12__short_links_application_domain_backfill.sql`
- Create: `server/app/src/main/resources/db/migration/V13__approval_audit_tables.sql`
- Create: `server/app/src/main/resources/db/migration/V14__platform_roles_and_api_keys.sql`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Application.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationDomainAuthorization.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationQuota.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationPolicy.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Domain.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainScope.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/DomainStatus.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/TargetTrustClass.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/ApplicationRepository.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/DomainRepository.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/ApplicationQuotaRepository.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/ApplicationPolicyRepository.java`
- Create: `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/persistence/...`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/...`
- Modify: `server/pom.xml`
- Modify: `server/app/pom.xml`
- Modify: `server/app/src/main/java/com/linkforge/app/compose/...`

Responsibility: add first-class platform entities for applications, domains, quotas, and policies without overloading `accounts` or `shortlink`.

### Backend: approval and audit governance

- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalRequest.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalDecision.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalStatus.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/AuditLog.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/SensitiveOperationType.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/port/ApprovalRepository.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/port/AuditLogRepository.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/...`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/domain/Roles.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/...`

Responsibility: define the approval matrix, forbid self-approval, persist before/after diffs, and expose governance flows through consistent APIs.

### Backend: shortlink ownership, lifecycle, and migration

- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLinkLifecycleState.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLinkRevision.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/query/SearchShortLinksQueryHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/mapper/ShortLinkDtoMapper.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkCreateHttpRequest.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkUpdateHttpRequest.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/ShortLinkEntity.java`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.xml`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/migration/LegacyShortLinkBackfillService.java`

Responsibility: make short links application/domain-aware, introduce lifecycle state separate from approval state, and backfill legacy tenant-scoped links into default application/domain ownership.

### Backend: redirect and cache routing upgrade

- Modify: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMeta.java` or equivalent redirect contract types
- Modify: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaSourcePort.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectUrlBuilder.java`
- Modify: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
- Modify: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectRiskControlFilter.java`
- Modify: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/cache/LinkCacheService.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java`
- Modify: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjection.java`
- Modify: `server/redirect/infrastructure/src/main/resources/com/linkforge/redirect/infrastructure/projection/RedirectLinkProjectionMapper.xml`

Responsibility: switch link lookup, cache keys, and authoritative read models from `code` to `host + code` while preserving compatibility for legacy default domains.

### Backend: analytics, quotas, and application-scoped observability

- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/AnalyticsQueryServiceImpl.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/persistence/AnalyticsQueryRepository.java`
- Modify: `server/analytics/infrastructure/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.xml`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java`

Responsibility: expand stats and event attribution to application/domain scopes and support sensitive export requests through governance APIs instead of direct unsecured bulk reads.

### Frontend: self-service control plane console

- Modify: `web/src/router/index.ts`
- Modify: `web/src/stores/auth.ts`
- Modify: `web/src/services/http.ts`
- Modify: `web/src/services/types.ts`
- Create: `web/src/services/applications.ts`
- Create: `web/src/services/domains.ts`
- Create: `web/src/services/approvals.ts`
- Create: `web/src/services/audit.ts`
- Modify: `web/src/services/links.ts`
- Modify: `web/src/services/stats.ts`
- Create: `web/src/views/TenantOverviewView.vue`
- Create: `web/src/views/ApplicationsView.vue`
- Create: `web/src/views/ApplicationDetailView.vue`
- Create: `web/src/views/DomainsView.vue`
- Create: `web/src/views/ApiKeysView.vue`
- Create: `web/src/views/ApprovalsView.vue`
- Create: `web/src/views/AuditView.vue`
- Modify: `web/src/views/LinksView.vue`
- Modify: `web/src/views/StatsView.vue`
- Modify: `web/src/composables/useAppSessionNavigation.ts`
- Create: `web/src/composables/useApplicationsPage.ts`
- Create: `web/src/composables/useDomainsPage.ts`
- Create: `web/src/composables/useApprovalsPage.ts`
- Create: `web/src/composables/useAuditPage.ts`

Responsibility: make tenant self-service real by adding application/domain/governance pages instead of treating the console as link CRUD only.

### Tests and migration verification

- Create: `server/integration-tests/src/test/java/com/linkforge/platform/ApplicationProvisioningIntegrationTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/platform/DomainAuthorizationIntegrationTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/platform/LegacyShortLinkMigrationIntegrationTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/governance/ApprovalWorkflowIntegrationTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/redirect/HostCodeRedirectIntegrationTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/analytics/ApplicationScopedStatsIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkPaginationApiIntegrationTest.java`
- Modify: `web/src/router/index.spec.ts`
- Create: `web/src/composables/useApplicationsPage.spec.ts`
- Create: `web/src/composables/useDomainsPage.spec.ts`
- Create: `web/src/composables/useApprovalsPage.spec.ts`

Responsibility: prove migration safety, routing correctness, approval enforcement, and UI self-service behavior before rollout.

---

### Task 1: Introduce platform entities and application/domain ownership

**Files:**
- Create: `server/app/src/main/resources/db/migration/V11__platform_applications_domains.sql`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Application.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/Domain.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationQuota.java`
- Create: `server/platform/domain/src/main/java/com/linkforge/platform/domain/ApplicationPolicy.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/ApplicationRepository.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/DomainRepository.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/ApplicationQuotaRepository.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/port/ApplicationPolicyRepository.java`
- Create: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Create: `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/persistence/...`
- Modify: `server/pom.xml`
- Modify: `server/app/pom.xml`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void createApplication_should_persist_tenant_scoped_application_with_default_policy() { ... }

@Test
void createTenantSharedDomain_should_allow_authorization_to_multiple_applications() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && mvn -q -pl platform,integration-tests -P it -Dtest=ApplicationProvisioningIntegrationTest,DomainAuthorizationIntegrationTest test`
Expected: FAIL because the `platform` module, schema, and repositories do not exist yet.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- Flyway tables for `applications`, `domains`, `application_domain_authorizations`, `application_quotas`, and `application_policies`
- domain enums for scope/status/trust class
- repository ports and minimal MyBatis adapters
- `PlatformControlPlaneService` methods for:
  - create application
  - create tenant-shared domain
  - create application-dedicated domain
  - authorize tenant domain for application use
- module registration in the Maven reactor and Spring Boot composition

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl platform,integration-tests -P it -Dtest=ApplicationProvisioningIntegrationTest,DomainAuthorizationIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `cd server && mvn -q -pl platform test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/pom.xml server/app/pom.xml server/app/src/main/resources/db/migration/V11__platform_applications_domains.sql server/platform
git commit -m "feat: add platform application and domain model"
```

---

### Task 2: Add governance entities, approval matrix, and audit persistence

**Files:**
- Create: `server/app/src/main/resources/db/migration/V13__approval_audit_tables.sql`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalRequest.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalDecision.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalStatus.java`
- Create: `server/governance/domain/src/main/java/com/linkforge/governance/domain/AuditLog.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/port/ApprovalRepository.java`
- Create: `server/governance/application/src/main/java/com/linkforge/governance/application/port/AuditLogRepository.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/...`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/domain/Roles.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void requester_should_not_be_able_to_approve_own_sensitive_request() { ... }

@Test
void tenant_admin_should_only_approve_quota_increase_within_tenant_ceiling() { ... }

@Test
void platform_admin_should_be_required_for_external_domain_binding() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && mvn -q -pl governance,integration-tests -P it -Dtest=ApprovalWorkflowIntegrationTest test`
Expected: FAIL because the governance module and approval rules do not exist yet.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- approval/audit tables
- request states `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `CANCELLED`, `EXECUTED`, `EXPIRED`
- typed sensitive operation categories
- governance service methods for:
  - submit request
  - approve request
  - reject request
  - append audit log
- self-approval prevention
- tenant-admin approval only for quota changes within tenant ceilings and destination changes that stay inside already-approved production domains
- platform-admin-only approval for:
  - external domains
  - webhook enablement
  - high-privilege API keys
  - risk-control allowlist changes
  - detailed data exports
  - public-production exceptions for internal/test/temporary targets

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl governance,integration-tests -P it -Dtest=ApprovalWorkflowIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `cd server && mvn -q -pl governance test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/app/src/main/resources/db/migration/V13__approval_audit_tables.sql server/governance server/accounts/src/main/java/com/linkforge/accounts/domain/Roles.java server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java
git commit -m "feat: add approval workflow and audit log model"
```

---

### Task 3: Make short links application/domain-aware and split lifecycle from approval state

**Files:**
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLinkLifecycleState.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLinkRevision.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkCreateHttpRequest.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/dto/ShortLinkUpdateHttpRequest.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/ShortLinkEntity.java`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.xml`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void createLink_should_require_application_and_domain_ownership() { ... }

@Test
void active_public_link_destination_change_should_create_request_instead_of_direct_mutation() { ... }

@Test
void draft_link_should_allow_direct_edit_without_approval_state_on_link_row() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && mvn -q -pl shortlink,integration-tests -P it -Dtest=ShortLinkServiceTest,LinkForgeIntegrationTest test`
Expected: FAIL because short links are still tenant-scoped and have no lifecycle split from approval workflow.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- `applicationId`, `domainId`, and lifecycle state on `ShortLink`
- separate revision/history representation
- creation validation that the selected domain is authorized for the application
- direct edits only for `DRAFT` and `PRE_RELEASE`
- approval-request creation for public `ACTIVE` destination changes
- DTOs and service signatures updated for application/domain selection and lifecycle-aware operations

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl shortlink,integration-tests -P it -Dtest=ShortLinkServiceTest,LinkForgeIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `cd server && mvn -q -pl shortlink test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/shortlink
git commit -m "feat: add application-aware shortlink lifecycle"
```

---

### Task 4: Backfill legacy links into default application/domain ownership

**Files:**
- Create: `server/app/src/main/resources/db/migration/V12__short_links_application_domain_backfill.sql`
- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/migration/LegacyShortLinkBackfillService.java`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.xml`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml`
- Test: `server/integration-tests/src/test/java/com/linkforge/platform/LegacyShortLinkMigrationIntegrationTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void migration_should_place_existing_tenant_links_into_default_application_and_legacy_domain() { ... }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && mvn -q -pl integration-tests -P it -Dtest=LegacyShortLinkMigrationIntegrationTest test`
Expected: FAIL because no migration/backfill exists for existing tenant-scoped links.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- default application creation per tenant during migration
- legacy default domain creation representing the current base-url-backed host surface
- deterministic backfill of existing short links into default application/domain ownership
- explicit idempotent migration behavior

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl integration-tests -P it -Dtest=LegacyShortLinkMigrationIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run migration regression**

Run: `cd server && mvn -q -pl app,integration-tests -P it -Dtest=LegacyShortLinkMigrationIntegrationTest,LinkForgeIntegrationTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/app/src/main/resources/db/migration/V12__short_links_application_domain_backfill.sql server/shortlink/application/src/main/java/com/linkforge/shortlink/application/migration/LegacyShortLinkBackfillService.java server/integration-tests/src/test/java/com/linkforge/platform/LegacyShortLinkMigrationIntegrationTest.java
git commit -m "feat: backfill legacy links into default ownership"
```

---

### Task 5: Upgrade redirect lookup and cache keys to `host + code`

**Files:**
- Modify: `server/contracts/redirect/src/main/java/com/linkforge/contract/redirect/LinkMetaSourcePort.java`
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
- Modify: `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
- Modify: `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/cache/LinkCacheService.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/redirect/HostCodeRedirectIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void same_code_under_different_domains_should_resolve_to_different_links() { ... }

@Test
void legacy_default_domain_should_continue_to_resolve_migrated_links_during_compatibility_window() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && mvn -q -pl redirect,integration-tests -P it -Dtest=HostCodeRedirectIntegrationTest,RedirectServiceAuthoritativeFallbackTest test`
Expected: FAIL because redirect lookup and cache keys still depend on `code` only.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- redirect contract signatures that accept host and code
- cache keys shaped as `host:code`
- authoritative lookup constrained by resolved domain
- redirect controller extraction of request host
- compatibility handling for the legacy default domain

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl redirect,integration-tests -P it -Dtest=HostCodeRedirectIntegrationTest,RedirectServiceAuthoritativeFallbackTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `cd server && mvn -q -pl redirect test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/contracts/redirect server/redirect server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/query/AuthoritativeLinkMetaSourceAdapter.java server/integration-tests/src/test/java/com/linkforge/redirect/HostCodeRedirectIntegrationTest.java
git commit -m "feat: route redirects by host and code"
```

---

### Task 6: Add application-scoped API keys, quotas, and policy enforcement

**Files:**
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/interfaces/web/ApiKeyAdminController.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/platform/ApplicationProvisioningIntegrationTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void api_key_should_be_bound_to_one_application_not_only_to_tenant() { ... }

@Test
void link_create_should_fail_when_application_quota_is_exceeded() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && mvn -q -pl accounts,platform,integration-tests -P it -Dtest=ApiKeyServiceTest,ApplicationProvisioningIntegrationTest,LinkForgeIntegrationTest test`
Expected: FAIL because API keys are tenant-scoped and no application quota enforcement exists.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- application ownership on API keys
- principal enrichment with `applicationId`
- create/list/rotate/disable APIs at application scope
- quota evaluation in link-creation path
- platform-admin-only path for high-privilege API key creation

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl accounts,platform,integration-tests -P it -Dtest=ApiKeyServiceTest,ApplicationProvisioningIntegrationTest,LinkForgeIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `cd server && mvn -q -pl accounts,platform test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/accounts server/platform server/app/src/main/java/com/linkforge/app/security/ApiKeyAuthenticationFilter.java server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java
git commit -m "feat: scope api keys and quotas to applications"
```

---

### Task 7: Expand analytics and exports to tenant/application/domain scopes

**Files:**
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/query/AnalyticsQueryServiceImpl.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/persistence/AnalyticsQueryRepository.java`
- Modify: `server/analytics/infrastructure/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.xml`
- Test: `server/integration-tests/src/test/java/com/linkforge/analytics/ApplicationScopedStatsIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void stats_should_be_queryable_at_application_scope_without_leaking_other_applications() { ... }

@Test
void detailed_export_should_require_governance_request_before_download() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && mvn -q -pl analytics,integration-tests -P it -Dtest=ApplicationScopedStatsIntegrationTest,LinkForgeIntegrationTest test`
Expected: FAIL because analytics APIs are tenant/link oriented only and exports are not governed through approval tasks.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- analytics query methods for application and domain scopes
- stats controller routes for tenant/application/domain/link dashboards
- governance-gated export task issuance for detail data
- query filters that prevent cross-application leakage inside the same tenant

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl analytics,integration-tests -P it -Dtest=ApplicationScopedStatsIntegrationTest,LinkForgeIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `cd server && mvn -q -pl analytics test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/analytics server/integration-tests/src/test/java/com/linkforge/analytics/ApplicationScopedStatsIntegrationTest.java
git commit -m "feat: add application scoped stats and governed exports"
```

---

### Task 8: Restructure backend APIs around tenant-admin, platform-admin, and application resources

**Files:**
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminApplicationController.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminDomainController.java`
- Create: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/PlatformAdminController.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/ApprovalController.java`
- Create: `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/AuditController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void application_scoped_link_api_should_reject_access_to_other_application_in_same_tenant() { ... }

@Test
void tenant_admin_should_be_able_to_manage_own_applications_but_not_other_tenants() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && mvn -q -pl platform,governance,shortlink,analytics,integration-tests -P it -Dtest=LinkForgeIntegrationTest,ShortLinkPaginationApiIntegrationTest test`
Expected: FAIL because APIs are still primarily tenant-wide and lack the new resource hierarchy.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- tenant-admin application/domain endpoints
- platform-admin global endpoints
- approval/audit endpoints
- application-scoped link/stat/export endpoints
- compatibility handling for existing management APIs where needed during rollout

- [ ] **Step 4: Run focused tests**

Run: `cd server && mvn -q -pl platform,governance,shortlink,analytics,integration-tests -P it -Dtest=LinkForgeIntegrationTest,ShortLinkPaginationApiIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run server regression**

Run: `cd server && mvn -q test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/platform/interfaces server/governance/interfaces server/shortlink/interfaces server/analytics/interfaces server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkPaginationApiIntegrationTest.java
git commit -m "feat: add control plane api surfaces"
```

---

### Task 9: Build the self-service control-plane console

**Files:**
- Modify: `web/src/router/index.ts`
- Modify: `web/src/stores/auth.ts`
- Modify: `web/src/services/types.ts`
- Create: `web/src/services/applications.ts`
- Create: `web/src/services/domains.ts`
- Create: `web/src/services/approvals.ts`
- Create: `web/src/services/audit.ts`
- Modify: `web/src/services/links.ts`
- Modify: `web/src/services/stats.ts`
- Create: `web/src/views/TenantOverviewView.vue`
- Create: `web/src/views/ApplicationsView.vue`
- Create: `web/src/views/ApplicationDetailView.vue`
- Create: `web/src/views/DomainsView.vue`
- Create: `web/src/views/ApiKeysView.vue`
- Create: `web/src/views/ApprovalsView.vue`
- Create: `web/src/views/AuditView.vue`
- Modify: `web/src/views/LinksView.vue`
- Modify: `web/src/views/StatsView.vue`
- Modify: `web/src/composables/useAppSessionNavigation.ts`
- Create: `web/src/composables/useApplicationsPage.ts`
- Create: `web/src/composables/useDomainsPage.ts`
- Create: `web/src/composables/useApprovalsPage.ts`
- Create: `web/src/composables/useAuditPage.ts`
- Test: `web/src/router/index.spec.ts`
- Test: `web/src/composables/useApplicationsPage.spec.ts`
- Test: `web/src/composables/useDomainsPage.spec.ts`
- Test: `web/src/composables/useApprovalsPage.spec.ts`

- [ ] **Step 1: Write the failing tests**

```ts
it("shows application and domain self-service navigation for tenant admins", () => { ... });
it("submits a sensitive action as an approval request instead of mutating immediately", () => { ... });
it("loads application-scoped stats and audit views", () => { ... });
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd web && npm run test -- --runInBand`
Expected: FAIL because router, navigation, services, and views for control-plane self-service do not exist.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- new routes for tenant overview, applications, domains, API keys, approvals, and audit
- service clients for platform/application/domain/governance APIs
- page composables for onboarding and governance views
- navigation updates so the console is no longer only links/tags/stats
- links/stats views updated for application scope and governance-aware flows

- [ ] **Step 4: Run focused tests**

Run: `cd web && npm run test -- --runInBand`
Expected: PASS

- [ ] **Step 5: Run build verification**

Run: `cd web && npm run build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "feat: add self service control plane console"
```

---

### Task 10: Final end-to-end verification and rollout checklist

**Files:**
- Modify: `README.md` only if platform-facing setup instructions need to be updated after implementation
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/specs/2026-03-19-internal-shortlink-control-plane-spec.md` only if implementation-driven clarifications were required
- Create: `server/integration-tests/src/test/java/com/linkforge/platform/ControlPlaneEndToEndIntegrationTest.java`

- [ ] **Step 1: Write the failing end-to-end test**

```java
@Test
void tenant_admin_should_onboard_application_request_sensitive_change_get_approved_and_serve_public_redirect() { ... }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd server && mvn -q -pl integration-tests -P it -Dtest=ControlPlaneEndToEndIntegrationTest test`
Expected: FAIL because the full control-plane workflow is not wired end to end yet.

- [ ] **Step 3: Complete any missing wiring**

Implement any last-mile integration needed so this path works end-to-end:
- tenant admin creates application
- application obtains scoped API key
- domain authorization exists
- sensitive action submits approval
- approval executes change
- migrated/created link resolves publicly
- stats and audit reflect the result

- [ ] **Step 4: Run final verification**

Run: `cd server && mvn -q test`
Run: `cd server && mvn -q -P it test`
Run: `cd web && npm run test -- --runInBand`
Run: `cd web && npm run build`
Expected: PASS

- [ ] **Step 5: Update docs**

Update:
- `docs/architecture.md` with platform, governance, and domain-aware routing modules
- `README.md` with setup and routing/domain expectations if implementation changed operator workflow

- [ ] **Step 6: Commit**

```bash
git add docs/architecture.md README.md docs/superpowers/specs/2026-03-19-internal-shortlink-control-plane-spec.md server/integration-tests/src/test/java/com/linkforge/platform/ControlPlaneEndToEndIntegrationTest.java
git commit -m "docs: finalize internal shortlink control plane rollout"
```


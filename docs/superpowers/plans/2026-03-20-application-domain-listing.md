# Application Domain Listing Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tenant-admin API to enumerate the domains an application can actually use, combining its dedicated domains with explicitly authorized tenant-shared hostnames.

**Architecture:** Use the existing platform bounded context: domain queries live in `platform/application`, persistence stays in `platform/infrastructure`, and the controller remains in `platform/interfaces`. We'll add a focused new endpoint, reuse the repository/mapper layer for the new filter, and keep the response DTOs unchanged.

**Tech Stack:** Spring Web / Spring Security, MyBatis mappers under `platform/infrastructure`, integration tests under `server/integration-tests` using MockMvc.

---

### Task 1: Guarded integration test for the new endpoint

**Files:**
- Create: `server/integration-tests/src/test/java/com/linkforge/platform/ApplicationDomainListingIntegrationTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void tenantAdminCanListApplicationDomainsWithAuthorizedSharedNames() throws Exception {
    // set up tenant, two applications, one shared domain + authorization, one dedicated domain, one un-authorized shared
    mockMvc.perform(get("/api/v1/applications/{id}/domains", applicationId)
            .with(tenantAdmin(token))
    )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data..hostname").value(hasItems("shared-authorized", "dedicated")))
            .andExpect(jsonPath("$.data..hostname").value(not(hasItem("shared-unassigned"))));
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd server && mvn -q -pl integration-tests -P it -Dtest=ApplicationDomainListingIntegrationTest test
```
Expected: FAIL because endpoint and service behavior don't exist yet.

- [ ] **Step 3: Implement the minimal code (below)**

```
// done across Tasks 2+3
```

- [ ] **Step 4: Rerun the integration test**

```
cd server && mvn -q -pl integration-tests -P it -Dtest=ApplicationDomainListingIntegrationTest test
```
Expected: PASS

---

### Task 2: Platform controller/service support for application domains

**Files:**
- Modify: `server/platform/interfaces/src/main/java/com/linkforge/platform/interfaces/web/TenantAdminDomainController.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/PlatformControlPlaneService.java`
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/port/DomainRepository.java`
- Modify: `server/platform/infrastructure/src/main/java/com/linkforge/platform/infrastructure/persistence/DomainRepositoryMybatisAdapter.java`

- [ ] **Step 1: Extend controller**

Add `GET /applications/{applicationId}/domains` annotated with `@PreAuthorize("hasRole('TENANT_ADMIN')")` that calls into a new `platformControlPlaneService.listDomainsForApplication(tenantId, applicationId)` and returns the DTO list.

- [ ] **Step 2: Add service passthrough**

Add `listDomainsForApplication(long tenantId, long applicationId)` to `PlatformControlPlaneService` returning `ApplicationProvisioningService.DomainDto`. It should require the application exists and call a new `domainRepository.listForApplication(tenantId, applicationId)`.

- [ ] **Step 3: Expose repository port**

Add method `List<Domain> listForApplication(long tenantId, long applicationId);` (or similar) to `DomainRepository`.

- [ ] **Step 4: Implement repository query**

Implement the new method in `DomainRepositoryMybatisAdapter` using a mapper query that joins `application_domain_authorizations`. It must return dedicated domains for that app plus shared domains where an authorization record exists, ordered by newest first.

---

### Task 3: Persistence mapper and SQL

**Files:**
- Modify: `server/platform/infrastructure/src/main/resources/com/linkforge/platform/infrastructure/persistence/mapper/DomainMapper.xml`
- Modify: `server/platform/infrastructure/src/main/resources/db/migration/*` (none if schema already supports joined tables)

- [ ] **Step 1: Add SQL query**

Define `<select id="listForApplication" ...>` which selects domains that satisfy either (a) `scope = 'APPLICATION_DEDICATED' AND application_id = #{applicationId}` or (b) `scope = 'TENANT_SHARED'` joined to `application_domain_authorizations` for the application. Ensure `tenant_id` matches in both branches.

- [ ] **Step 2: Hook mapper into repository adapter**

Add call to `mapper.listForApplication` and map to `Domain` instances.

- [ ] **Step 3: Run entire integration test suite relevant to platform**

```
cd server && mvn -q -pl platform,integration-tests -P it -Dtest=ApplicationDomainListingIntegrationTest test
```
Expected: PASS.

---

Plan complete and saved to `docs/superpowers/plans/2026-03-20-application-domain-listing.md`. Ready to execute?

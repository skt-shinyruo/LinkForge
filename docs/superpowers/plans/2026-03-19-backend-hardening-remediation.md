# Backend Hardening Remediation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the backend risks identified in the March 19 review by hardening auth/session invalidation, analytics event integrity, shortlink write concurrency, tenant-admin safety, and relational integrity.

**Architecture:** Keep the existing modular monolith boundaries intact and fix each risk in the module that owns it. Use small, independently testable changes: accounts owns JWT invalidation and admin guardrails, analytics owns server-generated event IDs and incremental dirty-link flushing, shortlink owns optimistic locking for mutable links, and `app` owns schema migrations.

**Tech Stack:** Spring Boot 3.2, Spring Security, MyBatis, Redis, MySQL/Flyway, JUnit 5, Mockito, Testcontainers integration tests.

---

## File Structure

### Accounts auth/session invalidation and tenant-admin guardrails

- Create: `server/app/src/main/resources/db/migration/V8__users_token_version.sql`
- Create: `server/app/src/main/resources/db/migration/V10__core_operational_foreign_keys.sql`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsUserStore.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserEntity.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsUserStoreMybatisAdapter.java`
- Modify: `server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/UserMapper.xml`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/interfaces/web/AuthController.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/JwtService.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- Test: `server/accounts/src/test/java/com/linkforge/accounts/interfaces/web/AuthControllerTest.java`
- Test: `server/accounts/src/test/java/com/linkforge/accounts/application/AuthServiceTest.java` or create it if missing
- Test: `server/accounts/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java` (create if missing)
- Test: `server/app/src/test/java/com/linkforge/app/security/SecurityConfigCsrfTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/accounts/UserAdminSafetyIntegrationTest.java` (new)
- Test: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

Responsibility: add a user-scoped JWT token version, include it in issued JWTs, reject stale JWTs after password reset or logout, keep logout backward-compatible for cookie clearing when auth is absent, prevent tenant-admin self-lockout, and add only the safe operational foreign keys.

### Analytics event integrity and incremental flush

- Modify: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/AnalyticsKeys.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJob.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJob.java`
- Review: `server/analytics/infrastructure/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.xml`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/VisitRecorderServiceTest.java`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJobTest.java`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJobTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

Responsibility: stop using client-controlled `X-Request-Id` as analytics dedupe identity, explicitly treat analytics `request_id` as a server-owned event identifier, and replace full active-set rescans with failure-safe per-job dirty streams.

### Shortlink optimistic locking

- Create: `server/app/src/main/resources/db/migration/V9__short_links_version.sql`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/ShortLinkEntity.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkEntityMapper.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkRepository.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repository/MybatisShortLinkRepository.java`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkOptimisticLockIntegrationTest.java` (new)
- Test: `server/shortlink/interfaces/src/test/java/com/linkforge/shortlink/interfaces/web/ShortLinkWriteGuardTest.java` only if controller response mapping changes

Responsibility: add an explicit `version` column to mutable short links, require version-matching updates/deletes, and translate stale-write collisions into a business error instead of silently overwriting data.

## Task 1: Harden accounts auth/session invalidation and tenant-admin safety

**Files:**
- Create: `server/app/src/main/resources/db/migration/V8__users_token_version.sql`
- Create: `server/app/src/main/resources/db/migration/V10__core_operational_foreign_keys.sql`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/port/AccountsUserStore.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserEntity.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/AccountsUserStoreMybatisAdapter.java`
- Modify: `server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/UserMapper.xml`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/interfaces/web/AuthController.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/security/JwtService.java`
- Modify: `server/foundation/src/main/java/com/linkforge/foundation/security/AuthPrincipal.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/JwtAuthenticationFilter.java`
- Test: `server/accounts/src/test/java/com/linkforge/accounts/interfaces/web/AuthControllerTest.java`
- Test: `server/accounts/src/test/java/com/linkforge/accounts/application/UserAdminServiceTest.java`
- Test: `server/app/src/test/java/com/linkforge/app/security/SecurityConfigCsrfTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/accounts/UserAdminSafetyIntegrationTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void logout_should_revoke_current_users_cookie_jwt() { ... }

@Test
void password_reset_should_reject_old_jwt_even_if_signature_is_valid() { ... }

@Test
void disable_should_reject_self_disable_for_current_tenant_admin() { ... }

@Test
void disable_should_reject_disabling_last_active_tenant_admin() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl app,accounts,integration-tests -P it -Dtest=AuthControllerTest,UserAdminServiceTest,SecurityConfigCsrfTest,AuthPersistenceIntegrationTest,UserAdminSafetyIntegrationTest,ShortLinkDeleteRetentionIntegrationTest test`
Expected: FAIL because logout only clears cookies, password reset does not invalidate older JWTs, and tenant admins can still self-disable / disable the last active admin.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- `users.token_version INT NOT NULL DEFAULT 0`
- store `tokenVersion` in `AccountsUserStore.UserData`, `UserEntity`, and MyBatis mappings
- include `tokenVersion` claim in issued JWTs and `AuthPrincipal`
- make `JwtAuthenticationFilter` reject tokens whose version does not match the current user row
- make `UserAdminService.resetPassword(...)` increment token version
- make `AuthController.logout(...)` increment token version when an authenticated user principal is present, but still behave as a best-effort cookie clear when auth is absent
- pass actor user ID into `UserAdminService.disable(...)`
- reject disabling the current actor
- reject disabling the last active user whose roles contain `TENANT_ADMIN`
- add Flyway constraints only for operational live tables:
  - `users.tenant_id -> tenants.id`
  - `api_keys.tenant_id -> tenants.id`
  - `short_links.tenant_id -> tenants.id`
  - `tags.tenant_id -> tenants.id`
  - `user_roles.user_id -> users.id`
  - `link_tags.link_id -> short_links.id`
  - `link_tags.tag_id -> tags.id`
- do **not** add FKs from analytics history/projection tables to `short_links`, because delete retention intentionally outlives the source row

- [ ] **Step 4: Run focused tests**

Run: `mvn -q -pl app,accounts,integration-tests -P it -Dtest=AuthControllerTest,UserAdminServiceTest,SecurityConfigCsrfTest,AuthPersistenceIntegrationTest,UserAdminSafetyIntegrationTest,ShortLinkDeleteRetentionIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `mvn -q -pl app,accounts test`
Expected: PASS

## Task 2: Harden analytics event identity and make flush truly incremental

**Files:**
- Modify: `server/contracts/analytics/src/main/java/com/linkforge/contract/analytics/AnalyticsKeys.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/VisitRecorderService.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJob.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJob.java`
- Review: `server/analytics/infrastructure/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.xml`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/VisitRecorderServiceTest.java`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJobTest.java`
- Test: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsDimensionFlushJobTest.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void recordVisit_should_not_reuse_client_request_id_as_stream_request_id() { ... }

@Test
void flush_should_pop_only_dirty_members_instead_of_scanning_full_active_set() { ... }

@Test
void dimension_flush_should_use_its_own_dirty_set() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl analytics,integration-tests -P it -Dtest=VisitRecorderServiceTest,AnalyticsFlushJobTest,AnalyticsDimensionFlushJobTest,LinkForgeIntegrationTest test`
Expected: FAIL because event `requestId` mirrors `RequestId.get()` and both flush jobs still rely on the shared scanned active set.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- new analytics key helpers for separate dirty streams, for example `stats:dirty:flush:{day}` and `stats:dirty:dim:{day}`
- `VisitRecorderService.recordVisit(...)` should generate a server-owned random event request ID exactly once per stream record and persist it as the analytics event identifier; API/log request correlation remains outside analytics history rows
- `VisitRecorderService.recordVisit(...)` should append dirty members to both job-specific Redis Streams
- `AnalyticsFlushJob` and `AnalyticsDimensionFlushJob` should consume their own dirty-member streams with consumer-group semantics and acknowledge only after successful MySQL upsert so failures remain retryable
- keep day TTL / retention behavior for the new dirty streams consistent with existing analytics keys
- verify existing analytics query surfaces still expose non-blank `requestId`, now representing the server-owned event ID

- [ ] **Step 4: Run focused tests**

Run: `mvn -q -pl analytics,integration-tests -P it -Dtest=VisitRecorderServiceTest,AnalyticsFlushJobTest,AnalyticsDimensionFlushJobTest,LinkForgeIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `mvn -q -pl analytics test`
Expected: PASS

## Task 3: Add optimistic locking to shortlink writes

**Files:**
- Create: `server/app/src/main/resources/db/migration/V9__short_links_version.sql`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/ShortLinkEntity.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkEntityMapper.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/port/ShortLinkRepository.java`
- Modify: `server/shortlink/infrastructure/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repository/MybatisShortLinkRepository.java`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.xml`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
- Test: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkOptimisticLockIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void second_stale_update_should_fail_instead_of_silently_overwriting_first_update() { ... }

@Test
void stale_archive_restore_and_delete_should_fail_instead_of_applying_on_outdated_state() { ... }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl shortlink,integration-tests -P it -Dtest=ShortLinkOptimisticLockIntegrationTest test`
Expected: FAIL because stale update/archive/restore/delete paths currently succeed without version checks.

- [ ] **Step 3: Write the minimal implementation**

Implement:
- `short_links.version BIGINT NOT NULL DEFAULT 0`
- carry `version` through entity/domain/repository mappings
- update SQL should use `WHERE tenant_id = ? AND id = ? AND version = ?` and `SET ..., version = version + 1`
- delete/archive/restore/update flows should treat 0 affected rows as a stale-write conflict and surface a business error

- [ ] **Step 4: Run focused tests**

Run: `mvn -q -pl shortlink,integration-tests -P it -Dtest=ShortLinkOptimisticLockIntegrationTest,ShortLinkWriteGuardIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Run module regression**

Run: `mvn -q -pl shortlink test`
Expected: PASS

## Final verification

- [ ] Run the full server test suite after all tasks merge cleanly

Run: `mvn -q test`
Expected: PASS

- [ ] Run integration tests once after all migrations and cross-module changes are in place

Run: `mvn -q -P it test`
Expected: PASS

- [ ] Review schema migrations for ordering and conflicts

Check:
- `V8__users_token_version.sql`
- `V9__short_links_version.sql`
- `V10__core_operational_foreign_keys.sql`

Expected: deterministic ordering, no duplicate object names, and no overlap in ownership.

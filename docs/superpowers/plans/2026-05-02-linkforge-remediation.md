# LinkForge Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the reviewed security, governance, frontend-contract, deployment, and quota gaps with regression tests and full verification.

**Architecture:** Keep the modular monolith boundaries intact. Fix behavior at existing application/interface seams, add small focused configuration properties where needed, and keep frontend changes inside existing stores/composables/views. Prefer minimal compatibility-preserving changes unless an old behavior is the vulnerability.

**Tech Stack:** Java 17, Spring Boot 3, Maven multi-module, MyBatis XML, Vue 3, Pinia, Vue Router, Vitest, Docker Compose.

---

### Task 1: Shortlink Governance And Application Scope

**Files:**
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolver.java`
- Modify: `server/shortlink/interfaces/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Test: shortlink application/interface tests

- [ ] Add failing tests for destination-change approval bypass through inactive/reactivation state changes.
- [ ] Add failing tests proving non-tenant-admin users cannot create or browse application-scoped links through generic body/query loopholes.
- [ ] Implement minimal application-layer checks to require approval for application-aware destination changes and align generic application-scoped access with tenant-admin rules.
- [ ] Run targeted shortlink tests.

### Task 2: Domain Validation And Redirect Status Enforcement

**Files:**
- Modify: `server/platform/application/src/main/java/com/linkforge/platform/application/ApplicationProvisioningService.java`
- Modify: `server/shortlink/infrastructure/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml`
- Test: platform and shortlink query tests

- [ ] Add failing tests for invalid hostnames: URL strings, hostnames with ports, paths, spaces, underscores, wildcards, localhost, IP literals, and blank values.
- [ ] Add a failing test that disabled/non-ACTIVE domains do not resolve through host-aware redirect queries.
- [ ] Implement strict hostname normalization and active-domain SQL filtering.
- [ ] Run targeted tests.

### Task 3: Analytics Detail Governance

**Files:**
- Modify: `server/analytics/interfaces/src/main/java/com/linkforge/analytics/interfaces/web/StatsController.java`
- Modify: `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsLinkEventsService.java`
- Test: analytics tests

- [ ] Add failing tests showing normal users cannot directly read visit-event detail.
- [ ] Add failing tests for tenant-admin allowed access limits and rejected oversized ranges.
- [ ] Implement stricter access for event detail reads, retain export approval flow, cap time range and limit.
- [ ] Run targeted analytics tests.

### Task 4: Registration, Strict Config, Cookie Build, And Deployment Defaults

**Files:**
- Modify: `server/app/src/main/resources/application.yml`
- Modify: security/config classes located during implementation
- Modify: `server/accounts/interfaces/src/main/java/com/linkforge/accounts/interfaces/web/AuthController.java`
- Modify: `server/app/src/main/java/com/linkforge/app/security/SecurityConfig.java`
- Modify: `deploy/.env.example`
- Modify: `deploy/docker-compose.yml`
- Modify: `web/Dockerfile`
- Modify: `deploy/README.md`
- Test: auth/security/config tests

- [ ] Add failing tests that registration is rejected when registration is disabled.
- [ ] Add tests or static checks for cookie auth frontend build arguments.
- [ ] Add/adjust strict-config validation for sample JWT secret and analytics salt.
- [ ] Add server healthcheck, make web wait for server health, and document production defaults.
- [ ] Run targeted tests.

### Task 5: Strict Click Quota Behavior

**Files:**
- Modify: `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectQuotaGuard.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/quota/RedisApplicationClickQuotaReservationPort.java`
- Modify: relevant properties/config and `application.yml`
- Test: `server/redirect/application/src/test/java/com/linkforge/redirect/application/RedirectQuotaGuardTest.java`

- [ ] Add failing tests for strict quota mode: Redis reservation errors and quota lookup errors block redirects.
- [ ] Add tests preserving explicit non-strict fail-open behavior.
- [ ] Implement quota failure mode property and logging.
- [ ] Run redirect and quota tests.

### Task 6: Frontend Contract And UX Fixes

**Files:**
- Modify: `web/src/views/ApiKeysView.vue`
- Modify: `web/src/stores/auth.ts`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/composables/useAppSessionNavigation.ts`
- Modify: `web/src/composables/links/useLinkImportExport.ts`
- Modify: `web/src/components/links/LinkCreateForm.vue`
- Modify: `web/src/views/ApprovalsView.vue`
- Modify: `web/src/composables/useApprovalsPage.ts`
- Test: existing and new web specs

- [ ] Add failing Vitest coverage for API key active/disabled actions.
- [ ] Add failing router/auth tests for tenant-admin-only pages.
- [ ] Add failing tests for CSV import result exposure.
- [ ] Add failing tests for approval reason input.
- [ ] Implement minimal UI state and template changes.
- [ ] Run `npm test`.

### Task 7: Full Verification And Cleanup

- [ ] Run `mvn test` from `server`.
- [ ] Run `npm test` from `web`.
- [ ] Run `npm run build` from `web`.
- [ ] If a full command fails due environment, capture the failure, run the closest executable module/subset command, and report the limitation clearly.
- [ ] Run `git status --short` and summarize changed files. Do not commit unless the user explicitly asks.

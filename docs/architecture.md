# LinkForge Architecture

LinkForge is a modular monolith built as a Maven reactor plus a separate Vue frontend. The current design favors clear module boundaries inside one deployable runtime over independently deployed services.

## Backend

- `server/foundation`: split between pure shared-library packages and explicit runtime support. `foundation.config`, `foundation.id`, `foundation.tx`, and `foundation.util` stay framework-light library code; runtime beans such as `RequestIdFilter`, startup checks, and integration-event MyBatis wiring live under `foundation.runtime..`.
- `server/contracts/*`: shared vocabulary that still earns its maintenance cost inside the monolith. `contract-api` holds common API contracts, `contract-shortlink` carries shortlink integration-event payloads, `contract-redirect` carries redirect read/cache contracts, `contract-analytics` carries analytics contracts, and `contract-platform` carries application/domain authorization vocabulary.
- `server/accounts`: account, tenant, auth, and API-key management split into `domain`, `application`, `infrastructure`, and `interfaces` Maven modules. Its application layer now depends on ports/shared contracts instead of infrastructure classes or runtime-security helpers.
- `server/platform`: control-plane ownership for tenant applications, domains, quotas, and policies. It models `tenant -> application -> domain` relationships and exposes the tenant/platform admin HTTP surfaces used by the self-service console.
- `server/governance`: approval and audit management. It persists sensitive-operation requests, approval decisions, and audit logs, and exposes narrow application APIs for link destination changes and analytics export approvals.
- `server/shortlink`: write-side shortlink management split into `domain`, `application`, `infrastructure`, and `interfaces`. It owns durable shortlink state and emits integration events for downstream projections.
- `server/redirect`: cache-backed redirect serving split into `domain`, `application`, `infrastructure`, and `interfaces`. Redirect correctness uses Redis plus the authoritative shortlink read API on cache miss; it no longer maintains an independent redirect projection model.
- `server/analytics`: visit recording and read models split into `domain`, `application`, `infrastructure`, and `interfaces`.
- `server/app`: Spring Boot executable composition root. `LinkForgeApplication` explicitly imports context-owned runtime modules (`FoundationRuntimeModule`, `AccountsRuntimeModule`, `ShortlinkRuntimeModule`, `RedirectRuntimeModule`, `AnalyticsRuntimeModule`, `PlatformRuntimeModule`, and `GovernanceRuntimeModule`) instead of package scans or app-owned wrappers; the bounded-context runtime modules now live with the context export surface in `interfaces`.
- `server/integration-tests`: Testcontainers-based integration verification for cross-module behavior.

## Redirect Correctness Path

Redirect reads follow this order:

1. Redis redirect cache (`LinkCachePort`) using `host + code`
2. Authoritative shortlink read API using `host + code`
3. Negative-cache write for true misses

Shortlink mutations evict redirect cache entries only after transaction commit via `AfterCommit`, so Redis cannot diverge from durable state on rollback. There is no second redirect projection correctness channel.

## Frontend

The frontend is a Vue 3 + Vite SPA under `web/`.

- `views/`: route-level orchestration
- `components/`: presentational UI, including the shared `AppPageShell` and links-specific subcomponents
- `composables/`: page/session behavior extracted from views
- `services/`: HTTP-facing transport helpers and shared types
- `stores/`: application state such as auth

The SPA now exposes both the original shortlink pages and the self-service control-plane console:

- tenant overview
- applications and application detail
- domains
- API keys
- approvals
- audit
- links, stats, and tags

The control-plane pages stay thin by routing all HTTP work through `services/*` modules and driving view state from dedicated composables such as `useApplicationsPage`, `useDomainsPage`, `useApprovalsPage`, and `useAuditPage`. `LinksView.vue` and `StatsView.vue` have also been upgraded to understand application scope without duplicating transport logic.

## Deployment Shape

The repository currently ships as:

- one backend runtime (`server/app`)
- one frontend app (`web`)
- supporting infrastructure such as MySQL and Redis

This is not a microservice deployment. Module boundaries remain for ownership and tests, but day-to-day correctness is designed for a single deployed monolith rather than future service extraction.

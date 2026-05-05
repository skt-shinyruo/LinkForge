# LinkForge Architecture

LinkForge is a modular monolith built as a Maven reactor plus a separate Vue frontend. The current design favors clear module boundaries inside one deployable runtime over independently deployed services.

## Backend

- `server/foundation`: split between pure shared-library packages and explicit runtime support. `foundation.config`, `foundation.id`, `foundation.tx`, and `foundation.util` stay framework-light library code; runtime beans such as `RequestIdFilter`, startup checks, and integration-event MyBatis wiring live under `foundation.runtime..`.
- `server/contracts/*`: shared vocabulary that still earns its maintenance cost inside the monolith. `contract-api` holds common API contracts, `contract-shortlink` carries shortlink integration-event payloads, `contract-redirect` carries redirect read/cache contracts, `contract-analytics` carries analytics contracts, `contract-platform` carries application/domain authorization vocabulary, and `contract-governance` carries stable approval orchestration language.
- `server/accounts`: account, tenant, auth, and API-key management split into `domain`, `application`, `infrastructure`, `interfaces`, and `runtime` Maven modules. Its application layer now depends on ports/shared contracts instead of infrastructure classes or runtime-security helpers.
- `server/platform`: control-plane ownership for tenant applications, domains, quotas, and policies. It models `tenant -> application -> domain` relationships and exposes the tenant/platform admin HTTP surfaces used by the self-service console.
- `server/governance`: approval and audit management. It persists sensitive-operation requests, approval decisions, and audit logs, and exposes narrow application APIs for link destination changes and analytics export approvals.
- `server/shortlink`: write-side shortlink management split into `domain`, `application`, `infrastructure`, `interfaces`, and `runtime`. It owns durable shortlink state and emits integration events for downstream projections. Its public application entry points are focused use-case interfaces rather than one aggregate service interface.
- `server/redirect`: cache-backed redirect serving split into `domain`, `application`, `infrastructure`, `interfaces`, and `runtime`. Redirect correctness uses Redis plus the authoritative shortlink read API on cache miss; it no longer maintains an independent redirect projection model.
- `server/analytics`: visit recording and read models split into `domain`, `application`, `infrastructure`, `interfaces`, and `runtime`.
- `server/app`: Spring Boot executable composition root. `LinkForgeApplication` explicitly imports context-owned runtime modules (`FoundationRuntimeModule`, `AccountsRuntimeModule`, `ShortlinkRuntimeModule`, `RedirectRuntimeModule`, `AnalyticsRuntimeModule`, `PlatformRuntimeModule`, and `GovernanceRuntimeModule`) instead of package scans or app-owned wrappers; bounded-context runtime modules now live in each context's `runtime` Maven module. Security wiring is split by request family instead of one monolithic security class.
- `server/integration-tests`: Testcontainers-based integration verification for cross-module behavior.

## DDD Context Map

The backend bounded contexts are code-ownership boundaries inside one deployed monolith.
They are not independently deployed services.

### Accounts

Owns tenants, users, roles, API keys, authentication state, and account-status checks.
Accounts may publish authentication and account-status capabilities, but persistence details,
token parsing internals, and role storage remain private to the context.

### Platform

Owns tenant applications, domains, quotas, and application policies.
Platform publishes application scope, domain hostname lookup, and quota views through
`contract-platform`.

### Shortlink

Owns durable link state, link lifecycle, destination rules, tags, revisions, and shortlink
mutation events. `ShortLink` is the first aggregate root for tactical DDD hardening.
Other contexts may read redirect metadata, ownership, and summaries only through
`contract-shortlink`.

### Redirect

Owns traffic-plane redirect resolution, Redis cache behavior, preview/not-found responses,
and lightweight visit-event append. Redirect does not own link truth; cache misses use the
shortlink published read contract.

### Analytics

Owns visit ingestion, aggregates, detail storage, statistics reads, and export integration.
Analytics read models remain private. Cross-context link enrichment uses published
shortlink contracts.

### Governance

Owns approval request lifecycle, approval decisions, sensitive-operation records, and audit
logs. Governance exposes published approval contracts where cross-context callers still need
approval orchestration.

## Tactical DDD Rules

- `domain` owns aggregate behavior, invariants, value objects, domain services, and internal domain events.
- `application` owns use-case orchestration, transactions, repository ports, authorization input handling, and integration-event publication. Application services may accept explicit actor/input objects, but they must not read hidden runtime security context. Public request/result models in application code should be top-level types, not nested DTO/result/request containers inside service classes.
- `interfaces` owns HTTP mapping, request validation, principal extraction, and transport response shaping. Controllers map application DTOs to transport DTOs instead of exposing application records as HTTP contracts.
- `infrastructure` owns MyBatis, Redis, schedulers, and persistence mapping. Infrastructure adapters must not depend on runtime-security helpers such as `AuthContext` or `TenantGuard`; tenant scope is passed explicitly through application ports.
- `runtime` owns context-local Spring composition by importing the context's application, infrastructure, and interfaces configs.
- `contracts/*` owns published language shared across bounded contexts. Contract modules express stable business semantics and must not reuse foundation security/context actor objects as published API language.
- Bounded contexts must not import another context's `domain`, `application`, `infrastructure`, `interfaces`, or `runtime` packages.

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

The control-plane pages stay thin by routing all HTTP work through `services/*` modules and driving view state from dedicated composables such as `useApplicationsPage`, `useApplicationDetailPage`, `useDomainsPage`, `useApprovalsPage`, and `useAuditPage`. `LinksView.vue` and `StatsView.vue` have also been upgraded to understand application scope without duplicating transport logic.

## Deployment Shape

The repository currently ships as:

- one backend runtime (`server/app`)
- one frontend app (`web`)
- supporting infrastructure such as MySQL primary/replica and Redis

The backend uses Apache ShardingSphere-JDBC as the logical application datasource. `readwrite_ds` routes writes to `write_ds` and eligible non-transactional reads to `read_ds_0`; transactional reads stay on the primary through `transactionalReadQueryStrategy: PRIMARY`. Flyway is explicitly bound to the primary MySQL connection and does not migrate through the logical read/write splitting datasource.

This is not a microservice deployment. Module boundaries remain for ownership and tests, but day-to-day correctness is designed for a single deployed monolith rather than future service extraction.

# LinkForge Architecture

LinkForge is a modular monolith built as a Maven reactor plus a separate Vue frontend. The current design favors clear module boundaries inside one deployable runtime over independently deployed services.

## Backend

- `server/foundation`: split between pure shared-library packages and explicit runtime support. `foundation.config`, `foundation.id`, `foundation.tx`, and `foundation.util` stay framework-light library code; runtime beans such as `RequestIdFilter`, startup checks, and integration-event MyBatis wiring live under `foundation.runtime..`.
- `server/contracts/*`: shared vocabulary that still earns its maintenance cost inside the monolith. `contract-api` holds common API contracts, `contract-shortlink` carries shortlink integration-event payloads, `contract-redirect` carries redirect read/cache contracts, `contract-analytics` carries analytics contracts, and `contract-platform` carries application/domain authorization vocabulary.
- `server/accounts`: account, tenant, auth, and API-key management split into `domain`, `application`, `infrastructure`, and `interfaces` Maven modules. Its application layer now depends on ports/shared contracts instead of infrastructure classes or runtime-security helpers.
- `server/platform`: control-plane ownership for tenant applications, domains, quotas, and policies. Domain value objects and policies own application keys, hostnames, quota limits, domain authorization, and mutation-approval decisions while application services keep persistence and contract orchestration.
- `server/governance`: approval and audit management. The approval aggregate owns lifecycle transitions, and domain policies own self-approval, approval-matrix, sensitive-operation, decision-reason, snapshot, and audit vocabulary.
- `server/shortlink`: write-side shortlink management split into `domain`, `application`, `infrastructure`, and `interfaces`. It owns durable shortlink state, short-code allocation decisions, destination-change policy, redirect status validation, tag rules, and internal mutation facts.
- `server/redirect`: cache-backed redirect serving split into `domain`, `application`, `infrastructure`, and `interfaces`. Redirect correctness uses Redis plus the authoritative shortlink read API on cache miss; domain policy objects own lookup-key validation, availability decisions, and risk decisions without owning link truth.
- `server/analytics`: visit recording and read models split into `domain`, `application`, `infrastructure`, and `interfaces`. Domain policy objects own visit facts, normalized dimensions, aggregation windows, and export-window decisions while read models remain private.
- `server/app`: Spring Boot executable composition root. `LinkForgeApplication` explicitly imports context-owned runtime modules (`FoundationRuntimeModule`, `AccountsRuntimeModule`, `ShortlinkRuntimeModule`, `RedirectRuntimeModule`, `AnalyticsRuntimeModule`, `PlatformRuntimeModule`, and `GovernanceRuntimeModule`) instead of package scans or app-owned wrappers; the bounded-context runtime modules now live with the context export surface in `interfaces`.
- `server/integration-tests`: Testcontainers-based integration verification for cross-module behavior.

## DDD Context Map

The backend bounded contexts are code-ownership boundaries inside one deployed monolith.
They are not independently deployed services.

### Accounts

Owns tenants, users, roles, API keys, authentication state, and account-status checks through
`Tenant`, `AccountUser`, `ApiKey`, role assignments, value objects, and account/role/API-key policies.
Accounts may publish authentication and account-status capabilities, but persistence details,
token parsing internals, and role storage remain private to the context.

### Platform

Owns tenant applications, domains, quotas, and application policies.
`Application`, `Domain`, `ApplicationQuota`, `ApplicationPolicy`, `ApplicationKey`,
`DomainHostname`, `MonthlyLinkLimit`, `DomainAuthorizationPolicy`, `QuotaPolicy`, and
`ApplicationPolicyDecisionService` carry the control-plane domain vocabulary.
Platform publishes application scope, domain hostname lookup, and quota views through
`contract-platform`.

### Shortlink

Owns durable link state, link lifecycle, destination rules, tags, revisions, and shortlink
mutation events. `ShortLink` is the aggregate root; `ShortCode`, `HttpUrl`, query-forward
value objects, `RedirectStatusCode`, `LinkTagPolicy`, `DestinationChangePolicy`, and
`ShortCodeAllocationPolicy` own validation and policy decisions.
Other contexts may read redirect metadata, ownership, and summaries only through
`contract-shortlink`.

### Redirect

Owns traffic-plane redirect resolution, Redis cache behavior, preview/not-found responses,
and lightweight visit-event append. `RedirectLookupKey`, `RedirectDecision`,
`RedirectAvailabilityPolicy`, and `RedirectRiskPolicy` keep redirect decisions pure and
testable. Redirect does not own link truth; cache misses use the shortlink published read
contract.

### Analytics

Owns visit ingestion, aggregates, detail storage, statistics reads, and export integration.
`VisitFact`, `VisitDimension`, `AggregationWindow`, `AnalyticsExportRequest`,
`VisitNormalizationPolicy`, `AggregationPolicy`, and `AnalyticsExportPolicy` describe
analytics facts and policies without turning reporting rows into aggregates. Analytics read
models remain private. Cross-context link enrichment uses published shortlink contracts.

### Governance

Owns approval request lifecycle, approval decisions, sensitive-operation records, and audit
logs. `ApprovalRequest` owns submit/approve/execute state transitions; `ApprovalActor`,
`ApprovalDecisionReason`, `ApprovalSnapshot`, `ApprovalMatrixPolicy`,
`SensitiveOperationPolicy`, and `AuditPolicy` own approval vocabulary and decision rules.
Governance exposes published approval contracts where cross-context callers still need
approval orchestration.

## Tactical DDD Rules

- `domain` owns aggregate behavior, invariants, value objects, domain services, and internal domain events.
- `application` owns use-case orchestration, transactions, repository ports, authorization input handling, and integration-event publication.
- `interfaces` owns HTTP mapping, request validation, principal extraction, and transport response shaping.
- `infrastructure` owns MyBatis, Redis, schedulers, and persistence mapping.
- `contracts/*` owns published language shared across bounded contexts.
- Bounded contexts must not import another context's `domain`, `application`, `infrastructure`, or `interfaces` packages.
- Cross-context callers use `contracts/*` ports and views only; provider application services remain private implementation details.
- Internal domain events stay framework-free. Application/infrastructure code translates them to stable integration events after persistence succeeds.
- Compatibility facades may remain at controller/application boundaries, but lifecycle, quota, approval, tag, redirect, and normalization rules belong in domain aggregates or policy objects.

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
- supporting infrastructure such as MySQL primary/replica and Redis

The backend uses Apache ShardingSphere-JDBC as the logical application datasource. `readwrite_ds` routes writes to `write_ds` and eligible non-transactional reads to `read_ds_0`; transactional reads stay on the primary through `transactionalReadQueryStrategy: PRIMARY`. Flyway is explicitly bound to the primary MySQL connection and does not migrate through the logical read/write splitting datasource.

This is not a microservice deployment. Module boundaries remain for ownership and tests, but day-to-day correctness is designed for a single deployed monolith rather than future service extraction.

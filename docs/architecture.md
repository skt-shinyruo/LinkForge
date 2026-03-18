# LinkForge Architecture

LinkForge is a modular monolith built as a Maven reactor plus a separate Vue frontend. The current design favors clear module boundaries inside one deployable runtime over independently deployed services.

## Backend

- `server/foundation`: shared technical library code used across modules. It provides low-level building blocks such as IDs, configuration objects, utilities, and transaction helpers. The guarded `foundation` packages are intended to stay pure library code rather than becoming implicit runtime bean buckets.
- `server/contracts/*`: published language between modules. `contract-api` holds common API contracts, `contract-shortlink` carries shortlink integration-event payloads, `contract-redirect` carries redirect read/cache contracts, and `contract-analytics` carries analytics contracts.
- `server/accounts`: account, tenant, auth, and API-key management. It is still packaged as one Maven module, but its application layer now depends on internal ports instead of directly on infrastructure classes.
- `server/shortlink`: write-side shortlink management split into `domain`, `application`, `infrastructure`, and `interfaces`. It owns durable shortlink state and emits integration events for downstream projections.
- `server/redirect`: read-side redirect serving split into `domain`, `application`, `infrastructure`, and `interfaces`. In monolith mode, redirect correctness uses an authoritative shortlink-backed metadata source on cache miss. Async projectors remain in place to warm Redis and maintain a projection for recovery/backfill scenarios.
- `server/analytics`: visit recording and read models split into `domain`, `application`, `infrastructure`, and `interfaces`.
- `server/app`: Spring Boot executable that assembles the modular monolith.
- `server/integration-tests`: Testcontainers-based integration verification for cross-module behavior.

## Redirect Correctness Path

Redirect reads follow this order:

1. Redis redirect cache (`LinkCachePort`)
2. Authoritative shortlink-backed metadata source (`LinkMetaSourcePort`)
3. Negative-cache write for true misses

Shortlink mutations evict redirect cache entries only after transaction commit via `AfterCommit`, so Redis cannot diverge from durable state on rollback. The redirect projector jobs still consume shortlink integration events, but they are now warm/recovery infrastructure instead of the source of truth for monolith correctness.

## Frontend

The frontend is a Vue 3 + Vite SPA under `web/`.

- `views/`: route-level orchestration
- `components/`: presentational UI, including the shared `AppPageShell` and links-specific subcomponents
- `composables/`: page/session behavior extracted from views
- `services/`: HTTP-facing transport helpers and shared types
- `stores/`: application state such as auth

`LinksView.vue` now orchestrates the page by delegating behavior to `useLinksPage.ts` and rendering dedicated create/list components. `StatsView.vue` and `TagsView.vue` reuse the shared page shell and service-layer API helpers instead of embedding repeated session/navigation code.

## Deployment Shape

The repository currently ships as:

- one backend runtime (`server/app`)
- one frontend app (`web`)
- supporting infrastructure such as MySQL and Redis

This is not a microservice deployment. Async projectors and contracts exist to keep module boundaries explicit and to preserve a path for future separation, but day-to-day correctness is designed for a single deployed monolith.

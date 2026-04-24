# LinkForge Single-Runtime Architecture Simplification Design

**Date:** 2026-04-24

## Problem Statement

LinkForge already presents itself as a modular monolith, but several runtime paths still carry complexity that only makes sense if future service extraction remains a top priority.

In the current codebase:

- `redirect` reads are described as a read-side module, yet cache misses still fall back to `shortlink` as the authoritative source.
- `redirect` also keeps a projection table and projector job, so redirect correctness is maintained by both direct cache invalidation and async projection machinery.
- the redirect request path synchronously records analytics side effects, which keeps the hottest user-facing path coupled to analytics write cost.
- governance is technically decoupled through contracts, but callers still assemble generic approval payloads and actor fields by hand.
- some `contracts/*` and adapter layers exist primarily to preserve hypothetical future service boundaries rather than to simplify today's single-process runtime.

The result is a codebase with strong boundary intent but extra moving parts in the most important runtime flows.

## Constraints

- The system should remain a single deployed Spring Boot monolith plus the existing Vue frontend.
- This design intentionally does not optimize for future service extraction.
- Existing user-facing HTTP behavior should remain stable unless a change is required to simplify runtime mechanics.
- Existing layering discipline still matters: `domain` stays pure, controllers stay protocol-facing, and application code should not depend on servlet or hidden runtime context.

## Goals

1. Simplify the runtime path for redirect correctness.
2. Reduce duplicate consistency mechanisms between `shortlink` and `redirect`.
3. Remove synchronous analytics work from the redirect hot path, except for the lightest possible event append.
4. Replace generic governance submission protocols with narrower monolith-oriented application APIs.
5. Keep module ownership clear without continuing to simulate service boundaries that the product no longer plans to use.

## Non-Goals

- Do not merge all bounded contexts into one undifferentiated package.
- Do not redesign product features, permissions, or page flows.
- Do not remove existing layering rules that protect `domain` and `application` from transport/runtime leakage.
- Do not attempt a microservice-ready abstraction strategy.

## Recommended Approach

Treat bounded contexts as code-ownership units inside one runtime, not as proto-services.

That means:

- keep the existing module split where it still improves ownership and testability
- remove runtime mechanisms whose only value is preserving a future extraction path
- allow direct cross-context application-service calls inside the monolith when that is simpler than routing through generic contracts
- continue forbidding cross-layer leakage such as controllers reaching into repositories or domains depending on infrastructure

This is a simplification strategy, not a full architecture rewrite.

## Target Architecture

### Design Rules

1. `shortlink` is the single source of truth for link state.
2. `redirect` is a cache-backed read module, not an independently projected read model.
3. redirect correctness uses exactly one consistency mechanism: post-commit cache eviction plus read-through cache refill.
4. redirect requests may append a lightweight visit event, but they must not synchronously perform full analytics aggregation work.
5. governance use cases are exposed as narrow application APIs, not as a generic cross-context approval protocol.
6. cross-context calls inside the monolith may depend on exported application services and dedicated DTOs, but must not reach into another context's repositories, entities, or runtime framework helpers.

### Module Responsibilities

#### `shortlink`

- owns durable link state and link mutation rules
- owns link availability fields and redirect-related metadata
- exposes a small read-oriented application API for redirect lookups and analytics enrichment
- performs post-commit cache eviction after create, update, archive, restore, and delete

#### `redirect`

- owns redirect request parsing, availability checks, preview logic, not-found behavior, and redirect response generation
- reads from Redis first
- on cache miss, calls `shortlink`'s read service directly
- writes positive and negative cache entries as part of read-through behavior
- does not own a projection table or projector job

#### `analytics`

- owns visit-event ingestion, aggregation, event detail storage, and statistics queries
- receives a lightweight visit event from the redirect path
- computes PV, UV, dimensions, and detail records asynchronously
- enriches query results from `shortlink` through narrow read APIs instead of relying on a dedicated shortlink catalog projector

#### `governance`

- owns approval state, approval decisions, and audit logging
- exposes explicit monolith-oriented application operations such as:
  - request link destination change approval
  - request analytics detail export approval
- owns translation from business request to persisted approval model
- callers provide business inputs and actor identity, not preassembled governance protocol payloads

#### `contracts/*`

- remain only where they provide durable value in-process
- are not the default mechanism for every cross-context interaction
- should be reduced to:
  - common API response contracts where still useful
  - a small set of stable value objects or event payloads that remain worth sharing

### Layering Rules

The existing layering discipline should remain in force:

- `interfaces` translate HTTP and runtime concerns into explicit application inputs
- `application` owns use cases, orchestration, validation, and cross-context coordination
- `domain` owns business invariants and stays framework-free
- `infrastructure` owns MyBatis, Redis, scheduling, and other technical adapters

The key simplification is not "remove layers". The key simplification is "stop adding contracts and runtime machinery whose only purpose is future extraction".

## Target Runtime Flows

### Redirect Request Flow

1. request enters `redirect`
2. `redirect` normalizes host and code
3. lookup `host + code` in Redis
4. if hit, use cached metadata
5. if miss, call a `shortlink` read application service that returns the minimal redirect metadata
6. if found, refill cache and continue
7. if not found, write a negative cache entry and return not-found
8. evaluate enabled/expired/preview behavior
9. append one lightweight visit event
10. return preview or redirect response

There is no redirect projection table in this target flow.

### Shortlink Mutation Flow

1. mutation is validated and persisted in `shortlink`
2. transaction commits
3. post-commit hook evicts the affected redirect cache keys
4. the next redirect request refills cache through the `shortlink` read API

There is no second correctness channel through redirect projector side effects.

### Analytics Flow

1. redirect appends a lightweight event containing tenant, link, timestamp, and visit context
2. background jobs consume the event stream
3. jobs update aggregates, dimension tables, and event-detail storage
4. stats queries read the aggregated outputs
5. if query responses need link labels or URLs, analytics asks `shortlink` for those through a narrow read API

This keeps redirect latency independent from the full analytics write path.

### Governance Flow

1. caller invokes a specific governance use case
2. caller passes business identifiers and actor identity
3. governance builds approval records, snapshots, and audit entries internally
4. caller receives a typed approval result

Callers no longer assemble generic `beforeSnapshot`, `afterSnapshot`, and actor-role payloads for a catch-all submission port.

## Concrete Simplifications

### Remove From `redirect`

- redirect projection table usage
- shortlink-to-redirect projector jobs
- cache writes performed by redirect projector side effects
- read-path dependence on projected redirect rows

### Keep In `redirect`

- `RedirectService`
- request/response mapping
- preview/unavailable rendering
- Redis cache access
- negative-cache support

### Remove From Redirect Hot Path

- synchronous PV/UV/dimension aggregation updates
- synchronous detail-event enrichment beyond what is needed to append a lightweight visit event

### Replace In `governance`

- generic approval submission interfaces used as a universal cross-context protocol
- caller-side assembly of governance snapshots and actor-role payloads

with:

- narrow application use cases per approval scenario

## Migration Plan

### Phase 1: Simplify Redirect Read Path

- add a dedicated `shortlink` read application service for redirect metadata
- switch `redirect` cache-miss behavior to call that service directly
- keep post-commit cache eviction as-is
- remove redirect projection readers from the request path

**Acceptance criteria**

- redirect requests no longer require redirect projection storage
- cache miss behavior depends only on Redis plus `shortlink` read service

### Phase 2: Remove Redirect Projection Machinery

- delete redirect projection job, mapper, and related data-path wiring
- delete projection-specific tests and replace them with read-through cache tests

**Acceptance criteria**

- no scheduled redirect projector remains
- redirect correctness still passes through read-through cache tests and integration tests

### Phase 3: Move Analytics Off The Hot Path

- introduce a minimal visit-event append step in the redirect path
- make background jobs responsible for aggregate generation
- keep existing query APIs stable while changing how data is produced

**Acceptance criteria**

- redirect request flow no longer performs full analytics aggregation writes
- analytics queries continue to return equivalent results for the covered scenarios

### Phase 4: Replace Generic Governance Submission

- add explicit governance application services for the existing approval scenarios
- migrate `shortlink` and `analytics` to those services
- delete the old generic approval submission contract where unused

**Acceptance criteria**

- approval callers no longer construct generic governance payloads
- governance owns request translation and persistence details internally

### Phase 5: Reduce Low-Value Contracts And Rules

- remove contracts and adapters that only survive as monolith-internal indirection
- keep only the contracts that still earn their maintenance cost
- relax architecture tests that currently forbid any direct bounded-context dependency
- retain tests that protect layer integrity

**Acceptance criteria**

- cross-context application calls are allowed where intentional
- repository/entity/runtime leakage across contexts remains forbidden

## Testing Strategy

The simplification must be verified at three levels:

- unit tests for `redirect` read-through behavior, cache refill, negative cache, and availability rules
- unit and integration tests for shortlink post-commit cache eviction
- integration tests for:
  - redirect after create/update/archive/delete
  - analytics event append plus eventual aggregate visibility
  - approval request flows through the new governance APIs

Architecture tests should continue to enforce:

- controllers only in `interfaces`
- `domain` free of outer-layer dependencies
- `application` free of servlet/runtime thread-context types

Architecture tests should stop enforcing:

- absolute prohibition on direct bounded-context dependencies

That rule is useful for future service extraction but actively harmful for a long-term single-runtime design.

## Risks And Trade-Offs

- Future service extraction becomes harder because some generic contracts will be removed.
- Analytics migration may require a short verification phase that compares event-derived aggregates against the current path before the old synchronous writes are removed.
- Redirect cache correctness becomes more dependent on reliable post-commit eviction, so those flows need stronger integration coverage.

These trade-offs are acceptable because the explicit product direction is to optimize for a long-lived monolith, not for eventual service separation.

## Success Criteria

The simplification is successful when all of the following are true:

- redirect no longer maintains an independent projection model
- redirect correctness depends on only one cache-consistency strategy
- redirect requests no longer synchronously perform full analytics aggregation work
- governance callers no longer handcraft generic approval payloads
- the codebase retains clear module ownership and layer integrity without paying for speculative service-boundary abstractions

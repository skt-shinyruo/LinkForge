# LinkForge DDD Refactor Design

**Date:** 2026-04-28

## Problem Statement

LinkForge already has a strong modular-monolith shell: bounded-context Maven modules, layered packages, explicit runtime module imports, contracts, and architecture tests. The remaining problem is that the code is not consistently domain-driven inside those boundaries.

Several symptoms show up in the current backend:

- some `application` services still act as broad procedural facades rather than use-case coordinators
- important business decisions are split across application services, controllers, guards, persistence adapters, and runtime configuration
- domain objects exist in places such as `shortlink`, but aggregate boundaries and domain events are not explicit enough
- cross-context dependencies are being corrected through published contracts, but the ubiquitous language for those contracts is not yet documented as the source of truth
- `app/security` still knows too much about `accounts` implementation details

The refactor should make the existing modular monolith more genuinely DDD-oriented without turning LinkForge into independently deployed services.

## Constraints

- Keep one deployed Spring Boot backend runtime and the existing Vue frontend.
- Focus this design on `server/`; frontend changes are out of scope unless a backend API shape change requires a small adapter update.
- Preserve current user-facing product behavior unless a behavior change is explicitly required to restore a domain invariant.
- Internal Java APIs may change.
- HTTP APIs should remain stable during the first pass unless the implementation plan identifies a high-value simplification and covers migration tests.
- Keep existing Maven bounded-context modules unless a new module directly improves boundary clarity.
- Align with `2026-04-27-cross-context-contract-ports-design.md`: cross-context consumers should depend on published contracts, not another context's `application` package.

## Goals

1. Define a stable context map and ubiquitous language for the backend.
2. Turn `shortlink` into the tactical DDD pilot because it is the core domain and already has the richest domain model.
3. Move business invariants from procedural application code into aggregates, value objects, and domain services.
4. Split broad application facades into explicit use cases and query services.
5. Publish cross-context language through `contracts/*` ports and records.
6. Decouple `app/security` from accounts infrastructure and accounts domain internals.
7. Strengthen architecture tests so the new DDD boundaries cannot silently regress.

## Non-Goals

- Do not rewrite the whole backend at once.
- Do not introduce CQRS/event sourcing as a broad architectural style.
- Do not split the monolith into microservices.
- Do not create generic repository abstractions or framework-heavy DDD infrastructure for its own sake.
- Do not force every class to match a DDD pattern name. Use tactical patterns only where they clarify real business rules.
- Do not redesign analytics storage, redirect cache mechanics, or governance workflows beyond the boundary changes described here.

## Recommended Approach

Use a phased DDD hardening strategy.

The current module structure is a good base, so the refactor should keep it and change the contents of the modules:

- `domain` owns invariants, aggregate behavior, value objects, domain services, and domain events
- `application` owns use-case orchestration, transactions, authorization decisions expressed as explicit inputs, repository calls, and integration-event publication
- `interfaces` owns HTTP mapping, request validation, principal extraction, and transport response shaping
- `infrastructure` owns MyBatis, Redis, schedulers, external adapters, and persistence mapping
- `contracts/*` owns published language used across bounded contexts
- `app` owns only executable composition, global security wiring, global error handling, scheduling bootstrap, and migrations

The first implementation plan should start with `shortlink`, then apply the same shape to `accounts` security exports and finally tighten the remaining contexts.

## Context Map

### `accounts`

Responsibilities:

- tenants, users, roles, API keys, authentication state, token version state
- account status checks used by security and management APIs

Published language:

- authenticated user principal
- API key authentication result
- account status
- standard roles

DDD direction:

- keep account persistence and token implementation private
- expose authentication and account-status capabilities through explicit ports or exports
- stop leaking `accounts.domain.Roles` or `accounts.infrastructure.security.JwtService` into `app`

### `platform`

Responsibilities:

- tenant applications
- domains
- quotas
- application policies

Published language:

- application scope
- domain hostname lookup
- application quota view
- legacy application binding only while compatibility requires it

DDD direction:

- model `Application`, `Domain`, `ApplicationQuota`, and `ApplicationPolicy` as domain concepts with explicit invariants
- keep quota and domain authorization rules in domain/application, not controllers

### `shortlink`

Responsibilities:

- durable shortlink state
- link lifecycle
- destination and query-forward rules
- tags
- link revision behavior
- integration events for link mutations

Published language:

- redirect metadata
- link ownership
- link summary
- shortlink mutation integration events

DDD direction:

- make `ShortLink` the first aggregate root to receive full tactical DDD treatment
- move link lifecycle and destination invariants into the aggregate/value objects
- publish read-only cross-context needs through `contract-shortlink`

### `redirect`

Responsibilities:

- resolve `host + code`
- use Redis positive and negative cache
- apply redirect availability decisions
- produce redirect/preview/not-found responses
- append lightweight visit records

Published language:

- redirect cache contract only where still needed

DDD direction:

- keep redirect as a traffic-plane context, not owner of link truth
- depend on shortlink published contracts for authoritative link metadata
- keep redirect availability decisions explicit and testable

### `analytics`

Responsibilities:

- visit event ingestion
- aggregate and detail storage
- stats reads
- export approval integration

Published language:

- visit record
- visit context
- application click usage

DDD direction:

- depend on shortlink contracts for link summaries/ownership
- keep analytics read models private to analytics
- keep export approval interaction through governance contracts

### `governance`

Responsibilities:

- approval request lifecycle
- approval decisions
- audit logs
- sensitive operation records

Published language:

- sensitive operation
- approval request view
- approval submission/execution ports

DDD direction:

- model `ApprovalRequest` as an aggregate with explicit state transitions
- move generic caller-assembled payload logic toward typed use cases where possible
- keep audit log creation as part of governance application orchestration

## Tactical DDD Pilot: `shortlink`

### Target Aggregate

`ShortLink` remains the aggregate root for link lifecycle and mutation rules.

It should own:

- identity: `linkId`, `tenantId`, optional `applicationId`, optional `domainId`
- code: `ShortCode`
- destination: target URL and unavailable landing URL
- lifecycle: active, archived, deleted/restored behavior
- availability: enabled flag, expiry, preview behavior, redirect status
- query forwarding: mode and allowlist
- authorship: creator type and creator id
- revision/version data needed for optimistic updates and auditability

The aggregate should expose behavior methods instead of letting application code mutate fields indirectly:

- `archive(requestedAt)`
- `restore()`
- `delete()`
- `changeDestination(...)`
- `changeAvailability(...)`
- `changeQueryForwarding(...)`
- `assignScope(applicationId, domainId)`
- `recordRevision(...)` if revision behavior stays inside the aggregate

### Value Objects

Keep existing value objects where they already work and add narrowly where they remove repeated validation:

- existing: `ShortCode`, `HttpUrl`, `QueryForwardMode`, `QueryForwardAllowlist`, `CreatedByType`
- target additions if the implementation plan confirms repeated logic:
  - `LinkScope`
  - `CreatedBy`
  - `RedirectPolicy`
  - `LinkAvailability`
  - `Destination`

Do not add a value object just to rename a primitive. Add it only when it owns validation, normalization, or comparison behavior.

### Domain Services

Use domain services only for rules that require collaboration beyond one aggregate:

- code allocation policy, if custom-code and generated-code collision handling remains spread across services
- destination trust policy, if platform/governance rules determine whether a destination change needs approval
- domain binding policy, if link scope must be checked against platform-owned domain/application ownership

These services should depend on domain-facing ports, not infrastructure adapters.

### Domain Events

Introduce internal domain events for link facts:

- `ShortLinkCreated`
- `ShortLinkUpdated`
- `ShortLinkArchived`
- `ShortLinkRestored`
- `ShortLinkDeleted`
- `ShortLinkDestinationChangeRequested` if governance approval is required

Application code should collect those events after a successful aggregate mutation and translate them into stable integration events under `contract-shortlink/event`.

Domain events are not the same as integration events:

- domain events are internal facts in the owning bounded context
- integration events are stable published messages for other contexts

## Application Layer Shape

Replace broad facades with explicit use cases over time. The first target is `ShortLinkService`.

Preferred use-case split:

- `CreateShortLinkUseCase`
- `UpdateShortLinkUseCase`
- `ArchiveShortLinkUseCase`
- `RestoreShortLinkUseCase`
- `DeleteShortLinkUseCase`
- `ImportShortLinksUseCase`
- `ExportShortLinksQuery`
- `BrowseShortLinksQuery`
- `GetShortLinkDetailQuery`
- `ManageTagsUseCase`

Application use cases may:

- authorize actor and tenant/application scope
- load aggregates through repository ports
- call aggregate behavior
- save through repository ports
- schedule post-commit cache eviction through an application port
- translate domain events into integration events
- call published contracts from other bounded contexts

Application use cases must not:

- read `AuthContext`
- depend on servlet/web types
- depend on MyBatis mappers, Redis clients, or infrastructure entities
- encode lifecycle transitions that belong inside the aggregate
- depend on another bounded context's application package

## Cross-Context Contracts

This DDD refactor should follow the stricter contract direction from `2026-04-27-cross-context-contract-ports-design.md`.

Rules:

- consumers depend on `com.linkforge.contract..`
- providers implement contracts inside their owning context
- contract records use published language, not persistence entity names
- contract modules stay framework-light
- no bounded context imports another context's `domain`, `application`, `infrastructure`, or `interfaces` package

Initial contract priorities:

1. `contract-shortlink`: redirect metadata, ownership, summary reads
2. `contract-analytics`: visit recording and application click usage
3. `contract-platform`: application scope, domain hostname, quota
4. `contract-governance`: approval submission/execution only where typed direct use cases are not yet available
5. `contract-api`: shared error/response vocabulary

## `app/security` Boundary

Global Spring Security wiring can stay in `app`, but it should not know accounts implementation details.

Target shape:

- `app/security` depends on a published authentication API, not `accounts.infrastructure.security.JwtService`
- API key authentication depends on an accounts-published port, not `ApiKeyService` internals
- role constants move to `foundation.security.StandardRoles` or a contract package
- accounts remains the owner of token parsing, API key lookup, and account status validation

Possible published ports:

- `JwtPrincipalVerifier`
- `ApiKeyAuthenticator`
- `AccountStatusVerifier`

The exact names can be refined in the implementation plan, but the boundary rule should be fixed before code movement begins.

## Phased Migration

### Phase 1: Domain Map And Guardrail Baseline

- update architecture documentation with the context map, aggregate ownership, and published language
- add or tighten architecture tests for DDD boundary rules
- make cross-context `application` dependencies fail as expected before migration

Acceptance criteria:

- `docs/architecture.md` documents context ownership and published language
- architecture tests describe the intended final dependency direction
- no production behavior changes

### Phase 2: Shortlink Tactical DDD Pilot

- refactor `ShortLink` into a behavior-rich aggregate
- add or refine value objects for repeated shortlink validation
- move lifecycle and destination invariants out of application services
- split shortlink application behavior into explicit use cases
- preserve existing HTTP behavior through interface tests

Acceptance criteria:

- shortlink lifecycle tests are centered on domain behavior
- application tests verify orchestration and side effects, not duplicated domain rules
- controllers remain thin adapters

### Phase 3: Shortlink Published Contracts

- align shortlink read contracts with cross-context needs
- move redirect and analytics consumers off shortlink application classes
- keep provider implementations inside shortlink
- retire compatibility adapters that are no longer used

Acceptance criteria:

- no non-shortlink module imports `com.linkforge.shortlink.application..`
- redirect and analytics tests still pass
- contract tests cover mapping from shortlink domain/application results to published records

### Phase 4: Accounts Security Export

- introduce accounts-owned authentication/status exports
- update `app/security` to depend on those exports
- move shared roles out of accounts domain if still referenced outside accounts
- add architecture tests that prevent `app` from importing accounts infrastructure

Acceptance criteria:

- `app/security` does not import `accounts.infrastructure..`
- non-accounts code does not import `accounts.domain.Roles`
- existing JWT, cookie, CSRF, and API key tests pass

### Phase 5: Platform, Governance, Analytics Hardening

- model platform policy/quota/domain invariants explicitly
- model governance approval state transitions explicitly
- keep analytics read models private and publish only stable query/usage contracts
- reduce broad application service facades where they hide multiple use cases

Acceptance criteria:

- each context has clear aggregate ownership
- use-case tests describe application orchestration
- domain tests cover state transitions and invariants

## Testing Strategy

Use tests as the migration driver for each phase:

- domain unit tests for aggregate/value-object invariants
- application unit tests for use-case orchestration, authorization input handling, repository calls, post-commit hooks, and integration-event publication
- controller tests for request/response mapping only
- infrastructure tests for mapper/entity persistence behavior
- integration tests for representative flows:
  - create/update/archive/restore link then redirect
  - API key creates link then OpenAPI reads it
  - destination change requiring governance approval
  - redirect visit event reaches analytics ingestion path

Architecture tests should enforce:

- `domain` has no dependency on Spring, Servlet/Jakarta runtime APIs, MyBatis, Redis, or outer layers
- `application` has no dependency on web/servlet/runtime thread-context helpers
- bounded contexts do not depend on other bounded contexts' inner packages
- controllers live under `interfaces`
- repositories/mappers are used only from application/infrastructure as appropriate
- contracts do not depend on Spring, Servlet/Jakarta runtime APIs, or persistence APIs

## Risks And Mitigations

### Risk: Over-abstracting A Working Monolith

Mitigation:

- keep one runtime
- avoid generic frameworks
- add tactical DDD types only when they own real business behavior

### Risk: Large Churn In `shortlink`

Mitigation:

- migrate one use case at a time
- preserve HTTP behavior with controller and integration tests
- keep compatibility facades temporarily if needed, but mark them internal

### Risk: Conflicting Architecture Specs

Mitigation:

- treat this design as a refinement of the current DDD direction
- follow the stricter cross-context contract rule from `2026-04-27-cross-context-contract-ports-design.md`
- update `docs/architecture.md` during Phase 1 so the SSOT is current

### Risk: Domain Events Become Infrastructure Events

Mitigation:

- keep domain events inside domain/application
- translate to integration events at the application boundary
- keep JSON/outbox/versioning concerns out of domain objects

## Success Criteria

The DDD refactor is successful when:

- each bounded context has documented ownership and published language
- `shortlink` business rules can be understood from domain tests and aggregate methods
- broad application facades are replaced or wrapped by explicit use cases
- cross-context code depends on `contracts/*`, not foreign application/domain/infrastructure packages
- `app/security` no longer imports accounts infrastructure or accounts domain internals
- architecture tests lock the new dependency rules
- existing user-facing API behavior remains stable through focused tests and integration verification


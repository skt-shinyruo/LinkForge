# LinkForge Backend Architecture Phased Refactor Design

**Date:** 2026-04-06

## Problem Statement

The backend claims a modular-monolith architecture, but several critical boundaries are still soft:

- `application` code in multiple bounded contexts depends on implicit Spring runtime state such as security context, transaction synchronization, and request-scoped helpers.
- several controllers still perform business orchestration, cross-context lookups, request-window defaults, and approval assembly instead of acting as protocol adapters.
- `foundation` still behaves like a mixed shared-kernel plus runtime bucket, so compile-time ownership of web/security/transaction/persistence support is unclear.
- `accounts` is still a single runtime-heavy Maven module while other bounded contexts are physically split into `domain/application/infrastructure/interfaces`.
- `app` imports per-context module wrappers, but those wrappers still expose entire package trees through broad `@ComponentScan`, so the true module export surface is not explicit.
- some `shortlink` application and domain APIs still leak transport or persistence concerns into otherwise business-oriented boundaries.

The net effect is that several architecture rules are enforced only by conventions and ArchUnit checks, not by compile-time structure.

## Constraints

- Scope is limited to `server/`. The `web/` frontend is out of scope for this refactor.
- Breaking changes are allowed across internal module APIs, HTTP APIs, configuration names, and persistence shape.
- The work must be executed in phases. Each phase must be independently verifiable and committable.
- The target remains a single deployable Spring Boot monolith, not independently deployed services.

## Goals

1. Make `application` layers callable without hidden dependence on Spring security context, request context, or transaction thread state.
2. Reduce controllers to protocol adapters and move business orchestration into explicit use cases.
3. Split `foundation` into narrow library/runtime/bootstrap ownership instead of one mixed technical bucket.
4. Restructure `accounts` into the same physical layering model as the other bounded contexts.
5. Replace broad package-scan composition with explicit module export surfaces owned by each context.
6. Remove the most obvious transport and persistence leakage from `shortlink` application/domain boundaries.

## Non-Goals

- Do not split LinkForge into microservices.
- Do not preserve binary compatibility with current internal APIs.
- Do not optimize for zero-diff migrations; structural clarity wins over minimizing churn.
- Do not redesign product behavior unless a behavior change is required to restore architectural consistency.

## Recommended Approach

Use a boundary-first phased refactor:

1. remove implicit runtime context dependencies from `application`
2. move orchestration out of controllers
3. split `foundation`
4. split `accounts`
5. make module exports and composition explicit

This order is recommended because it first fixes the cross-cutting seams that currently infect multiple bounded contexts. If physical module splitting happens before those seams are removed, the codebase will spend several phases in a structurally prettier but operationally more tangled state.

## Target Architecture

### Layer Responsibilities

- `interfaces`
  - decode HTTP or other transport protocols
  - authenticate and map runtime principals to explicit application inputs
  - call exactly one use case or one thin application facade
  - encode results back to protocol responses
- `application`
  - own use cases, orchestration, validation of application-level rules, and cross-context interactions through published contracts
  - accept explicit inputs such as `tenantId`, actor identity, request time, visit data, and pagination/query parameters
  - publish side effects through ports
  - never read thread-local request/security/transaction state
- `domain`
  - own business model, invariants, and pure domain behavior
  - never depend on web, servlet, security, MyBatis, or runtime framework classes
- `infrastructure`
  - implement application ports
  - adapt persistence, cache, event store, external systems, and framework-specific integration
- `app`
  - remain the single bootstrap composition root
  - assemble explicit module exports and runtime adapters

### Runtime Context Model

Hidden runtime state is replaced with explicit application inputs and ports:

- security context becomes explicit actor inputs, for example `UserActor`, `ApiKeyActor`, or other focused application records
- request-derived visit data becomes an application input model instead of `foundation.web.VisitInfo`
- transaction post-commit behavior becomes an application port such as `PostCommitHookPort`
- current time comes from injected `Clock` or explicit timestamp parameters, never `LocalDateTime.now()` inside use cases

### Cross-Context Dependency Rule

Bounded contexts may depend on:

- their own `domain`, `application`, `infrastructure`, and `interfaces`
- `contracts/*`
- narrow `foundation-*` library modules

Bounded contexts may not depend directly on other contexts' internal `application`, `domain`, `interfaces`, or `infrastructure` packages. Cross-context interactions must use published contracts or explicitly exported runtime modules.

## Concrete Design Rules

1. `application` must not depend on:
   - `foundation.security.AuthContext`
   - `foundation.web.RequestId`
   - `foundation.web.VisitInfo`
   - `foundation.runtime..`
   - `SecurityContextHolder`
   - `TransactionSynchronizationManager`
   - servlet or Spring Web types
2. controllers must not perform cross-context business decisions, defaulting rules, or approval assembly
3. transaction-after-commit behavior must flow through an application port implemented in runtime/infrastructure
4. application service interfaces must not expose `InputStream`, `OutputStream`, `MultipartFile`, `HttpServletRequest`, or `HttpServletResponse`
5. whole-bounded-context package scans from `app` are forbidden; module export surfaces must be explicit
6. `accounts` must be physically split into Maven submodules matching the same layering model used by `shortlink`, `platform`, `redirect`, `analytics`, and `governance`
7. `foundation` library modules must stay framework-light; web/security/MyBatis/startup beans must live in runtime modules

## Target Module Shape

### Foundation

Convert `server/foundation` into a Maven aggregator with the following child modules:

- `foundation/core`
- `foundation/runtime-web`
- `foundation/runtime-security`
- `foundation/runtime-persistence`
- `foundation/runtime-tx`
- `foundation/runtime-startup`

Ownership requirements:

- `foundation/core` is the only shared-library artifact available to all bounded contexts by default
- runtime modules own Spring beans and framework adapters for their specific concern
- no single artifact may simultaneously act as shared kernel and Spring runtime bucket

### Accounts

Convert `server/accounts` into a Maven aggregator with:

- `accounts/domain`
- `accounts/application`
- `accounts/infrastructure`
- `accounts/interfaces`

This makes `accounts` structurally consistent with the other bounded contexts and lets compile-time dependencies enforce the intended layering.

### Module Export Surface

Each bounded context must expose an explicit runtime module entry point, owned by that context, that `app` imports directly. Those runtime entry points may internally import focused configuration classes, but `app` must stop importing package-wide scan wrappers like "scan everything under `com.linkforge.shortlink`".

## Phase Plan

### Phase 1: Remove Implicit Runtime Context From Application

#### Scope

- `shortlink`
- `platform`
- `governance`
- `redirect`
- shared runtime helpers that currently pull thread-local state into use cases

#### Required Changes

- replace `TenantGuard`-style current-thread checks in application services with explicit `tenantId` and actor validation
- replace `AuthContext` reads in application services with explicit actor inputs supplied by interfaces
- replace direct `AfterCommit` usage in application with an application port for post-commit hooks
- replace `foundation.web.VisitInfo` in redirect application with an application-level visit input model
- replace `LocalDateTime.now()` inside application use cases with `Clock`

#### Acceptance Criteria

- targeted application packages no longer import runtime thread-context helpers
- new architecture tests fail on those dependencies if reintroduced
- focused unit and integration tests for the modified flows pass

### Phase 2: Move Business Orchestration Out of Controllers

#### Scope

- `StatsController`
- `ShortLinkController`
- `OpenApiShortLinkController`
- `RedirectController`

#### Required Changes

- move time-window defaulting, pagination validation, scope enforcement, approval request assembly, export orchestration, and redirect decision trees into application use cases
- keep controllers as protocol adapters that translate between transport and use case inputs
- create application commands/queries for operations that are currently assembled ad hoc in controllers

#### Acceptance Criteria

- the target controllers are materially smaller and mostly linear
- controller code no longer performs cross-context business orchestration
- moved rules are covered by application-level tests

### Phase 3: Split Foundation Into Library vs Runtime Ownership

#### Scope

- convert `server/foundation` into an aggregator
- move web/security/persistence/transaction/startup support into runtime modules
- update all context dependencies to the new artifacts

#### Required Changes

- move pure shared-library code to library modules
- move Spring beans and framework adapters to runtime modules
- update architecture rules so library modules forbid runtime dependencies

#### Acceptance Criteria

- no pure foundation library module depends on Spring Web, Spring Security, or MyBatis
- runtime capabilities are consumed through explicit adapters or module imports
- the old all-in-one `linkforge-foundation` artifact no longer acts as the universal dependency bucket

### Phase 4: Split Accounts Into Layered Maven Modules

#### Scope

- physically reorganize `accounts`
- migrate tests and wiring

#### Required Changes

- move accounts domain models, application services, infrastructure adapters, and controllers into separate submodules
- keep cross-module interaction through application ports and explicit exports
- update `app` composition and tests accordingly

#### Acceptance Criteria

- `accounts.application` no longer has compile-time access to `accounts.infrastructure`
- only `accounts.interfaces` depends on web/security transport concerns
- accounts tests pass after migration

### Phase 5: Make Module Composition Explicit

#### Scope

- `server/app`
- per-context runtime module exports

#### Required Changes

- replace broad context package scans with explicit module imports
- create composition tests that verify the module graph and exported beans
- ensure `app` is the only bootstrap owner

#### Acceptance Criteria

- `LinkForgeApplication` imports explicit context modules, not package-scan wrappers over whole bounded contexts
- architecture and composition tests fail if broad scans are reintroduced
- full backend verification passes

## Verification Strategy

Each phase must be independently shippable inside the monolith and must include:

- architecture test updates that lock the new boundary
- focused unit tests for new use cases, ports, or adapters
- focused integration verification for at least one representative end-to-end path touched by the phase

The final verification bar is:

- architecture tests green
- affected module test suites green
- at least one integration-test slice green for cross-context behavior

## Risks And Mitigations

### Risk: Phase 1 creates wide signature churn

Mitigation:

- start from the highest-value use cases first
- add adapter shims only when needed to keep each phase committable
- expand architecture rules only after the replacement path exists

### Risk: Foundation and accounts splitting causes large move-only churn

Mitigation:

- do structural moves in dedicated commits
- add compile-time and architecture guards immediately after each move
- keep behavior changes separated from pure relocation commits whenever possible

### Risk: Explicit module export configuration becomes verbose

Mitigation:

- prefer explicit context-owned runtime configuration classes over hidden package scans
- accept some configuration verbosity as the cost of trustworthy module boundaries

## Success Criteria

The refactor is successful when:

- application code can be reasoned about without knowing Spring thread state
- controllers read like adapters, not business coordinators
- Maven module boundaries match the documented architecture
- `accounts` is no longer a special-case monolith inside the modular monolith
- `foundation` is no longer the catch-all technical dependency bucket
- composition ownership is explicit and test-covered

# LinkForge Backend Architecture Remediation Design

Status: Draft

## Summary

This spec defines a phased cleanup of the backend architecture in LinkForge. The current codebase is a modular monolith with explicit bounded contexts, but several seams have drifted:

- controllers in `interfaces` expose `application` DTOs directly
- application services in `platform`, `governance`, and `analytics` are too coarse and mix orchestration, validation, mapping, and policy decisions
- `platform` domain objects are mostly passive records, so invariants live in services instead of the domain
- `foundation` security types carry context-specific business semantics
- `contracts` are starting to absorb too much shared business language
- the app bootstrap owns too much composition, security routing, and exception wiring
- architecture tests cover some rules, but not all contexts

The goal is not to split the monolith or change business behavior. The goal is to move behavior to the right layer, preserve the current HTTP contract, and make the architecture enforceable.

## Goals

- keep one deployable backend runtime
- keep bounded contexts isolated from each other except through contracts
- make HTTP transport DTOs owned by `interfaces`
- keep `application` focused on use-case orchestration and ports
- move invariant-heavy logic into `domain` or domain-local policy classes
- keep `foundation` technical and context-neutral
- keep `contracts` small, stable, and cross-context only
- expand automated architecture checks so regressions fail fast

## Non-Goals

- no microservice split
- no frontend redesign
- no database sharding or schema rewrite
- no business feature expansion beyond what is needed to preserve current behavior while relocating it

## Options Considered

### Option A: Keep the current shape and add more guardrails

This is the lowest-risk option, but it leaves the codebase with the same awkward seams. It only slows new debt.

### Option B: Big-bang clean architecture rewrite

This would produce the cleanest end state, but the blast radius is too large. It would touch too many controllers, services, tests, and contract types in one pass.

### Option C: Phased hardening with compatibility-preserving refactors

This is the recommended option. It fixes the worst coupling first, keeps HTTP JSON stable, and lets each context move independently.

## Proposed Architecture

### 1. Interface Layer Owns Transport DTOs

Every controller under `..interfaces..` must return interface-local response types, not application-layer result records.

Rules:

- controllers may call application services directly
- controllers may map application results to response DTOs
- controllers must not expose `com.linkforge.*.application.*Dto` or nested service DTOs in public signatures
- request records used only for HTTP stay in `interfaces`
- response records used only for HTTP stay in `interfaces`

Expected shape:

- `platform.interfaces.web.*HttpResponse`
- `governance.interfaces.web.*HttpResponse`
- `analytics.interfaces.web.*HttpResponse`
- `accounts.interfaces.web.*HttpResponse`

Mapping lives in small local mapper classes or static factory methods inside `interfaces`.

### 2. Application Layer Becomes Use-Case Orchestration

Application services should orchestrate a single use case or a tight family of related use cases.

Rules:

- application code depends on domain objects and ports, not on HTTP, servlet, or security context APIs
- application services may accept explicit actor/input objects
- application services should not be DTO buckets with nested public records
- query and command models should be top-level types, not nested inside service classes

Recommended shape:

- `application.command.*` for write-side handlers
- `application.query.*` for read-side handlers
- `application.result.*` or `application.model.*` for internal result records

For `platform` and `governance`, the current coarse services should be split or wrapped by smaller internal handlers when the class starts owning too many rules.

### 3. Domain Layer Owns Invariants

The `platform` domain is the clearest place where rules currently live in services instead of the model.

Rules:

- hostname normalization and validity should live in a domain policy or domain service
- application-to-domain authorization should live in `domain`, not in controller code
- creation defaults such as redirect status and default scope should be expressed in domain factories or domain-local policy helpers
- records may remain passive only when they are truly value data and do not own invariants

Practical rule:

- do not rewrite every record into a rich entity
- do promote the objects that own validation, allowed transitions, or authorization rules

`shortlink.domain.ShortLink` is the good reference style. `platform.domain` should move toward that style where behavior matters.

### 4. Foundation Becomes Technical Only

`foundation` should stay a technical support layer, not a place where business identity semantics accumulate.

Rules:

- `AuthContext` remains a thin bridge to `SecurityContextHolder`
- `AuthPrincipal` should remain a minimal authenticated-subject carrier
- context-specific semantics such as API key, application scope, or actor mapping should move into context-local mappers or credential adapters
- `foundation.runtime.security` must not grow business rules or context-specific policy language

If additional credential metadata is needed, it should be attached through context-local authentication details, not by widening the shared base principal indefinitely.

### 5. Contracts Stay Small and Stable

`contracts/*` should only hold stable language shared across bounded contexts.

Rules:

- contracts may define ports, event payloads, and shared views
- contracts must not depend on `foundation.runtime..` or `foundation.security..`
- contracts should not become a general shared-kernel for unrelated business concepts
- if a payload family is only meaningful to one producer/consumer pair, split it into a narrower contract or keep it in the owning bounded context

For approval payloads, the design should prefer small, explicit payload types over a large utility class that mixes multiple business capabilities.

### 6. Bootstrap Stays Thin

`server/app` should remain the composition root only.

Rules:

- `LinkForgeApplication` only imports context runtime modules and shared technical properties
- security config should be split by request space instead of accumulating every rule in one class
- redirect traffic-plane rules should remain isolated from API auth rules
- exception handling should be split by response shape when different contexts return different envelopes

The app module may own the top-level wiring, but it should not own domain policy or context-specific business semantics.

## Migration Plan

### Phase 1: Stop leaking application DTOs through HTTP

Update controllers in `platform`, `governance`, `analytics`, and any other affected context so they return interface-local DTOs.

Add local mappers and keep the JSON shape unchanged.

### Phase 2: Split coarse application services

Introduce top-level result and request types where nested DTOs currently live inside services.

Where a service owns unrelated responsibilities, split it into smaller handlers or helper services.

### Phase 3: Tighten platform domain rules

Move hostname normalization, domain creation defaults, and domain authorization decisions out of `ApplicationProvisioningService` and into domain-local policy or factory code.

Keep the existing business outcomes the same.

### Phase 4: Narrow foundation identity semantics

Trim `AuthPrincipal` to the smallest stable identity set that still supports current authentication flows.

Move context-specific semantics into context-local adapters.

### Phase 5: Reduce contracts and bootstrap coupling

Split any overly broad contract helpers into narrower published types.

Split app security and exception wiring into smaller pieces if a single class still knows too much about unrelated request families.

### Phase 6: Expand architecture guardrails

Add architecture tests for:

- controller return types
- nested DTO leakage from application services
- context cross-dependencies
- foundation depending on bounded contexts
- contracts depending on foundation runtime/security

Make the new rules cover `accounts`, `platform`, `governance`, `analytics`, `shortlink`, and `redirect`, not just the shortlink-specific cases.

## Acceptance Criteria

- no public controller in `interfaces` returns an application-layer DTO directly
- application-layer result records are top-level types, not nested service DTO containers
- platform domain owns the rules for hostname validity and application-domain authorization
- foundation security remains technical and does not grow more context-specific semantics
- contracts remain free of foundation runtime/security dependencies and stop accumulating unrelated business language
- app bootstrap remains a thin assembly layer
- architecture tests fail if a future change reintroduces the same coupling
- current HTTP JSON output remains compatible for existing clients

## Verification

- run the backend test suite
- run targeted module tests for `platform`, `governance`, `analytics`, and `shortlink`
- run the architecture tests
- verify no controller signature still exposes `application` DTOs
- verify the published JSON payloads remain stable

## Notes

This spec intentionally avoids a microservice extraction. The target state is a cleaner modular monolith with stronger boundaries and lower cross-layer coupling.

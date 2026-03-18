# LinkForge Deep Architecture Remediation Spec

**Date:** 2026-03-18

## Problem Statement

The previous remediation pass cleaned up several surface-level issues, but the codebase still has a set of high-cost architectural inconsistencies:

- `accounts` remains a single runtime-heavy module whose application services still depend directly on Redis and Spring Security primitives, so the critical auth path does not have stable compile-time boundaries.
- `foundation` mixes pure shared-library code with runtime beans, MyBatis scanning, and web filters, which makes the shared kernel concept soft and turns `foundation` into an implicit platform bucket.
- `app` is still too knowledgeable about bounded-context internals: module composition and startup validation are centralized there instead of being owned by the relevant runtime modules.
- `redirect` still exposes transitional abstractions from the old projection-first design, and its main controller has accumulated request classification, availability checks, HTML rendering, confirm-link generation, and logging concerns in one place.
- `analytics` still concentrates stream reading, pending reclaim, record sanitation, persistence, ack, and DLQ behavior inside one scheduled job.
- The frontend improved, but `StatsView.vue` and `useLinksPage.ts` still mix transport concerns, page orchestration, formatting, browser side effects, and UI-facing state shaping.

## Goals

1. Remove remaining direct technical-framework dependencies from `accounts.application`.
2. Separate pure shared-library responsibilities from runtime/platform responsibilities around `foundation`.
3. Shrink `app` into a composition layer by pushing module-specific startup checks closer to module ownership.
4. Make `redirect` application/runtime boundaries reflect the actual monolith correctness model and split its oversized interface-layer orchestration.
5. Break the analytics ingest pipeline into smaller collaborators with single responsibilities.
6. Continue frontend decomposition so route views become orchestration shells and composables stop doubling as transport mappers plus browser-effect coordinators.

## Non-Goals

- Do not split LinkForge into independently deployable services.
- Do not redesign public API semantics or product behavior beyond what is required for the refactor.
- Do not remove async projector / analytics jobs; keep them, but make their boundaries clearer.
- Do not force a full Maven-reactor split of `accounts` in this pass; internal boundary hardening comes first.

## Recommended Approach

Use a phased remediation instead of one repo-wide refactor:

### Phase 1: Backend Boundary Hardening

Stabilize the highest-risk backend seams first:

- Invert the remaining `accounts.application` dependencies on Redis, password hashing, and token/security helpers behind ports and adapters.
- Simplify `redirect` to depend only on the abstractions it actually uses in monolith mode, then extract controller helpers so request classification, HTML rendering, confirm-link building, and logging each have a clear home.

This phase changes the most security-sensitive and latency-sensitive paths, so it should be implemented first and verified narrowly.

### Phase 2: Shared Platform vs Runtime Extraction

Clarify the platform boundary:

- Move runtime beans and runtime-only configuration out of `foundation` shared packages into explicit runtime-owned packages.
- Replace the single `AppStartupValidator` knowledge center with module-owned startup checks registered into `app`.

This reduces hidden coupling and turns architecture rules from "tests plus discipline" into stronger structural boundaries.

### Phase 3: Large-Orchestrator Decomposition

Finish by shrinking oversized orchestrators:

- Split analytics ingest into reader / decoder / writer / DLQ collaborators.
- Split remaining large frontend route logic into page-level composables, focused presentational components, and explicit UI-facing state shaping.

This phase is lower architectural risk than Phase 1 but high leverage for long-term maintainability.

## Acceptance Criteria

- `accounts.application` depends on ports, contracts, domain, and framework-neutral helpers only; direct Redis/security-hasher references are gone.
- Runtime-only web/filter/MyBatis/eventing beans are no longer treated as part of the shared-library `foundation` surface.
- `app` no longer owns all bounded-context-specific startup validation logic in one class.
- `redirect` no longer keeps unused projection abstractions in its application flow, and `RedirectController` is materially smaller with extracted collaborators.
- `AnalyticsEventIngestJob` becomes a coordinator over smaller collaborators instead of the owner of the whole ingest pipeline.
- `StatsView.vue` and `useLinksPage.ts` are materially smaller because data shaping, page orchestration, and browser side effects have been extracted.

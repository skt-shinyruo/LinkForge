# LinkForge Architecture Remediation Spec

**Date:** 2026-03-18

**Problem Statement**

LinkForge’s current codebase mixes several architectural styles:

- `accounts` still couples application services directly to infrastructure mappers/entities/security helpers.
- Redirect correctness in monolith mode still depends on async projector timing for cache/projection freshness.
- Shortlink HTTP contracts are duplicated across management and OpenAPI controllers.
- The web app concentrates too much behavior inside view SFCs, especially `LinksView.vue`.
- The repo advertises `docs/architecture.md`, but the document is missing, and `shared-kernel` exists as a dead module that adds confusion without providing code.

**Goals**

1. Remove direct `accounts.application -> accounts.infrastructure` coupling from production code.
2. Make redirect correctness in monolith mode independent from projector timing.
3. Consolidate shortlink web request/response mapping so management and OpenAPI controllers stop duplicating transport logic.
4. Split the largest frontend view into reusable components/composables/services and extract repeated page-shell behavior.
5. Align repository documentation and Maven structure with the actual architecture.

**Non-Goals**

- Do not split the backend into independent deployable services.
- Do not remove analytics async ingestion/flush jobs.
- Do not redesign product behavior or API semantics beyond what is needed for architectural cleanup.

**Acceptance Criteria**

- `accounts` services depend on application ports/domain/contracts/foundation only; architecture tests fail if they depend on `accounts.infrastructure`.
- A shortlink create/update/archive/delete is reflected correctly by redirect reads in monolith mode without requiring a projector drain.
- Shared shortlink HTTP DTO/mapping code is defined once and reused by both controllers.
- `LinksView.vue` is materially smaller, and shared UI/session/navigation concerns are extracted out of views.
- `docs/architecture.md` exists and `README.md` points to a real architecture document.
- The empty `shared-kernel` module is either removed or given a real role; this remediation chooses removal if no production code needs it.

# Split platform API contract out of shared module (Design)

## Background

Today the `server/platform` module contains both:

- Cross-service shared primitives (config, request id, visit info, redis helpers, etc.)
- API error contract and JSON shape (e.g. `ApiResponse`, `ErrorCode`, `ApiErrorResponseWriter`, `GlobalExceptionHandler`)

This makes `platform` gradually turn into a “god module”, and leaks “API response contract” concerns into Edge:

- Edge risk-control decision carries `ErrorCode` (API contract type), binding risk-control strategy to API error codes.
- Edge Filter reuses `ApiErrorResponseWriter`, forcing `application/json` + `ApiResponse` shape even for browser/HTML requests.

## Problems

1. **Incorrect dependency direction**
   - Edge policy/strategy code should not depend on API contract types (`ErrorCode`, `ApiResponse`).

2. **Content negotiation is blocked**
   - Edge wants freedom to return HTML/redirect pages in the future.
   - A filter that hard-writes JSON prevents that evolution.

3. **Contract divergence becomes painful**
   - If Edge and API want to split error codes, the shared enum in `platform` becomes a bottleneck.

## Goals

- `server/platform` no longer contains API error contract / JSON structure code.
- API keeps its JSON contract intact (same fields + semantics), but the contract lives outside `platform`.
- Edge can evolve independently (HTML responses, different error code namespace).
- Compile-time boundaries prevent Edge from pulling in API contract “by accident”.

## Non-goals

- Full “platform module decomposition” (e.g. split platform into platform-web/platform-data/platform-redis).
- Changing the public API JSON schema (for API service) in this refactor.
- Rewriting Edge’s redirect HTML UX; only make the boundary possible.

## Proposed Architecture

### New module: `server/api-contract`

Introduce a dedicated Maven module:

- Artifact: `com.linkforge:linkforge-api-contract`
- Package root: `com.linkforge.contract.api`
- Contents: API response contract + related helpers
  - `ApiResponse`
  - `ErrorCode`
  - `BusinessException`
  - `GlobalExceptionHandler`
  - `ApiErrorResponseWriter`

This module may depend on `server/platform` for request-id utilities (`RequestId`, `RequestIdFilter`), but `platform` must not depend on it.

### API service

- Depends on `platform` (shared primitives) and `api-contract` (response contract).
- Updates imports from `com.linkforge.platform.api.*` → `com.linkforge.contract.api.*`.

### Edge service

- Does **not** depend on `api-contract`.
- Owns its own error codes + error response writing (content-negotiable).
- Risk-control decisions stop carrying API `ErrorCode`; only carry edge-domain info.

## Migration Strategy

1. Create the new module and move the API contract classes into it (with package rename).
2. Update API module imports and wiring.
3. Replace Edge’s use of API contract:
   - Introduce edge-local error code types and writers.
   - Add an edge exception handler for JSON (non-HTML) and keep HTML paths working.
4. Delete `server/platform/src/main/java/com/linkforge/platform/api` package.

## Risks & Mitigations

- **Risk:** Accidental behavior change in Edge JSON errors.
  - **Mitigation:** Keep the JSON field names stable (`code`, `message`, `requestId`) for now; only change ownership.

- **Risk:** Spring component scanning might register handlers in unexpected services.
  - **Mitigation:** Edge does not depend on `api-contract`, so API-only components cannot be picked up by Edge.

## Testing Plan

- Run `mvn test` at `server/` root.
- Ensure Edge redirect experience integration tests still pass.
- Smoke-check Edge endpoints:
  - `/r/{code}` browser request returns HTML or redirect as before.
  - `/r/{code}` non-HTML request returns JSON error payload with requestId.


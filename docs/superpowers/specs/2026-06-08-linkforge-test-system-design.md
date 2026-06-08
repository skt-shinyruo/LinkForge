# LinkForge Test System Design

Status: Draft

## Summary

This spec defines a complete testing system for LinkForge. The repository already has meaningful coverage across backend unit tests, Testcontainers integration tests, frontend Vitest specs, architecture guards, and a k6 redirect benchmark. The missing piece is a coherent testing model: stable commands, clear separation between fast and slow suites, coverage reporting, pressure-test baselines, and focused tests for the highest-risk business paths.

The goal is to make testing repeatable for local development and release verification without forcing slow Docker-dependent checks into every development loop.

## Goals

- keep fast unit and frontend tests easy to run during normal development
- keep Docker/Testcontainers integration tests isolated from the default fast gate
- keep k6 pressure tests isolated from correctness tests
- add coverage reporting for backend and frontend code
- add focused tests for core short-link, redirect, auth, analytics, platform, and governance flows
- document the exact commands for quick, full, integration, and benchmark runs
- define baseline performance scenarios that can be repeated and compared over time

## Non-Goals

- no production load testing
- no CI platform-specific pipeline wiring in this pass
- no attempt to reach arbitrary high coverage percentages by testing trivial getters or framework code
- no full browser end-to-end suite unless a later feature needs it
- no rewrite of existing tests that are already stable and valuable

## Options Considered

### Option A: Minimal Test Cleanup

This would document current commands and add a few missing tests. It is fast but does not satisfy the requested complete testing scope.

### Option B: Coverage-Oriented Hardening

This would add JaCoCo and Vitest coverage plus a few critical-path tests. It improves quality gates but leaves pressure testing and business-risk coverage too ad hoc.

### Option C: Platformized Test System

This is the recommended option. It keeps the existing suite, adds quality gates, fills core business gaps, and turns pressure testing into a repeatable baseline workflow. It matches the requested scope: unit tests, integration tests, pressure tests, and quality gates.

## Proposed Testing Architecture

### 1. Fast Local Gate

The default development gate should stay fast and deterministic:

- backend: `cd server && mvn test`
- frontend: `cd web && npm run test && npm run build`
- backend coverage report: JaCoCo attached to the normal Maven test lifecycle
- frontend coverage report: Vitest coverage script

This gate must not require Docker services, a running application, k6, or external network access.

### 2. Integration Gate

Integration tests remain in `server/integration-tests` and run only through the Maven integration profile:

- command: `cd server && mvn -Pit test`
- dependencies: Docker and Testcontainers
- scope: cross-module behavior, database migrations, Redis behavior, transaction boundaries, security flows, and end-to-end API behavior inside the Spring application

The integration suite should continue to use Testcontainers so it can create isolated MySQL and Redis dependencies instead of relying on a developer's local services.

### 3. Pressure Test Gate

Pressure tests remain outside correctness gates:

- location: `bench/redirect`
- runner: k6, preferably through Docker
- target: local or test environment only
- default target path: `/r/{code}`

The pressure suite should define repeatable scenarios:

- smoke: low rate, short duration, verifies script and target setup
- baseline: stable representative RPS for regression comparison
- ramp: increasing load to observe latency and error behavior
- burst: short high-rate run to expose queueing or cache problems

Pressure tests must record parameters, environment, observed throughput, latency percentiles, error rate, and notes in `bench/redirect/results.md`.

## Core Business Test Coverage

### Short Link

Tests should cover:

- create, update, archive/delete, and restore flows
- short-code case sensitivity
- application-scoped link creation and lookup
- application link quota reservation
- optimistic locking
- tag behavior
- pagination and overflow guards
- CSV import/export time semantics
- redirect cache invalidation after committed changes

Pure rules belong in domain or application unit tests. Persistence, transactions, cache invalidation, and API behavior belong in integration tests.

### Redirect

Tests should cover:

- host plus code routing
- cache hit and authoritative fallback behavior
- negative cache behavior
- expired and archived links
- preview/confirmation href limits
- risk control and fixed-window rate limiting
- trusted proxy and client IP resolution
- redirect response shape and error response shape

The redirect path is also the primary pressure-test target.

### Authentication and Open API

Tests should cover:

- login and registration behavior
- JWT token version invalidation
- CSRF behavior for browser sessions
- API key authentication
- application-scoped API key authorization
- user admin safety checks
- unauthenticated and unauthorized response envelopes

Controller or filter-specific behavior can stay in unit or narrow web tests. Multi-step security behavior belongs in integration tests.

### Analytics

Tests should cover:

- visit event append behavior
- Redis aggregation and flush jobs
- event ingestion batch assembly
- poison message isolation
- dead-letter writes
- application-scoped daily and top-link stats
- visitor fingerprint normalization
- analytics export approval requests

Job behavior should be tested at the unit level when dependencies can be replaced by fakes. Database and Redis projections belong in integration tests.

### Platform and Governance

Tests should cover:

- application creation and provisioning
- domain authorization rules
- legacy short-link migration and binding
- approval submission, state transitions, and execution
- audit-log persistence and querying
- sensitive operation payload mapping

Domain policies should have unit tests. Persistence and workflow tests should remain in integration tests.

### Frontend

Frontend tests should prioritize behavior that can regress without a backend:

- service request and response contract mapping
- auth store state transitions
- router guards and session navigation
- page composables for applications, domains, links, approvals, audit, and stats
- key component interactions for link creation, link lists, stats tables, and API keys

The suite should avoid brittle visual assertions. Components should be tested for rendered state, emitted commands, disabled/loading/error states, and service calls.

## Quality Gates

### Backend Coverage

Add JaCoCo to the backend Maven parent so modules produce coverage reports during tests. Start with reporting and a modest threshold rather than an aggressive failure gate. The first useful threshold is a guardrail against coverage collapse, not a claim of completeness.

The backend gate should exclude generated or framework-heavy code only when exclusions are explicit and documented.

### Frontend Coverage

Add Vitest coverage support and a dedicated script:

- `npm run test:coverage`

Frontend thresholds should focus on services, stores, composables, and core components. The threshold should start low enough to pass after the targeted additions, then be raised as coverage improves.

### Test Command Documentation

Document these commands in the project README or a dedicated testing document:

- quick backend tests
- quick frontend tests
- frontend build
- backend integration tests
- backend coverage report
- frontend coverage report
- complete local verification
- redirect pressure-test smoke run
- redirect pressure-test baseline run

The documentation should state which commands require Docker, a running app, or k6.

## Acceptance Criteria

- `cd server && mvn test` remains the backend fast gate
- `cd web && npm run test && npm run build` remains the frontend fast gate
- `cd server && mvn -Pit test` runs Docker/Testcontainers integration tests separately
- backend coverage reports are generated through JaCoCo
- frontend coverage reports are generated through Vitest
- the repository documents quick, full, integration, coverage, and pressure-test commands
- pressure-test scenarios are repeatable and documented under `bench/redirect`
- core business tests are added for the highest-risk short-link, redirect, auth, analytics, platform, and governance flows
- slow tests and pressure tests are not required for the default fast development loop

## Verification

- run backend unit tests with `cd server && mvn test`
- run backend integration tests with `cd server && mvn -Pit test`
- run frontend tests with `cd web && npm run test`
- run frontend build with `cd web && npm run build`
- run backend coverage report generation
- run frontend coverage report generation
- run k6 redirect smoke test against a local or test deployment
- verify test documentation names all prerequisites and commands
- verify new tests fail for the intended behavior before implementation and pass after implementation

## Notes

This spec intentionally keeps pressure testing out of the default correctness gate. Load tests are operational evidence, not a substitute for deterministic unit and integration tests.

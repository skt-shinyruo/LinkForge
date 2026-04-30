# LinkForge DDD Aggressive Completion Design

**Date:** 2026-04-30

## Problem Statement

LinkForge now has the structural shell needed for Domain-Driven Design: bounded-context Maven modules, `domain/application/infrastructure/interfaces` layers, published `contracts/*` modules, explicit runtime imports, and architecture tests that block several invalid dependencies. The remaining gap is consistency inside those context boundaries.

The current backend is closer to a DDD-oriented modular monolith than a completed DDD implementation:

- `shortlink` has the richest tactical model, but some policies and compatibility facades still need final hardening.
- `accounts` is still heavily procedural, with registration, login, token-version, status, and role decisions concentrated in application services.
- `platform` has domain package types, but several of them are data records rather than behavior-owning domain objects.
- `governance` has an approval aggregate shape, but application services still own parts of the approval orchestration and execution policy.
- `analytics` and `redirect` should not be forced into heavyweight aggregate models, but their published language, policy objects, and application boundaries need to be explicit.

This design is the aggressive completion target for tactical DDD across the backend while preserving the current single Spring Boot runtime.

## Relationship To Existing Specs

This design refines and supersedes the tactical scope of `2026-04-28-ddd-refactor-design.md`. It keeps the same architecture direction, but raises the bar from "shortlink as the DDD pilot" to "each bounded context has an intentional model style that matches its domain complexity."

It also inherits the stricter cross-context contract rule from `2026-04-27-cross-context-contract-ports-design.md`: bounded contexts may call each other only through `com.linkforge.contract..` published language, not through another context's `domain`, `application`, `infrastructure`, or `interfaces` packages.

## Constraints

- Keep one deployed backend runtime. Do not split LinkForge into microservices.
- Preserve existing HTTP API behavior unless a behavior change is explicitly required to restore a domain invariant.
- Preserve the Maven bounded-context layout and explicit runtime module imports.
- Keep domain modules framework-free: no Spring, Servlet/Jakarta runtime APIs, MyBatis, Redis, persistence entities, or runtime security context.
- Do not introduce global event sourcing, broad CQRS infrastructure, or a generic DDD framework.
- Keep tactical DDD types purposeful. Add value objects and domain services only when they own validation, normalization, comparison, lifecycle, or policy behavior.

## Goals

1. Give every backend bounded context an explicit target model: aggregate roots, value objects, domain services, ports, and published language.
2. Move business invariants out of controllers, guards, persistence adapters, and broad application services.
3. Convert procedural application services into explicit use-case coordinators.
4. Make cross-context collaboration go through published contracts with business names, not persistence or implementation names.
5. Add domain and architecture tests that prevent regression to transaction-script style.
6. Keep user-facing behavior stable while changing internal Java APIs aggressively where useful.

## Non-Goals

- Do not make every data structure an aggregate.
- Do not require domain events for contexts whose behavior is naturally query/projection oriented.
- Do not move password hashing, JWT signing, Redis access, SQL mapping, request principal extraction, or transaction mechanics into domain code.
- Do not remove compatibility application facades until controllers and tests can migrate safely.
- Do not redesign the Vue frontend as part of this work.

## Target DDD Rules

### Domain Layer

The domain layer owns:

- aggregate behavior and lifecycle transitions
- value-object validation and normalization
- domain services for policies that require multiple domain concepts
- internal domain events where state changes are meaningful business facts
- domain exceptions that describe invariant violations in domain language

The domain layer must not:

- read runtime authentication or request context
- depend on Spring, web, servlet, persistence, Redis, MyBatis, or foreign bounded-context internals
- publish integration events directly
- know about HTTP response shapes, DTOs, SQL rows, cache keys, or JSON payload versions

### Application Layer

The application layer owns:

- one use case per command/query handler where practical
- transactions
- loading and saving aggregates through ports
- authorization input handling expressed as explicit method parameters
- calls to published cross-context contracts
- translation from domain exceptions to API-level business exceptions
- translation from internal domain events to integration events

The application layer should not become a hidden domain model. If it contains lifecycle rules, status transitions, quota decisions, or approval decisions that can be expressed without infrastructure, those rules belong in domain objects or domain services.

### Infrastructure Layer

The infrastructure layer owns:

- MyBatis mappers and persistence entities
- Redis adapters
- scheduler jobs
- password hashing and token-signing implementations
- integration-event persistence and serialization
- adapter implementations for application ports and published contracts

Infrastructure maps between persistence/runtime shapes and domain/application shapes. It does not define business vocabulary.

### Interfaces Layer

The interfaces layer owns:

- HTTP controllers
- request validation at transport boundaries
- principal extraction and mapping to explicit actors
- request/response DTO mapping
- endpoint-specific error shaping

Controllers should call one use case or one compatibility facade and should not read repositories or infrastructure adapters.

### Contracts

`contracts/*` modules own published language shared across bounded contexts. Contract records and ports should:

- use stable business names
- stay framework-light
- avoid persistence entity names
- avoid leaking provider application-service APIs
- be narrower than internal facades

## Context-By-Context Target Model

### Accounts

Responsibilities:

- tenants
- users
- roles
- API keys
- authentication status
- token invalidation/versioning

Target aggregates and value objects:

- `Tenant`: owns tenant identity, display name, active/disabled status, and tenant lifecycle rules.
- `AccountUser`: owns email identity, user status, token version, and user lifecycle decisions.
- `ApiKey`: owns key identity, display name, scope, status, secret fingerprint metadata, and revocation/rotation lifecycle.
- `RoleAssignment`: represents a user's role grants inside a tenant.
- `EmailAddress`, `TenantName`, `RoleCode`, `ApiKeyName`, `TokenVersion`: value objects for validation and normalization.

Domain services:

- `AccountStatusPolicy`: decides whether tenant and user state allow authentication.
- `RolePolicy`: validates role grants and default role behavior.
- `ApiKeyScopePolicy`: validates which API key scopes are allowed for a tenant/application boundary.

Application use cases:

- `RegisterTenantAdmin`
- `LoginUser`
- `LogoutUser`
- `DisableUser`
- `EnableUser`
- `CreateApiKey`
- `RotateApiKey`
- `RevokeApiKey`
- `AssignUserRole`
- `RemoveUserRole`

Important boundary rule:

Password hashing and token issuing stay in application ports. The domain can decide that a user may authenticate, but it must not verify raw passwords or sign tokens.

### Platform

Responsibilities:

- tenant applications
- domains
- application quotas
- application policies
- domain authorization for application-owned link creation

Target aggregates and value objects:

- `Application`: owns application key, display name, status, tenant ownership, and lifecycle.
- `Domain`: owns hostname, scope, status, application binding, verification state, and lifecycle.
- `ApplicationQuota`: owns monthly link limits and quota configuration invariants.
- `ApplicationPolicy`: owns destination-change, target-trust, and governance policy decisions.
- `ApplicationKey`, `DomainHostname`, `MonthlyLinkLimit`, `ApplicationStatus`, `DomainVerificationState`: value objects/enums where validation or comparison is meaningful.

Domain services:

- `DomainAuthorizationPolicy`: verifies whether an application may use a domain.
- `QuotaPolicy`: determines whether quota configuration is valid and how quota windows are interpreted.
- `ApplicationPolicyDecisionService`: determines whether a link mutation requires governance approval.

Application use cases:

- `ProvisionApplication`
- `RenameApplication`
- `DisableApplication`
- `RegisterDomain`
- `VerifyDomain`
- `BindDomainToApplication`
- `UpdateApplicationQuota`
- `UpdateApplicationPolicy`
- `ResolveApplicationScope`
- `ResolveDomainHostname`

Important boundary rule:

Platform publishes authorization and lookup views through `contract-platform`. Other contexts must not import platform repositories or platform application services.

### Shortlink

Responsibilities:

- durable shortlink state
- short code allocation rules
- link lifecycle
- destination and query-forward rules
- tags and revisions
- mutation facts for downstream projections

Target aggregates and value objects:

- `ShortLink`: aggregate root for durable link state, lifecycle, destination, preview, query-forward, revision, and mutation facts.
- `Tag`: owns tag name normalization and tenant-local uniqueness semantics.
- `ShortLinkRevision`: captures meaningful change history.
- `ShortCode`, `HttpUrl`, `QueryForwardMode`, `QueryForwardAllowlist`, `QueryParamPattern`, `RedirectStatusCode`: value objects/enums with validation.

Domain services:

- `ShortCodeAllocationPolicy`: handles generated/custom code collision semantics without leaking persistence.
- `DestinationChangePolicy`: determines whether a destination change is direct or approval-gated.
- `LinkTagPolicy`: validates tag limits and assignment rules.

Domain events:

- `ShortLinkCreated`
- `ShortLinkUpdated`
- `ShortLinkArchived`
- `ShortLinkRestored`
- `ShortLinkDeleted`
- `ShortLinkDestinationChangeRequested`
- `ShortLinkTagsChanged`

Application use cases:

- `CreateShortLink`
- `UpdateShortLink`
- `ArchiveShortLink`
- `RestoreShortLink`
- `DeleteShortLink`
- `RequestDestinationChange`
- `ExecuteApprovedDestinationChange`
- `SetLinkTags`
- `ImportShortLinksCsv`
- `ExportShortLinksCsv`
- `SearchShortLinks`
- `GetShortLinkDetail`

Important boundary rule:

Domain events are internal facts. Application code translates them into stable integration events under `contract-shortlink/event` after successful persistence.

### Redirect

Responsibilities:

- resolve `host + code`
- apply redirect availability policy
- handle positive and negative Redis cache behavior
- produce redirect, preview, unavailable, and not-found outcomes
- append lightweight visit records

Target model style:

Redirect is a traffic-plane context. It should stay lightweight and policy-oriented rather than inventing durable aggregates for link truth it does not own.

Target domain concepts:

- `RedirectLookupKey`: host/code lookup identity.
- `RedirectDecision`: resolved outcome such as redirect, preview, unavailable, not found, or blocked.
- `RedirectAvailabilityPolicy`: evaluates enabled/expired/archived/unavailable states from published link metadata.
- `RedirectRiskPolicy`: combines CIDR, user-agent, rate-limit, and bot decisions into a redirect risk outcome.
- `CidrBlock`, `CidrBlocks`, `IpStrings`, `UserAgentBotDetector`: keep pure parsing and detection logic in domain.

Application use cases:

- `ResolveRedirect`
- `RecordRedirectVisit`
- `WarmRedirectCache`
- `EvictRedirectCache`

Important boundary rule:

Redirect does not own link truth. Cache misses use shortlink published read contracts. Redirect must not reintroduce its own durable projection as an authoritative model.

### Analytics

Responsibilities:

- visit ingestion
- event normalization
- aggregate read models
- statistics queries
- export requests
- link-summary enrichment through published contracts

Target model style:

Analytics is mostly an ingestion and read-model context. It should use explicit domain language for event facts, dimensions, aggregation windows, and export policy, but it should not force every reporting row into an aggregate.

Target domain concepts:

- `VisitFact`: immutable normalized visit fact.
- `VisitDimension`: normalized browser/device/referrer/location/tracking dimensions.
- `AggregationWindow`: time range and granularity rules.
- `AnalyticsExportRequest`: export request lifecycle and approval state.
- `TopLinkStatistic`, `DailyStatistic`: read-model records with analytics vocabulary, not persistence row names.

Domain/application services:

- `VisitNormalizationPolicy`: normalizes inbound visit context.
- `AggregationPolicy`: validates allowed windows and dimensions.
- `AnalyticsExportPolicy`: determines when export needs governance approval.
- `AnalyticsLinkSummaryEnricher`: application component that owns shortlink contract enrichment and fallback behavior.

Application use cases:

- `RecordVisit`
- `IngestVisitBatch`
- `BuildDailyAggregates`
- `QueryDashboardStats`
- `QueryTopLinks`
- `RequestAnalyticsExport`
- `ExecuteApprovedAnalyticsExport`

Important boundary rule:

Analytics read models remain private. Other contexts receive only published usage or visit-recording contracts where needed.

### Governance

Responsibilities:

- approval request lifecycle
- approval decisions
- sensitive operation records
- execution coordination
- audit logs

Target aggregates and value objects:

- `ApprovalRequest`: aggregate root for submit, approve, reject, expire, execute, and failure transitions.
- `AuditLog`: immutable audit fact for sensitive operations and approval decisions.
- `SensitiveOperationType`, `ApprovalStatus`, `ApprovalDecisionReason`, `ApprovalActor`, `ApprovalSnapshot`: domain vocabulary for approval state.

Domain services:

- `ApprovalMatrixPolicy`: determines whether an actor may approve a request.
- `SensitiveOperationPolicy`: determines whether an operation type requires governance.
- `AuditPolicy`: determines required audit facts for each operation.

Application use cases:

- `SubmitApprovalRequest`
- `ApproveRequest`
- `RejectRequest`
- `ExecuteApprovedRequest`
- `ListApprovalRequests`
- `ListAuditLogs`

Important boundary rule:

Governance may orchestrate execution through `contract-governance` ports, but the approval aggregate owns approval state transitions and self-approval prevention.

## Cross-Context Collaboration

Cross-context dependencies must follow these rules:

- `redirect` reads shortlink metadata only through `contract-shortlink`.
- `analytics` enriches link summaries only through `contract-shortlink`.
- `shortlink` checks application/domain authorization only through `contract-platform`.
- `shortlink` and `analytics` request approvals only through `contract-governance`.
- `platform` may read analytics usage only through `contract-analytics`.
- `app/security` may use accounts-published authentication/account-status ports, but must not import accounts infrastructure or accounts domain internals.

Provider contexts may implement their own published contracts inside application or infrastructure modules, but consumers cannot depend on provider inner layers.

## Migration Phases

### Phase 1: Strengthen Architecture Guardrails

Add or tighten tests before large refactors:

- verify every bounded context has explicit `domain/application/infrastructure/interfaces` module ownership where intended
- block cross-context imports to all inner layers
- block application dependencies on servlet/web/runtime context helpers
- block domain dependencies on runtime frameworks and persistence APIs
- block controllers from calling repositories, mappers, or infrastructure adapters directly
- block contracts from depending on Spring, Jakarta runtime APIs, persistence APIs, or provider internals

Acceptance criteria:

- architecture tests fail for representative forbidden imports
- existing allowed same-context infrastructure-to-application adapter dependencies remain possible

### Phase 2: Accounts Tactical Model

Extract account lifecycle and authentication-status rules from procedural services:

- introduce account domain aggregates and value objects
- move status and role decisions into domain policies
- keep password hashing and token issuing as application ports
- split `AuthService`, `ApiKeyService`, `AccountStatusService`, and `UserAdminService` into explicit use cases where behavior is complex

Acceptance criteria:

- registration, login, logout, API-key, and account-status tests describe domain rules
- application services orchestrate aggregates instead of encoding status/lifecycle decisions inline

### Phase 3: Platform Domain Completion

Turn platform records into behavior-owning domain objects:

- model application lifecycle
- model domain ownership, scope, status, and verification
- model quota and policy invariants
- keep `DomainAuthorizationPolicy` and extend policy services where rules involve multiple concepts

Acceptance criteria:

- application/domain/quota/policy invariants are covered by domain tests
- platform application services load domain objects and call explicit behavior methods
- published platform contract views do not expose persistence entities

### Phase 4: Shortlink Completion

Finish the core-domain model:

- close remaining lifecycle, destination-change, revision, and tag rules inside aggregates/value objects/domain services
- ensure create/update/archive/restore/delete mutation facts originate from domain behavior
- keep compatibility facades only as controller-facing wrappers
- translate domain events to integration events at the application boundary

Acceptance criteria:

- shortlink mutation use cases read as orchestration
- domain tests explain lifecycle and mutation rules without infrastructure
- integration-event tests verify stable contract payload mapping

### Phase 5: Governance Completion

Complete approval and audit behavior:

- move all approval state transitions into `ApprovalRequest`
- add explicit reject, expire, execute, and failure semantics if supported by current behavior
- centralize self-approval and approval-matrix decisions in domain policy
- keep executor discovery and invocation in application

Acceptance criteria:

- approval transition tests cover valid and invalid states
- application services coordinate persistence, contracts, and execution without owning the state machine

### Phase 6: Analytics And Redirect Boundary Hardening

Keep these contexts intentionally lightweight:

- make redirect policies explicit and pure
- keep redirect cache behavior in infrastructure/application
- make analytics visit normalization, aggregation-window, and export policies explicit
- keep analytics read models private

Acceptance criteria:

- redirect tests cover decisions without requiring Redis or HTTP
- analytics tests cover normalization, aggregation-window, export, and enrichment policies
- neither context imports another context's inner layers

### Phase 7: Documentation And Final Verification

Update architecture documentation to match the completed model:

- context ownership
- aggregate ownership
- published language
- allowed dependency directions
- domain-event versus integration-event rules

Acceptance criteria:

- `docs/architecture.md` is the concise source of truth
- this spec remains the detailed design reference
- focused module tests and full backend tests pass

## Testing Strategy

Use tests as migration drivers:

- domain tests for aggregate lifecycle, value-object validation, and domain policies
- application tests for orchestration, transaction boundary behavior, contract calls, and exception translation
- infrastructure tests for mapper/domain conversion and adapter behavior
- controller tests for HTTP compatibility
- architecture tests for dependency direction and framework isolation
- integration tests only where behavior crosses module/runtime boundaries

Each phase should start with failing tests that describe the intended domain behavior before moving production code.

## Risks And Mitigations

### Risk: Over-Modeling Simple Contexts

Mitigation:

- treat `analytics` and `redirect` as policy/read-model contexts, not aggregate-heavy contexts
- add domain objects only for rules that currently need tests or clearer ownership

### Risk: Large Internal API Churn

Mitigation:

- preserve HTTP APIs
- keep compatibility facades temporarily
- migrate one use case at a time
- run focused tests before broad verification

### Risk: Domain Events Leak Infrastructure Concerns

Mitigation:

- keep internal domain events framework-free
- translate domain events to integration events in application/infrastructure
- keep payload versions and serialization in contract/infrastructure code

### Risk: Published Contracts Become God Facades

Mitigation:

- publish narrow consumer-driven ports
- name records by business concepts
- keep provider application services private
- enforce cross-context dependency rules with ArchUnit

### Risk: Security Logic Moves To The Wrong Layer

Mitigation:

- domain owns account eligibility and lifecycle rules
- application owns password hasher and token issuer ports
- interfaces/app security owns request principal extraction and Spring Security wiring

## Success Criteria

The aggressive DDD completion is done when:

- every bounded context has documented aggregate or policy ownership appropriate to its domain complexity
- accounts, platform, shortlink, and governance business invariants are expressed in domain code and domain tests
- analytics and redirect have explicit lightweight domain policies without pretending to own foreign state
- broad procedural application services have been split or reduced to compatibility facades over explicit use cases
- all cross-context collaboration goes through `contracts/*`
- architecture tests prevent domain/framework coupling and cross-context inner-layer imports
- existing user-facing API behavior remains stable
- full backend verification passes after each major phase

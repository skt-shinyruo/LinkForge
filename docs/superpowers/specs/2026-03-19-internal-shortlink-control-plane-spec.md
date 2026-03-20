# LinkForge Internal Shortlink Control Plane Spec

**Date:** 2026-03-19

## Problem Statement

LinkForge currently behaves like a capable shortlink MVP for a single management surface:

- multi-tenant auth exists, but the tenant is the only meaningful isolation boundary.
- short links are managed at tenant scope instead of at the scope of an internal consuming system.
- redirect and analytics flows do not model domains as first-class governance resources.
- OpenAPI support exists, but it is too narrow to serve as the stable control-plane API for multiple internal systems.
- the web console supports links, tags, and stats, but it does not provide self-service onboarding, application governance, approvals, or audit workflows.

That shape is sufficient for a small internal tool, but it is not sufficient for the desired target product: a production-grade internal shortlink control plane operated by a platform team and consumed by multiple internal systems, with tenant administrators able to self-serve within governance constraints.

The target operating model is:

- `tenant = business line`
- `application = consuming internal system`
- platform-generated links are mainly clicked by public internet users
- target URLs are mainly company-owned public URLs, but approved third-party URLs and internal/test/temporary URLs also exist
- the platform must be production-ready at first release, not a minimal pilot
- self-service and governance must ship together

## Goals

1. Turn LinkForge into an internal control plane where `application` becomes the primary unit for API issuance, quota, audit, policy, statistics, and operational ownership.
2. Keep `tenant` as the business-line boundary while allowing tenant administrators to self-serve most configuration without platform-team intervention.
3. Make `domain` a first-class governed resource, supporting both tenant-shared domains and application-owned dedicated domains.
4. Upgrade redirect routing from a code-only model to a `host + code` model so branded/shared/dedicated domains can coexist safely.
5. Introduce approval and audit workflows for sensitive operations instead of relying on controller-level checks and tribal process.
6. Ship a first-release web console that supports actual onboarding and governance, not just link CRUD.
7. Preserve LinkForge’s modular-monolith deployment model while reshaping module responsibilities around control-plane concerns.

## Non-Goals

- Do not turn LinkForge into a marketing-heavy public SaaS product in this phase.
- Do not prioritize QR codes, A/B tests, mobile deep links, or social-preview customization.
- Do not introduce a fine-grained RBAC matrix beyond `platform_admin` and `tenant_admin` in the first release.
- Do not split the system into independently deployable microservices.
- Do not optimize for arbitrary untrusted public destinations as the main use case; governance should focus on controlled internal usage patterns first.

## Product Operating Model

### Tenancy and ownership

- A `tenant` represents a business line.
- An `application` represents one consuming internal system inside a tenant.
- A tenant may own multiple applications.
- A tenant administrator manages the full set of applications in that tenant.
- A platform administrator can view and manage all tenants, applications, domains, approvals, audits, and emergency operations globally.

### Permission model

- Only two management roles exist in the first release:
  - `platform_admin`
  - `tenant_admin`
- `tenant_admin` manages the tenant end-to-end, including applications, domains, API keys, policies, link operations, and approval requests.
- Sensitive operations are controlled by approval workflow and audit logging rather than by introducing extra per-application roles.

### Domain model

- The platform supports both:
  - tenant-shared domains
  - application-dedicated domains
- Applications can use:
  - tenant-owned shared domains they are explicitly authorized to use
  - their own dedicated domains
- Short codes are unique within a domain.
- Redirect resolution uses `host + code`.

### Traffic exposure

- The platform is for internal system usage, but end-user clicks mainly come from the public internet.
- Governance must therefore treat link creation and target changes as externally visible production operations even though the operators are internal users.

## Governance Model

### Self-service vs approval

The platform should support both platform-team management and tenant self-service.

- Ordinary operations should be self-service.
- Sensitive operations must require approval and leave a full audit trail.

Approval authority should be explicit instead of implied:

- `tenant_admin` may approve ordinary tenant-scoped requests that do not expand external blast radius beyond the tenant's already-governed assets.
- `platform_admin` must approve requests that affect platform-wide risk posture, public-domain exposure, or sensitive data extraction.
- The requester and the approver must not be the same person.
- A request must retain requester identity, approver identity, timestamps, decision reason, and the before/after diff of the intended change.

Sensitive operations include:

1. adding or binding an external domain
2. increasing application quota
3. enabling webhook/callback integration
4. creating a high-privilege API key
5. modifying default redirect policy or risk-control allowlists
6. exporting detailed visit data or audit data
7. changing the destination URL of an already-public production link

Default approval matrix:

- `platform_admin` approval required:
  - adding or binding an external domain
  - enabling webhook/callback integration
  - creating a high-privilege API key
  - modifying default redirect policy or risk-control allowlists
  - exporting detailed visit data or audit data
  - public-production exception activation for internal/test/temporary targets
- `tenant_admin` approval allowed within the tenant:
  - increasing application quota within a platform-defined tenant ceiling
  - changing the destination URL of an already-public production link when the change stays inside already-approved production domains for that tenant
- escalation to `platform_admin` required even for tenant-originated requests when:
  - the request crosses domain trust class
  - the request introduces a new external/public exposure pattern
  - the request exceeds tenant-level quota ceilings

### Target URL governance

The platform must support controlled use of:

- company-owned production public domains
- approved third-party domains
- internal/test/temporary URLs

Default governance rules:

- production links should default to approved production allowlist domains
- third-party domains require approval
- internal/test/temporary URLs may exist, but public-production activation must go through an approval-backed exception workflow

### Editable-link policy

Links should use a graded mutability model:

- draft / pre-release / internal-validation links may be edited directly, with audit trail
- already-public production links may be edited only through approval, with before/after diff retained
- platform policy may still mark certain high-risk links as immutable except for disable-and-recreate

## Lifecycle and Approval State Model

Link lifecycle state and approval workflow state should be modeled separately.

### Link lifecycle state

Recommended link states:

- `DRAFT`: editable, not publicly routable
- `PRE_RELEASE`: routable only for controlled validation flows
- `ACTIVE`: publicly routable
- `DISABLED`: not routable

### Change request workflow state

Recommended request states:

- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`
- `CANCELLED`
- `EXECUTED`
- `EXPIRED`

Rules:

- Sensitive creation or mutation should first create a request record.
- A link can remain in `DRAFT` or `PRE_RELEASE` while its promotion request is `PENDING_APPROVAL`.
- Approval state must not be overloaded onto the link row itself.
- Redirect behavior is determined by the link lifecycle state only:
  - `DRAFT` and `DISABLED` are never publicly routable
  - `PRE_RELEASE` is routable only through controlled validation entry points
  - `ACTIVE` is publicly routable
- Once an approved request is executed, the request becomes `EXECUTED` and the link revision/history captures the applied change.

## Recommended Domain Architecture

LinkForge should evolve from a shortlink-centric MVP into five explicit control-plane subdomains.

### 1. Platform / Control Plane

Responsibilities:

- tenant lifecycle
- application lifecycle
- domain registration/authorization
- quota configuration
- default policy configuration
- platform and tenant administration flows

Core entities:

- `Tenant`
- `Application`
- `Domain`
- `ApplicationQuota`
- `ApplicationPolicy`

### 2. Link Management

Responsibilities:

- link creation and lifecycle management
- application-scoped ownership
- link revision history
- controlled state transitions

Core entities:

- `ShortLink`
- `ShortLinkRevision`
- `ShortLinkState`
- `LinkChangeRequest`

`ShortLink` should evolve to carry at least:

- `tenantId`
- `applicationId`
- `domainId`
- `code`
- destination/policy fields

### 3. Redirect / Traffic Plane

Responsibilities:

- resolve `host + code`
- enforce domain authorization and link state
- apply availability and environment rules
- produce correct final redirect destination
- keep cache behavior correct under configuration changes

The redirect cache key should evolve from `code` to `host:code`.

### 4. Governance / Approval / Audit

Responsibilities:

- sensitive-operation request workflow
- approval decisions
- immutable audit records
- export task authorization and retention

Core entities:

- `ApprovalRequest`
- `ApprovalDecision`
- `AuditLog`
- `SensitiveExportTask`

### 5. Analytics / Observability

Responsibilities:

- aggregate and expose operational visibility across:
  - tenant
  - application
  - domain
  - link
  - redirect outcomes
  - approval and audit activity

The analytics model should expand from “link stats” to “platform governance stats”.

## Core Data and Routing Rules

### Identity and uniqueness

- `tenant` is the business-line boundary.
- `application` is unique within a tenant.
- `domain` is globally unique.
- `code` is unique within a domain.
- the stable externally visible link identity is `domain + code`.

### Routing rules

Redirect resolution should follow:

1. resolve incoming host to a known active domain
2. resolve code inside that domain
3. verify link state and governance constraints
4. apply redirect strategy and target resolution
5. record traffic and outcome at tenant/application/domain/link scopes

### Domain authorization

- tenant domains can be shared across authorized applications in that tenant
- application domains are exclusive to the owning application unless future policy says otherwise
- every link must be attached to one concrete domain, not only a global base URL

## Migration and Compatibility Strategy

The platform cannot adopt the new model only as a greenfield design; it must define how current tenant-scoped links survive the transition.

### Existing link ownership migration

Recommended transition policy:

- every existing tenant gets a system-created `default application`
- every existing tenant gets a system-created `legacy default domain` representing the current base-url-backed routing surface
- all existing tenant-scoped links migrate into that tenant's default application and legacy default domain unless explicitly reassigned later

This keeps migration deterministic and prevents P0 from depending on a manual reclassification project before release.

### Existing routing compatibility

- the platform should support a compatibility window where the legacy base URL continues to resolve migrated links
- migrated legacy links should be addressable through the legacy default domain mapping
- new domain-aware routing should become the source of truth, but rollout should allow dual-read/compatibility handling until the legacy host surface is fully absorbed

### Reassignment after migration

- tenant administrators may later move legacy links from the default application into a concrete application through an audited reassignment workflow
- reassignment should preserve historical analytics, audit lineage, and stable external routing identity where possible

## P0 Release Definition

P0 is the first production release, not a minimal pilot.

### P0 capabilities

#### Control-plane model

- tenant-aware application management
- domain ownership and authorization model
- application-scoped API keys
- application quotas
- application default policy

#### Link lifecycle

- draft / pre-release / active / disabled states
- application-owned link CRUD
- approved mutation workflow for public active links
- controlled exception flow for third-party and internal/test/temporary targets
- explicit request-state model for approval-backed changes
- emergency disable path

#### Domain governance

- tenant-shared domains
- application-dedicated domains
- domain verification/state management
- `host + code` redirect resolution

#### Unified API surface

- application-scoped create/query/update/disable APIs
- application-scoped statistics APIs
- explicit API versioning and error semantics
- idempotency and rate limiting for write APIs

#### Governance

- approval request lifecycle
- platform-admin approval center
- audit logs for all sensitive mutations
- controlled export workflow for sensitive detail data

#### Observability

- tenant, application, domain, and link statistics
- redirect success/failure visibility
- operational dashboards for platform administrators and tenant administrators

#### Self-service console

- tenant overview
- applications list/detail
- domain management
- API key management
- policy and quota management
- approval center
- audit-log search
- statistics dashboard
- shortlink management

### P0 explicit exclusions

- QR code generation
- A/B testing and traffic splitting
- mobile deep links
- rich marketing campaign tooling
- complex multi-layer RBAC

## P1 Release Direction

P1 should make the platform scale operationally across many internal systems.

Expected themes:

- batch create/disable/migrate workflows
- policy inheritance and override mechanics
- quota and domain health alerting
- approval SLA and escalation features
- export task hardening
- stronger onboarding templates and SDKs

## P2 Release Direction

P2 should mature the platform into a broader redirect-governance foundation.

Expected themes:

- higher-order traffic governance and migration workflows
- stronger compliance/reporting surfaces
- platform catalog and configuration hygiene tooling
- optional enterprise enhancements such as SSO or more granular roles if proven necessary

## Required UI Surfaces for P0

P0 should ship the following page groups because self-service is in scope from day one:

- login/auth bootstrap
- tenant overview
- application list/detail
- domain management
- API key management
- policy and quota management
- shortlink management
- approval center
- audit search
- statistics dashboard
- platform-global admin views

This is a substantial expansion over the current console, which only exposes link, tag, and stats views.

## Required API Groups for P0

The API should be restructured around platform resources instead of a single tenant-wide link namespace.

Recommended API groups:

1. platform-admin APIs
2. tenant-admin APIs
3. application link APIs
4. approval APIs
5. audit/export APIs
6. statistics APIs

Representative application-scoped resources should look like:

- `/api/v1/applications/{appId}/links`
- `/api/v1/applications/{appId}/stats`
- `/api/v1/applications/{appId}/webhooks`
- `/api/v1/applications/{appId}/exports`

## Testing and Rollout Requirements

### Testing

P0 should require:

- unit tests for domain rules around tenant/application/domain/quota/policy/approval behavior
- integration tests for domain authorization, `host + code` routing, approval-gated state changes, audit persistence, and cache invalidation
- end-to-end tests covering tenant-admin onboarding and application self-service
- regression coverage for current link CRUD, redirect, and analytics behavior
- security coverage for cross-tenant and cross-application access control
- migration tests for existing shortlink data into the new application/domain-aware model

### Rollout order

Recommended rollout sequence:

1. land control-plane entities and APIs first
2. migrate existing tenant-scoped links into default application/domain ownership
3. upgrade redirect/routing to domain-aware behavior with compatibility handling
4. ship self-service console surfaces
5. onboard production domains and real consuming systems with monitoring and emergency controls enabled

## Acceptance Criteria

- A tenant administrator can create and manage applications inside the tenant without platform-team involvement for ordinary operations.
- Every application has independent API credentials, quotas, statistics, audit trails, and default policy.
- Tenant-shared domains and application-dedicated domains both work in production.
- Redirects resolve by `host + code`, not by code alone.
- Sensitive operations cannot take effect without approval and auditable records.
- Public production links support controlled destination changes through approval-backed revisions.
- Existing tenant-scoped links can be migrated into the new model without losing routability or operational ownership.
- The web console is sufficient for business-line self-service onboarding, governance, and observation.
- Platform administrators retain global visibility, approval authority, and emergency control.

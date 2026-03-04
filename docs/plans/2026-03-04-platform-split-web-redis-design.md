# Split platform into core + web + redis (Design)

## Background

Today `server/platform` (`com.linkforge:linkforge-platform-shared`) mixes:

- Cross-service shared primitives / SSOT (config, ids, utils, tx helpers)
- Web runtime concerns (CORS, servlet filter, request id, visit info)
- Redis runtime concerns (short-link cache + JSON serialization)

This creates several long-term problems:

1) **Dependency bloat & blurred boundaries**
   - Any module that depends on `platform` is forced to pull in Web/Redis starters even if it only needs core utils.

2) **Implicit runtime module**
   - `platform` contains Spring runtime beans (`@Component/@Service/@Configuration`), so adding a bean can change behavior of both API and Edge.

3) **Evolvability risk**
   - `platform` can easily become a “god module”, making future refactors (or service splitting) increasingly expensive.

## Goals

- Make `linkforge-platform-shared` a real **core shared library**:
  - No `spring-boot-starter-web`
  - No `spring-boot-starter-data-redis`
  - No runtime Spring beans / auto-wiring side effects
- Introduce explicit, opt-in runtime modules:
  - `linkforge-platform-web` for servlet/web concerns
  - `linkforge-platform-redis` for Redis caching concerns
- Keep behavior unchanged for API + Edge (refactor-only).
- Add CI guardrails to prevent `platform-shared` from growing back.

## Non-goals

- Full decomposition into many small modules (e.g. `platform-json`, `platform-logging`).
- Changing API/Edge endpoints, JSON schema, or runtime behavior.
- Reworking component scan strategy (`scanBasePackages = "com.linkforge"`) in this change.

## Proposed Architecture

### Module layout (server/)

Keep existing module as core:

- `server/platform`
  - Artifact: `com.linkforge:linkforge-platform-shared`
  - Purpose: shared primitives (SSOT), no runtime beans

Add two new modules:

- `server/platform-web`
  - Artifact: `com.linkforge:linkforge-platform-web`
  - Depends on: `linkforge-platform-shared` + `spring-boot-starter-web`
  - Purpose: web runtime helpers

- `server/platform-redis`
  - Artifact: `com.linkforge:linkforge-platform-redis`
  - Depends on: `linkforge-platform-shared` + `spring-boot-starter-data-redis` + `spring-boot-starter-json`
  - Purpose: Redis cache helpers used by API/Edge

### Code moves

Move web package out of core:

- From: `server/platform/src/main/java/com/linkforge/platform/web/*`
- To: `server/platform-web/src/main/java/com/linkforge/platform/web/*`

Move redis cache package out of core:

- From: `server/platform/src/main/java/com/linkforge/redirect/service/*`
- To: `server/platform-redis/src/main/java/com/linkforge/redirect/service/*`

Keep these in `platform-shared` (examples):

- `com.linkforge.platform.config.*` (`AppProperties`, `StartupValidation`)
- `com.linkforge.platform.id.SnowflakeIdGenerator`
- `com.linkforge.platform.util.Base62`
- `com.linkforge.platform.tx.AfterCommit`
- `com.linkforge.analytics.service.AnalyticsKeys` (Redis key contract only)

### Remove runtime beans from core

Remove these from `platform-shared`:

- `server/platform/src/main/java/com/linkforge/platform/config/AppConfig.java` (`@EnableConfigurationProperties`)
- `server/platform/src/main/java/com/linkforge/platform/id/IdConfig.java` (`@Configuration` + `@Bean`)

Re-introduce wiring explicitly in services:

- API and Edge main applications add `@EnableConfigurationProperties(AppProperties.class)`
- API creates `SnowflakeIdGenerator` bean in API module (Edge does not need it)

### Dependency updates

- `server/api-contract` should depend on `platform-web` (it uses `RequestId` / `RequestIdFilter`).
- `server/api` should depend on `platform-shared` + `platform-web` + `platform-redis` + `api-contract`.
- `server/edge` should depend on `platform-shared` + `platform-web` + `platform-redis`.

### Guardrails (CI)

Add tests in `platform-shared` that fail if:

- Any class is annotated with Spring stereotype annotations (`@Component`, `@Service`, `@Configuration`, etc.)
- Any class depends on servlet/web/redis packages:
  - `jakarta.servlet..`
  - `org.springframework.web..`
  - `org.springframework.data.redis..`

This prevents “platform-shared grows back into runtime module” regressions.

## Build / Docker impact

`server/Dockerfile.api` and `server/Dockerfile.edge` must `COPY` the new modules and `api-contract` into the Maven build stage, otherwise Docker builds will fail.

## Risks & mitigations

- **Risk: split-package** (`com.linkforge.redirect.service`) across modules.
  - Mitigation: move the whole package to `platform-redis` (including `LinkMeta`).

- **Risk: AppProperties binding breaks** after removing `AppConfig`.
  - Mitigation: add `@EnableConfigurationProperties(AppProperties.class)` to API/Edge application classes + tests.

- **Risk: transitive deps change compile classpath unexpectedly.**
  - Mitigation: run full `cd server && mvn test` and ensure integration tests still pass.

## Testing Plan

- Baseline: `cd server && mvn test` (before refactor) should be green.
- After refactor:
  - `cd server && mvn test` (full reactor)
  - Verify new `platform-shared` architecture tests pass.


# DDD Baseline And Shortlink Pilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the DDD architecture baseline and complete the first shortlink tactical DDD slice by moving shortlink lifecycle facts into domain events and translating them at the application boundary.

**Architecture:** Keep LinkForge as a single-runtime modular monolith. This plan does not touch accounts/security or every bounded context; it updates the architecture SSOT, adds boundary guardrails that can pass immediately, and pilots DDD event ownership inside `shortlink` without changing HTTP behavior.

**Tech Stack:** Java 17, Spring Boot 3.2, Maven reactor, JUnit 5, AssertJ, Mockito, ArchUnit.

---

## Scope

This is the first implementation plan for `docs/superpowers/specs/2026-04-28-ddd-refactor-design.md`.

It covers:

- DDD context-map documentation in `docs/architecture.md`
- backend architecture guardrails that encode the DDD layer direction
- shortlink lifecycle domain events for archive, restore, and delete
- an application-layer dispatcher that translates those domain events into existing shortlink integration-event publication
- focused tests and commits

It deliberately does not cover:

- `app/security` and `accounts` decoupling
- full shortlink update/use-case splitting
- platform, governance, or analytics aggregate hardening
- public HTTP API redesign

Those must be handled by separate implementation plans after this pilot is complete.

## File Structure

### Documentation

- Modify: `docs/architecture.md`
  - Add a DDD context map and tactical DDD rules under the backend section.

### Architecture Tests

- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`
  - Add DDD guardrails for domain purity and bounded-context contract-only imports.

### Shortlink Domain

- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkDomainEvent.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkArchived.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkRestored.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkDeleted.java`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
  - Record lifecycle domain events and expose `pullDomainEvents()`.
- Modify: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java`
  - Add aggregate tests for archive, restore, delete, and event clearing.

### Shortlink Application

- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java`
  - Translate shortlink domain events into the existing `ShortLinkEventPublisher`.
- Create: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcherTest.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`
  - Publish integration events by dispatching aggregate domain events after persistence succeeds.

---

### Task 1: Update Architecture SSOT

**Files:**

- Modify: `docs/architecture.md`

- [ ] **Step 1: Insert DDD context map into `docs/architecture.md`**

Add this section after the existing backend module list and before `## Redirect Correctness Path`:

```markdown
## DDD Context Map

The backend bounded contexts are code-ownership boundaries inside one deployed monolith.
They are not independently deployed services.

### Accounts

Owns tenants, users, roles, API keys, authentication state, and account-status checks.
Accounts may publish authentication and account-status capabilities, but persistence details,
token parsing internals, and role storage remain private to the context.

### Platform

Owns tenant applications, domains, quotas, and application policies.
Platform publishes application scope, domain hostname lookup, and quota views through
`contract-platform`.

### Shortlink

Owns durable link state, link lifecycle, destination rules, tags, revisions, and shortlink
mutation events. `ShortLink` is the first aggregate root for tactical DDD hardening.
Other contexts may read redirect metadata, ownership, and summaries only through
`contract-shortlink`.

### Redirect

Owns traffic-plane redirect resolution, Redis cache behavior, preview/not-found responses,
and lightweight visit-event append. Redirect does not own link truth; cache misses use the
shortlink published read contract.

### Analytics

Owns visit ingestion, aggregates, detail storage, statistics reads, and export integration.
Analytics read models remain private. Cross-context link enrichment uses published
shortlink contracts.

### Governance

Owns approval request lifecycle, approval decisions, sensitive-operation records, and audit
logs. Governance exposes published approval contracts where cross-context callers still need
approval orchestration.

## Tactical DDD Rules

- `domain` owns aggregate behavior, invariants, value objects, domain services, and internal domain events.
- `application` owns use-case orchestration, transactions, repository ports, authorization input handling, and integration-event publication.
- `interfaces` owns HTTP mapping, request validation, principal extraction, and transport response shaping.
- `infrastructure` owns MyBatis, Redis, schedulers, and persistence mapping.
- `contracts/*` owns published language shared across bounded contexts.
- Bounded contexts must not import another context's `domain`, `application`, `infrastructure`, or `interfaces` packages.
```

- [ ] **Step 2: Review the docs diff**

Run:

```bash
git diff -- docs/architecture.md
```

Expected: The diff contains only the new DDD context map and tactical DDD rules.

- [ ] **Step 3: Commit the documentation baseline**

Run:

```bash
git add docs/architecture.md
git commit -m "docs: document DDD context map"
```

Expected: Commit succeeds.

---

### Task 2: Add DDD Architecture Guardrails

**Files:**

- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

- [ ] **Step 1: Add a domain-purity guardrail test**

Add this test method after `domain_should_not_depend_on_outer_layers()`:

```java
    @Test
    void domain_should_not_depend_on_runtime_frameworks_or_persistence_tools() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "jakarta.persistence..",
                        "org.mybatis..",
                        "org.apache.ibatis..",
                        "org.springframework.data..",
                        "org.springframework.security.."
                );
        rule.check(CLASSES);
    }
```

- [ ] **Step 2: Run the architecture test**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ArchitectureTest#domain_should_not_depend_on_runtime_frameworks_or_persistence_tools test
```

Expected: PASS. If it fails, the output names the domain class that imports runtime framework or persistence tooling; move the named dependency to `application`, `infrastructure`, or `interfaces` according to the package role before continuing.

- [ ] **Step 3: Add a source-level app security import guard**

Add these constants near `FORBIDDEN_GOVERNANCE_ROLES_REFERENCE`:

```java
    private static final String FORBIDDEN_APP_ACCOUNTS_INFRASTRUCTURE_REFERENCE = "com.linkforge.accounts.infrastructure.";
    private static final String FORBIDDEN_APP_ACCOUNTS_DOMAIN_ROLES_REFERENCE = "com.linkforge.accounts.domain.Roles";
```

Add this test after `governance_service_source_should_not_import_accounts_roles()`:

```java
    @Test
    void app_security_source_should_not_gain_new_accounts_internal_imports() throws Exception {
        Path securityDir = resolveFromCurrentWorkspace(
                "app/src/main/java/com/linkforge/app/security",
                "server/app/src/main/java/com/linkforge/app/security"
        );

        List<Path> sources;
        try (var stream = Files.walk(securityDir)) {
            sources = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String text = Files.readString(source);
            boolean existingKnownDebt = source.getFileName().toString().equals("JwtAuthenticationFilter.java")
                    || source.getFileName().toString().equals("ApiKeyAuthenticationFilter.java")
                    || source.getFileName().toString().equals("SecurityConfig.java");
            if (existingKnownDebt) {
                continue;
            }
            if (text.contains(FORBIDDEN_APP_ACCOUNTS_INFRASTRUCTURE_REFERENCE)
                    || text.contains(FORBIDDEN_APP_ACCOUNTS_DOMAIN_ROLES_REFERENCE)) {
                violations.add(source.toString());
            }
        }

        assertThat(violations)
                .withFailMessage("New app/security accounts-internal imports are forbidden: %s", violations)
                .isEmpty();
    }
```

This guard intentionally freezes the known debt instead of failing the build before the accounts/security follow-up plan.

- [ ] **Step 4: Run the focused app security guard**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ArchitectureTest#app_security_source_should_not_gain_new_accounts_internal_imports test
```

Expected: PASS.

- [ ] **Step 5: Run all architecture tests**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ArchitectureTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the guardrails**

Run:

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "test: add DDD architecture guardrails"
```

Expected: Commit succeeds.

---

### Task 3: Add Shortlink Domain Events

**Files:**

- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkDomainEvent.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkArchived.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkRestored.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkDeleted.java`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Modify: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java`

- [ ] **Step 1: Write failing domain-event tests**

Replace `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java` with:

```java
package com.linkforge.shortlink.domain;

import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortLinkTest {

    @Test
    void archive_withNullNowUtc_shouldRejectImplicitLocalTimeFallback() {
        ShortLink link = activeLink();

        assertThatThrownBy(() -> link.archive(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nowUtc");
        assertThat(link.archivedAtUtc()).isNull();
    }

    @Test
    void archive_shouldRecordDomainEventOnlyWhenStateChanges() {
        ShortLink link = activeLink();
        LocalDateTime archivedAtUtc = LocalDateTime.parse("2026-04-28T01:02:03");

        assertThat(link.archive(archivedAtUtc)).isTrue();
        assertThat(link.archive(LocalDateTime.parse("2026-04-28T02:03:04"))).isFalse();

        assertThat(link.archivedAtUtc()).isEqualTo(archivedAtUtc);
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkArchived(1L, 1L, null, "abc123", archivedAtUtc));
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void restore_shouldRecordDomainEventOnlyWhenLinkWasArchived() {
        ShortLink link = activeLink();
        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));
        link.pullDomainEvents();

        assertThat(link.restore()).isTrue();
        assertThat(link.restore()).isFalse();

        assertThat(link.archivedAtUtc()).isNull();
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkRestored(1L, 1L, null, "abc123"));
    }

    @Test
    void markDeleted_shouldRequireArchiveAndRecordDomainEvent() {
        ShortLink link = activeLink();
        LocalDateTime deletedAtUtc = LocalDateTime.parse("2026-04-28T03:04:05");

        assertThatThrownBy(() -> link.markDeleted(deletedAtUtc))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("删除前请先归档");

        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));
        link.pullDomainEvents();

        link.markDeleted(deletedAtUtc);

        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkDeleted(1L, 1L, null, "abc123", deletedAtUtc));
    }

    @Test
    void pullDomainEvents_shouldReturnImmutableSnapshotAndClearAggregateEvents() {
        ShortLink link = activeLink();
        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));

        List<?> events = link.pullDomainEvents();

        assertThatThrownBy(() -> events.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    private static ShortLink activeLink() {
        return ShortLink.create(
                1L,
                1L,
                ShortCode.of("abc123"),
                HttpUrl.of("https://example.com/path"),
                "note",
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                99L
        );
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
cd server && mvn -q -pl shortlink/domain -Dtest=ShortLinkTest test
```

Expected: FAIL because `ShortLinkArchived`, `ShortLinkRestored`, `ShortLinkDeleted`, `pullDomainEvents()`, and `markDeleted(...)` do not exist yet.

- [ ] **Step 3: Add the domain event interface**

Create `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkDomainEvent.java`:

```java
package com.linkforge.shortlink.domain.event;

public interface ShortLinkDomainEvent {

    long linkId();

    long tenantId();

    Long domainId();

    String code();
}
```

- [ ] **Step 4: Add the archive event**

Create `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkArchived.java`:

```java
package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

public record ShortLinkArchived(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime archivedAtUtc
) implements ShortLinkDomainEvent {
}
```

- [ ] **Step 5: Add the restore event**

Create `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkRestored.java`:

```java
package com.linkforge.shortlink.domain.event;

public record ShortLinkRestored(
        long linkId,
        long tenantId,
        Long domainId,
        String code
) implements ShortLinkDomainEvent {
}
```

- [ ] **Step 6: Add the delete event**

Create `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkDeleted.java`:

```java
package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

public record ShortLinkDeleted(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime deletedAtUtc
) implements ShortLinkDomainEvent {
}
```

- [ ] **Step 7: Update `ShortLink` imports and event storage**

In `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`, add imports:

```java
import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkDomainEvent;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;

import java.util.ArrayList;
import java.util.List;
```

Add this field after `private LocalDateTime updatedAtUtc;`:

```java
    private final List<ShortLinkDomainEvent> domainEvents = new ArrayList<>();
```

Add these methods after `updatedAtUtc()`:

```java
    public List<ShortLinkDomainEvent> pullDomainEvents() {
        List<ShortLinkDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void recordDomainEvent(ShortLinkDomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event, "event"));
    }
```

- [ ] **Step 8: Update lifecycle methods in `ShortLink`**

Replace `archive`, `restore`, and `requireArchivedBeforeDelete` with:

```java
    public boolean archive(LocalDateTime nowUtc) {
        Objects.requireNonNull(nowUtc, "nowUtc must be provided in UTC");
        if (archivedAtUtc != null) {
            return false;
        }
        archivedAtUtc = nowUtc;
        recordDomainEvent(new ShortLinkArchived(id, tenantId, domainId, code.value(), nowUtc));
        return true;
    }

    public boolean restore() {
        if (archivedAtUtc == null) {
            return false;
        }
        archivedAtUtc = null;
        recordDomainEvent(new ShortLinkRestored(id, tenantId, domainId, code.value()));
        return true;
    }

    public void requireArchivedBeforeDelete() {
        if (archivedAtUtc == null) {
            throw new ShortLinkDomainException(DELETE_REQUIRES_ARCHIVE, "删除前请先归档（可避免误删）");
        }
    }

    public void markDeleted(LocalDateTime nowUtc) {
        Objects.requireNonNull(nowUtc, "nowUtc must be provided in UTC");
        requireArchivedBeforeDelete();
        recordDomainEvent(new ShortLinkDeleted(id, tenantId, domainId, code.value(), nowUtc));
    }
```

- [ ] **Step 9: Run domain tests**

Run:

```bash
cd server && mvn -q -pl shortlink/domain -Dtest=ShortLinkTest test
```

Expected: PASS.

- [ ] **Step 10: Run all shortlink domain tests**

Run:

```bash
cd server && mvn -q -pl shortlink/domain test
```

Expected: PASS.

- [ ] **Step 11: Commit the domain event model**

Run:

```bash
git add server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java
git commit -m "feat: add shortlink lifecycle domain events"
```

Expected: Commit succeeds.

---

### Task 4: Translate Shortlink Domain Events At The Application Boundary

**Files:**

- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java`
- Create: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcherTest.java`

- [ ] **Step 1: Write the failing dispatcher test**

Create `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcherTest.java`:

```java
package com.linkforge.shortlink.application.eventing;

import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShortLinkDomainEventDispatcherTest {

    @Test
    void publish_shouldTranslateArchivedRestoredAndDeletedEvents() {
        ShortLinkEventPublisher publisher = mock(ShortLinkEventPublisher.class);
        ShortLinkDomainEventDispatcher dispatcher = new ShortLinkDomainEventDispatcher(publisher);
        ShortLink link = activeLink();

        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));
        link.restore();
        link.archive(LocalDateTime.parse("2026-04-28T02:03:04"));
        link.markDeleted(LocalDateTime.parse("2026-04-28T03:04:05"));

        dispatcher.publish(link, Instant.parse("2026-04-28T09:00:00Z"));

        verify(publisher).archived(link, Instant.parse("2026-04-28T01:02:03Z"));
        verify(publisher).restored(link, Instant.parse("2026-04-28T09:00:00Z"));
        verify(publisher).archived(link, Instant.parse("2026-04-28T02:03:04Z"));
        verify(publisher).deleted(link, Instant.parse("2026-04-28T03:04:05Z"));
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void publish_shouldDoNothingWhenAggregateHasNoDomainEvents() {
        ShortLinkEventPublisher publisher = mock(ShortLinkEventPublisher.class);
        ShortLinkDomainEventDispatcher dispatcher = new ShortLinkDomainEventDispatcher(publisher);

        dispatcher.publish(activeLink(), Instant.parse("2026-04-28T09:00:00Z"));

        verifyNoInteractions(publisher);
    }

    private static ShortLink activeLink() {
        return ShortLink.create(
                1L,
                1L,
                ShortCode.of("abc123"),
                HttpUrl.of("https://example.com/path"),
                "note",
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                99L
        );
    }
}
```

- [ ] **Step 2: Run the dispatcher test and confirm it fails**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkDomainEventDispatcherTest test
```

Expected: FAIL because `ShortLinkDomainEventDispatcher` does not exist.

- [ ] **Step 3: Add the dispatcher**

Create `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java`:

```java
package com.linkforge.shortlink.application.eventing;

import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkDomainEvent;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public class ShortLinkDomainEventDispatcher {

    private final ShortLinkEventPublisher publisher;

    public ShortLinkDomainEventDispatcher(ShortLinkEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(ShortLink link, Instant fallbackOccurredAtUtc) {
        Objects.requireNonNull(link, "link");
        Instant fallback = fallbackOccurredAtUtc == null ? Instant.now() : fallbackOccurredAtUtc;
        for (ShortLinkDomainEvent event : link.pullDomainEvents()) {
            publishOne(link, event, fallback);
        }
    }

    private void publishOne(ShortLink link, ShortLinkDomainEvent event, Instant fallback) {
        if (event instanceof ShortLinkArchived archived) {
            publisher.archived(link, toInstant(archived.archivedAtUtc(), fallback));
            return;
        }
        if (event instanceof ShortLinkRestored) {
            publisher.restored(link, fallback);
            return;
        }
        if (event instanceof ShortLinkDeleted deleted) {
            publisher.deleted(link, toInstant(deleted.deletedAtUtc(), fallback));
        }
    }

    private static Instant toInstant(LocalDateTime utc, Instant fallback) {
        if (utc == null) {
            return fallback;
        }
        return utc.toInstant(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 4: Run dispatcher tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkDomainEventDispatcherTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the dispatcher**

Run:

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java server/shortlink/application/src/test/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcherTest.java
git commit -m "feat: dispatch shortlink domain events"
```

Expected: Commit succeeds.

---

### Task 5: Migrate Lifecycle Command Handlers To Domain Event Dispatch

**Files:**

- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java`

- [ ] **Step 1: Update archive handler imports and fields**

In `ArchiveShortLinkCommandHandler.java`, replace:

```java
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
```

with:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

Replace the field:

```java
    private final ShortLinkEventPublisher eventPublisher;
```

with:

```java
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
```

Replace the constructor parameter:

```java
            ShortLinkEventPublisher eventPublisher,
```

with:

```java
            ShortLinkDomainEventDispatcher domainEventDispatcher,
```

Replace the assignment:

```java
        this.eventPublisher = eventPublisher;
```

with:

```java
        this.domainEventDispatcher = domainEventDispatcher;
```

- [ ] **Step 2: Update archive handler behavior**

In `ArchiveShortLinkCommandHandler.handle(...)`, replace:

```java
        boolean alreadyArchived = link.archivedAtUtc() != null;
        if (!alreadyArchived) {
            LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            try {
                link.archive(nowUtc);
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            link.incrementVersion();
            Instant occurredAtUtc = nowUtc.toInstant(ZoneOffset.UTC);
            eventPublisher.archived(link, occurredAtUtc);
            postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
        }
```

with:

```java
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        boolean archived;
        try {
            archived = link.archive(nowUtc);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
        if (archived) {
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            link.incrementVersion();
            domainEventDispatcher.publish(link, nowUtc.toInstant(ZoneOffset.UTC));
            postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
        }
```

Remove the now-unused `import java.time.Instant;`.

- [ ] **Step 3: Update restore handler imports and fields**

In `RestoreShortLinkCommandHandler.java`, replace:

```java
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
```

with:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

Replace the field:

```java
    private final ShortLinkEventPublisher eventPublisher;
```

with:

```java
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
```

Replace the constructor parameter:

```java
            ShortLinkEventPublisher eventPublisher,
```

with:

```java
            ShortLinkDomainEventDispatcher domainEventDispatcher,
```

Replace the assignment:

```java
        this.eventPublisher = eventPublisher;
```

with:

```java
        this.domainEventDispatcher = domainEventDispatcher;
```

- [ ] **Step 4: Update restore handler behavior**

In `RestoreShortLinkCommandHandler.handle(...)`, replace:

```java
        boolean restored = false;
        if (link.archivedAtUtc() != null) {
            link.restore();
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            link.incrementVersion();
            restored = true;
        }

        if (restored) {
            eventPublisher.restored(link, clock.instant());
            postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
        }
```

with:

```java
        boolean restored = link.restore();
        if (restored) {
            Instant occurredAtUtc = clock.instant();
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            link.incrementVersion();
            domainEventDispatcher.publish(link, occurredAtUtc);
            postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
        }
```

Add:

```java
import java.time.Instant;
```

- [ ] **Step 5: Update delete handler imports and fields**

In `DeleteShortLinkCommandHandler.java`, replace:

```java
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
```

with:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

Replace the field:

```java
    private final ShortLinkEventPublisher eventPublisher;
```

with:

```java
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
```

Replace the constructor parameter:

```java
            ShortLinkEventPublisher eventPublisher,
```

with:

```java
            ShortLinkDomainEventDispatcher domainEventDispatcher,
```

Replace the assignment:

```java
        this.eventPublisher = eventPublisher;
```

with:

```java
        this.domainEventDispatcher = domainEventDispatcher;
```

Add imports:

```java
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
```

- [ ] **Step 6: Update delete handler behavior**

In `DeleteShortLinkCommandHandler.handle(...)`, replace:

```java
        try {
            link.requireArchivedBeforeDelete();
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        eventPublisher.deleted(link, clock.instant());
        linkTagRepository.deleteAllByLinkId(linkId);
        if (!shortLinkRepository.deleteByTenantIdAndId(tenantId, linkId, link.version())) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }
        postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
```

with:

```java
        Instant occurredAtUtc = clock.instant();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(occurredAtUtc, ZoneOffset.UTC);
        try {
            link.markDeleted(nowUtc);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        linkTagRepository.deleteAllByLinkId(linkId);
        if (!shortLinkRepository.deleteByTenantIdAndId(tenantId, linkId, link.version())) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }
        domainEventDispatcher.publish(link, occurredAtUtc);
        postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
```

- [ ] **Step 7: Run compilation-focused tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkDomainEventDispatcherTest,ShortLinkTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 8: Run shortlink application tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application test
```

Expected: PASS.

- [ ] **Step 9: Commit handler migration**

Run:

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/ArchiveShortLinkCommandHandler.java server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/RestoreShortLinkCommandHandler.java server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/DeleteShortLinkCommandHandler.java
git commit -m "refactor: publish shortlink lifecycle events from domain events"
```

Expected: Commit succeeds.

---

### Task 6: Final Verification

**Files:**

- Read: `docs/superpowers/specs/2026-04-28-ddd-refactor-design.md`
- Read: `docs/superpowers/plans/2026-04-28-ddd-baseline-shortlink-pilot.md`

- [ ] **Step 1: Run focused backend verification**

Run:

```bash
cd server && mvn -q -pl app,shortlink/domain,shortlink/application -am -Dtest=ArchitectureTest,ShortLinkTest,ShortLinkDomainEventDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 2: Run full backend unit verification**

Run:

```bash
cd server && mvn -q test
```

Expected: PASS.

- [ ] **Step 3: Check working tree**

Run:

```bash
git status --short
```

Expected: no output.

- [ ] **Step 4: Record completion note**

Add this entry to the implementation branch notes or PR description:

```markdown
Implemented the first DDD refactor slice:

- documented the backend DDD context map
- added DDD architecture guardrails
- introduced shortlink lifecycle domain events
- translated shortlink lifecycle domain events at the application boundary
- preserved existing shortlink HTTP/application behavior
```

Expected: The note matches the committed changes and does not claim accounts/security decoupling or full DDD migration.

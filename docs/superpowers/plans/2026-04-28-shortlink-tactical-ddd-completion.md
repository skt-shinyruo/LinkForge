# Shortlink Tactical DDD Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the next shortlink DDD slice by making create/update mutation facts aggregate-owned and by making the shortlink application facade thinner without changing public HTTP behavior.

**Architecture:** This plan builds on `ddd-baseline-shortlink-pilot`. `ShortLink` remains the aggregate root. Application command handlers continue to own transactions, repository calls, authorization/scope inputs, tag persistence, cache eviction, and governance integration, but they no longer publish create/update integration events directly. They collect aggregate domain events and translate them through `ShortLinkDomainEventDispatcher`.

**Tech Stack:** Java 17, Spring Boot 3.2, Maven reactor, JUnit 5, AssertJ, Mockito, ArchUnit.

---

## Preconditions

- Base this work on the branch that already contains `2026-04-28-ddd-baseline-shortlink-pilot.md`.
- Do not start from `main` unless the pilot branch has already been merged.
- Keep public HTTP request/response contracts stable.
- Do not touch shortlink published contracts, redirect/analytics consumers, or accounts/security in this plan.

## Scope

This plan covers:

- `ShortLinkCreated` and `ShortLinkUpdated` internal domain events
- dispatcher translation for created/updated domain events into existing integration-event publication
- migration of create/update/destination-approval execution code away from direct `ShortLinkEventPublisher` calls
- extraction of actor/application-scope resolution out of `ShortLinkApplicationService`
- focused tests and architecture verification

This plan deliberately does not cover:

- removing non-shortlink modules from shortlink internals
- redesigning HTTP DTOs
- moving tag persistence into the aggregate
- accounts/security decoupling
- platform/governance/analytics aggregate hardening

## File Structure

### Shortlink Domain

- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkCreated.java`
  - Internal event for newly created links.
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkUpdated.java`
  - Internal event for persisted link mutations that should emit the existing `SHORT_LINK_UPDATED_V1` integration event.
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
  - Record `ShortLinkCreated` from factory creation.
  - Add `markUpdated(LocalDateTime updatedAtUtc)`.
- Modify: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java`
  - Add domain-event tests for creation, rehydration, and update marking.

### Shortlink Application Eventing

- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java`
  - Translate `ShortLinkCreated` and `ShortLinkUpdated`.
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcherTest.java`
  - Add dispatcher tests for created/updated events.

### Shortlink Application Commands

- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
  - Inject `ShortLinkDomainEventDispatcher`.
  - Publish create integration event by dispatching aggregate domain events.
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
  - Inject `ShortLinkDomainEventDispatcher`.
  - Mark aggregate updated after persistence succeeds and dispatch domain events.
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutor.java`
  - Inject `ShortLinkDomainEventDispatcher`.
  - Mark aggregate updated after approval execution persistence succeeds and dispatch domain events.
- Modify tests:
  - `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java`
  - `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
  - `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutorTest.java`

### Shortlink Application Facade

- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolver.java`
  - Own actor/path/body application-scope resolution currently embedded in `ShortLinkApplicationService`.
- Create: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolverTest.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`
  - Delegate scope resolution to `ShortLinkActorScopeResolver`.
  - Keep `ShortLinkService` as the compatibility facade used by controllers.
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkApplicationServiceTest.java`

---

### Task 1: Add Created And Updated Domain Events

**Files:**

- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkCreated.java`
- Create: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkUpdated.java`
- Modify: `server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java`
- Modify: `server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java`

- [ ] **Step 1: Add failing domain tests**

Add these tests to `ShortLinkTest`:

```java
    @Test
    void create_shouldRecordCreatedDomainEvent() {
        ShortLink link = activeLink();

        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkCreated(1L, 1L, null, "abc123"));
    }

    @Test
    void rehydrate_shouldNotRecordCreatedDomainEvent() {
        ShortLink link = ShortLink.rehydrate(
                1L,
                1L,
                ShortCode.of("abc123"),
                HttpUrl.of("https://example.com/path"),
                "note",
                true,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                99L,
                3L,
                LocalDateTime.parse("2026-04-28T01:02:03"),
                LocalDateTime.parse("2026-04-28T01:02:03")
        );

        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void markUpdated_shouldRecordUpdatedDomainEvent() {
        ShortLink link = activeLink();
        link.pullDomainEvents();
        LocalDateTime updatedAtUtc = LocalDateTime.parse("2026-04-28T04:05:06");

        link.markUpdated(updatedAtUtc);

        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkUpdated(1L, 1L, null, "abc123", updatedAtUtc));
    }

    @Test
    void markUpdated_withNullNowUtc_shouldRejectImplicitLocalTimeFallback() {
        ShortLink link = activeLink();
        link.pullDomainEvents();

        assertThatThrownBy(() -> link.markUpdated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updatedAtUtc");
        assertThat(link.pullDomainEvents()).isEmpty();
    }
```

Add imports:

```java
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;
```

Also update existing `ShortLinkTest` tests that call `activeLink()` and then expect only archive/restore/delete events. Immediately after each fixture creation, clear the creation event:

```java
        ShortLink link = activeLink();
        link.pullDomainEvents();
```

Do not add that clearing line to `create_shouldRecordCreatedDomainEvent()`.

- [ ] **Step 2: Run the tests and confirm they fail**

Run:

```bash
cd server && mvn -q -pl shortlink/domain -Dtest=ShortLinkTest test
```

Expected: FAIL because `ShortLinkCreated`, `ShortLinkUpdated`, and `markUpdated(...)` do not exist yet.

- [ ] **Step 3: Add created event**

Create `ShortLinkCreated.java`:

```java
package com.linkforge.shortlink.domain.event;

public record ShortLinkCreated(
        long linkId,
        long tenantId,
        Long domainId,
        String code
) implements ShortLinkDomainEvent {
}
```

- [ ] **Step 4: Add updated event**

Create `ShortLinkUpdated.java`:

```java
package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

public record ShortLinkUpdated(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime updatedAtUtc
) implements ShortLinkDomainEvent {
}
```

- [ ] **Step 5: Update `ShortLink` imports**

In `ShortLink.java`, add:

```java
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;
```

- [ ] **Step 6: Record created event from factory creation**

In the longer `ShortLink.create(...)` factory, replace the direct `return new ShortLink(...)` block with:

```java
        ShortLink link = new ShortLink(
                id,
                tenantId,
                applicationId,
                domainId,
                code,
                lifecycleState,
                originalUrl,
                note,
                en,
                expiresAtUtc,
                null,
                redirectStatusCode,
                preview,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                createdByType,
                createdBy,
                0L,
                null,
                null
        );
        link.recordDomainEvent(new ShortLinkCreated(link.id, link.tenantId, link.domainId, link.code.value()));
        return link;
```

- [ ] **Step 7: Add `markUpdated(...)` to `ShortLink`**

Add this method near the lifecycle methods:

```java
    public void markUpdated(LocalDateTime updatedAtUtc) {
        Objects.requireNonNull(updatedAtUtc, "updatedAtUtc must be provided in UTC");
        recordDomainEvent(new ShortLinkUpdated(id, tenantId, domainId, code.value(), updatedAtUtc));
    }
```

- [ ] **Step 8: Run domain tests**

Run:

```bash
cd server && mvn -q -pl shortlink/domain -Dtest=ShortLinkTest test
```

Expected: PASS.

- [ ] **Step 9: Commit domain events**

Run:

```bash
git add server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/ShortLink.java \
  server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkCreated.java \
  server/shortlink/domain/src/main/java/com/linkforge/shortlink/domain/event/ShortLinkUpdated.java \
  server/shortlink/domain/src/test/java/com/linkforge/shortlink/domain/ShortLinkTest.java
git commit -m "feat: add shortlink create and update domain events"
```

Expected: Commit succeeds.

---

### Task 2: Dispatch Created And Updated Domain Events

**Files:**

- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcherTest.java`

- [ ] **Step 1: Add failing dispatcher tests**

Add these tests to `ShortLinkDomainEventDispatcherTest`:

```java
    @Test
    void publish_shouldTranslateCreatedEvent() {
        ShortLinkEventPublisher publisher = mock(ShortLinkEventPublisher.class);
        ShortLinkDomainEventDispatcher dispatcher = new ShortLinkDomainEventDispatcher(publisher);
        ShortLink link = newLinkWithCreatedEvent();
        Instant occurredAtUtc = Instant.parse("2026-04-28T01:02:03Z");

        dispatcher.publish(link, occurredAtUtc);

        verify(publisher).created(link, occurredAtUtc);
        verifyNoMoreInteractions(publisher);
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void publish_shouldTranslateUpdatedEventUsingEventTime() {
        ShortLinkEventPublisher publisher = mock(ShortLinkEventPublisher.class);
        ShortLinkDomainEventDispatcher dispatcher = new ShortLinkDomainEventDispatcher(publisher);
        ShortLink link = activeLink();
        link.pullDomainEvents();
        link.markUpdated(LocalDateTime.parse("2026-04-28T04:05:06"));

        dispatcher.publish(link, Instant.parse("2026-04-28T00:00:00Z"));

        verify(publisher).updated(link, Instant.parse("2026-04-28T04:05:06Z"));
        verifyNoMoreInteractions(publisher);
        assertThat(link.pullDomainEvents()).isEmpty();
    }
```

After Task 1, `ShortLink.create(...)` records a created event. Update the existing dispatcher tests so `activeLink()` returns a clean aggregate without queued events:

```java
    private static ShortLink activeLink() {
        ShortLink link = ShortLink.create(
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
        link.pullDomainEvents();
        return link;
    }
```

Add a second helper named `newLinkWithCreatedEvent()` and use it only for the created test:

```java
    private static ShortLink newLinkWithCreatedEvent() {
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
```

- [ ] **Step 2: Run dispatcher tests and confirm they fail**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkDomainEventDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because created/updated events are ignored by the dispatcher.

- [ ] **Step 3: Update dispatcher imports**

Add:

```java
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;
```

- [ ] **Step 4: Translate created and updated events**

In `publishOne(...)`, add these branches before the existing archive branch:

```java
        if (event instanceof ShortLinkCreated) {
            publisher.created(link, fallback);
            return;
        }
        if (event instanceof ShortLinkUpdated updated) {
            publisher.updated(link, toInstant(updated.updatedAtUtc(), fallback));
            return;
        }
```

- [ ] **Step 5: Run dispatcher tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkDomainEventDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit dispatcher support**

Run:

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcher.java \
  server/shortlink/application/src/test/java/com/linkforge/shortlink/application/eventing/ShortLinkDomainEventDispatcherTest.java
git commit -m "feat: dispatch shortlink create and update domain events"
```

Expected: Commit succeeds.

---

### Task 3: Migrate Create Handler To Domain Event Dispatch

**Files:**

- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java`

- [ ] **Step 1: Add a failing constructor dependency test**

In `CreateShortLinkCommandHandlerTest`, add:

```java
    @Test
    void constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher() {
        Constructor<?> constructor = CreateShortLinkCommandHandler.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ShortLinkDomainEventDispatcher.class);
        assertThat(constructor.getParameterTypes())
                .doesNotContain(ShortLinkEventPublisher.class);
    }
```

Add import:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=CreateShortLinkCommandHandlerTest#constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the handler still injects `ShortLinkEventPublisher`.

- [ ] **Step 3: Replace create handler dependency**

In `CreateShortLinkCommandHandler.java`, replace:

```java
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
```

with:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

Replace field:

```java
    private final ShortLinkEventPublisher eventPublisher;
```

with:

```java
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
```

Replace constructor parameter:

```java
            ShortLinkEventPublisher eventPublisher,
```

with:

```java
            ShortLinkDomainEventDispatcher domainEventDispatcher,
```

Replace assignment:

```java
        this.eventPublisher = eventPublisher;
```

with:

```java
        this.domainEventDispatcher = domainEventDispatcher;
```

- [ ] **Step 4: Dispatch aggregate events after insert succeeds**

Replace:

```java
        eventPublisher.created(persisted, clock.instant());
```

with:

```java
        domainEventDispatcher.publish(link, clock.instant());
```

Use `link`, not `persisted`, because `link` is the aggregate instance that recorded `ShortLinkCreated`. Keep `persisted` for DTO mapping.

- [ ] **Step 5: Update existing test constructors**

In `CreateShortLinkCommandHandlerTest`, replace each `mock(ShortLinkEventPublisher.class)` constructor argument with:

```java
mock(ShortLinkDomainEventDispatcher.class)
```

Keep the `ShortLinkEventPublisher` import in this test file if the constructor guard still references `ShortLinkEventPublisher.class`.

- [ ] **Step 6: Add dispatch verification to the happy-path create test**

In `handle_shouldValidateApplicationScopeAndQuota_viaPlatformContract`, keep a local dispatcher mock:

```java
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
```

Pass it into the handler constructor. After existing verifications, add:

```java
        verify(domainEventDispatcher).publish(any(ShortLink.class), eq(clock.instant()));
```

- [ ] **Step 7: Run create handler tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=CreateShortLinkCommandHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 8: Commit create handler migration**

Run:

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandler.java \
  server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/CreateShortLinkCommandHandlerTest.java
git commit -m "refactor: publish shortlink create events from domain events"
```

Expected: Commit succeeds.

---

### Task 4: Migrate Update And Approval Execution To Domain Event Dispatch

**Files:**

- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutor.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutorTest.java`

- [ ] **Step 1: Add failing update handler constructor dependency test**

In `UpdateShortLinkCommandHandlerTest`, add:

```java
    @Test
    void constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher() {
        Constructor<?> constructor = UpdateShortLinkCommandHandler.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ShortLinkDomainEventDispatcher.class);
        assertThat(constructor.getParameterTypes())
                .doesNotContain(ShortLinkEventPublisher.class);
    }
```

Add import:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=UpdateShortLinkCommandHandlerTest#constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the handler still injects `ShortLinkEventPublisher`.

- [ ] **Step 3: Replace update handler dependency**

In `UpdateShortLinkCommandHandler.java`, replace:

```java
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
```

with:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

Replace field, constructor parameter, and assignment from `ShortLinkEventPublisher eventPublisher` to `ShortLinkDomainEventDispatcher domainEventDispatcher`.

- [ ] **Step 4: Mark aggregate updated after persistence succeeds**

Replace:

```java
        eventPublisher.updated(link, clock.instant());
```

with:

```java
        LocalDateTime updatedAtUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        link.markUpdated(updatedAtUtc);
        domainEventDispatcher.publish(link, updatedAtUtc.toInstant(ZoneOffset.UTC));
```

Keep this after tag handling so a tags-only accepted update still emits the existing update integration event.

- [ ] **Step 5: Update update handler tests**

In `UpdateShortLinkCommandHandlerTest`, replace constructor arguments using `mock(ShortLinkEventPublisher.class)` with `mock(ShortLinkDomainEventDispatcher.class)`.

In approval-request tests, replace event publisher verifications:

```java
        verify(eventPublisher, never()).updated(eq(link), eq(clock.instant()));
```

with:

```java
        verify(domainEventDispatcher, never()).publish(eq(link), eq(clock.instant()));
```

Use a named `domainEventDispatcher` mock in tests that need verification.

- [ ] **Step 6: Add approval executor constructor dependency test**

In `LinkDestinationChangeApprovalExecutorTest`, add:

```java
    @Test
    void constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher() {
        Constructor<?> constructor = LinkDestinationChangeApprovalExecutor.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ShortLinkDomainEventDispatcher.class);
        assertThat(constructor.getParameterTypes())
                .doesNotContain(ShortLinkEventPublisher.class);
    }
```

Add imports:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import java.lang.reflect.Constructor;
```

- [ ] **Step 7: Replace approval executor dependency**

In `LinkDestinationChangeApprovalExecutor.java`, replace:

```java
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
```

with:

```java
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
```

Replace field, constructor parameter, and assignment from `ShortLinkEventPublisher eventPublisher` to `ShortLinkDomainEventDispatcher domainEventDispatcher`.

- [ ] **Step 8: Dispatch updated domain event after approval execution persistence succeeds**

Replace:

```java
        eventPublisher.updated(link, executedAt.toInstant(ZoneOffset.UTC));
```

with:

```java
        link.markUpdated(executedAt);
        domainEventDispatcher.publish(link, executedAt.toInstant(ZoneOffset.UTC));
```

- [ ] **Step 9: Update approval executor tests**

Replace constructor arguments using `mock(ShortLinkEventPublisher.class)` with `mock(ShortLinkDomainEventDispatcher.class)`.

For the successful execution test, verify:

```java
        verify(domainEventDispatcher).publish(link, executedAt.toInstant(ZoneOffset.UTC));
```

Keep the `ShortLinkEventPublisher` import in this test file if the constructor guard still references `ShortLinkEventPublisher.class`.

- [ ] **Step 10: Run update and approval tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=UpdateShortLinkCommandHandlerTest,LinkDestinationChangeApprovalExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 11: Commit update and approval migration**

Run:

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandler.java \
  server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutor.java \
  server/shortlink/application/src/test/java/com/linkforge/shortlink/application/command/UpdateShortLinkCommandHandlerTest.java \
  server/shortlink/application/src/test/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutorTest.java
git commit -m "refactor: publish shortlink update events from domain events"
```

Expected: Commit succeeds.

---

### Task 5: Extract Actor Scope Resolution From The Shortlink Facade

**Files:**

- Create: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolver.java`
- Create: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolverTest.java`
- Modify: `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`
- Modify: `server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkApplicationServiceTest.java`

- [ ] **Step 1: Create failing resolver tests**

Create `ShortLinkActorScopeResolverTest.java`:

```java
package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShortLinkActorScopeResolverTest {

    @Test
    void resolveCreateForUser_shouldPinBodyApplicationToPathApplication() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(applicationScopePort);
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("TENANT_ADMIN"));

        ShortLinkService.CreateLinkRequest request = resolver.resolveCreateForUser(
                actor,
                new ShortLinkService.ScopedCreateLinkRequest(
                        createRequest(2001L),
                        2001L
                )
        );

        assertThat(request.applicationId()).isEqualTo(2001L);
        verify(applicationScopePort).requireApplicationExists(1L, 2001L);
    }

    @Test
    void resolveCreateForUser_shouldRejectPathBodyMismatch() {
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(mock(ApplicationScopePort.class));
        UserActor actor = new UserActor(1L, 99L, "user@example.com", Set.of("TENANT_ADMIN"));

        assertThatThrownBy(() -> resolver.resolveCreateForUser(
                actor,
                new ShortLinkService.ScopedCreateLinkRequest(createRequest(2002L), 2001L)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void resolveCreateForApiKey_shouldRequireApplicationWhenActorIsUnscoped() {
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(mock(ApplicationScopePort.class));
        ApiKeyActor actor = new ApiKeyActor(1L, 55L, null);

        assertThatThrownBy(() -> resolver.resolveCreateForApiKey(
                actor,
                new ShortLinkService.ScopedCreateLinkRequest(createRequest(null), null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void resolveBrowseForApiKey_shouldUsePrincipalApplicationWhenPresent() {
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ShortLinkActorScopeResolver resolver = new ShortLinkActorScopeResolver(applicationScopePort);
        ApiKeyActor actor = new ApiKeyActor(1L, 55L, 2001L);

        ShortLinkSearchQuery query = resolver.resolveBrowseForApiKey(
                actor,
                new ShortLinkService.BrowseLinksRequest(false, true, "abc", "tag", null, null, 0, 20, 100)
        );

        assertThat(query.applicationId()).isEqualTo(2001L);
        verifyNoInteractions(applicationScopePort);
    }

    private static ShortLinkService.CreateLinkRequest createRequest(Long applicationId) {
        return new ShortLinkService.CreateLinkRequest(
                "https://example.com",
                "note",
                null,
                true,
                "abc123",
                Set.of("alpha"),
                302,
                false,
                null,
                null,
                List.of(),
                applicationId,
                3001L,
                "ACTIVE"
        );
    }
}
```

- [ ] **Step 2: Run resolver tests and confirm they fail**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkActorScopeResolverTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `ShortLinkActorScopeResolver` does not exist.

- [ ] **Step 3: Add the resolver**

Create `ShortLinkActorScopeResolver.java`:

```java
package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import org.springframework.stereotype.Component;

@Component
public class ShortLinkActorScopeResolver {

    private final ApplicationScopePort applicationScopePort;

    public ShortLinkActorScopeResolver(ApplicationScopePort applicationScopePort) {
        this.applicationScopePort = applicationScopePort;
    }

    public ShortLinkService.CreateLinkRequest resolveCreateForUser(
            UserActor actor,
            ShortLinkService.ScopedCreateLinkRequest request
    ) {
        ShortLinkService.CreateLinkRequest createRequest = requireCreateRequest(request);
        Long pathApplicationId = request.pathApplicationId();
        if (pathApplicationId == null) {
            return createRequest;
        }
        applicationScopePort.requireApplicationExists(actor.tenantId(), pathApplicationId);
        Long requestApplicationId = createRequest.applicationId();
        if (requestApplicationId != null && !requestApplicationId.equals(pathApplicationId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求体中的 applicationId 与路径不一致");
        }
        return withApplicationId(createRequest, pathApplicationId);
    }

    public ShortLinkService.CreateLinkRequest resolveCreateForApiKey(
            ApiKeyActor actor,
            ShortLinkService.ScopedCreateLinkRequest request
    ) {
        ShortLinkService.CreateLinkRequest createRequest = requireCreateRequest(request);
        Long effectiveApplicationId = resolveAuthorizedApplicationId(
                actor,
                createRequest.applicationId(),
                request.pathApplicationId(),
                true
        );
        if (request.pathApplicationId() != null) {
            applicationScopePort.requireApplicationExists(actor.tenantId(), effectiveApplicationId);
        }
        return effectiveApplicationId == null ? createRequest : withApplicationId(createRequest, effectiveApplicationId);
    }

    public ShortLinkSearchQuery resolveBrowseForUser(long tenantId, ShortLinkService.BrowseLinksRequest request) {
        Long applicationId = resolveRequestedApplicationId(tenantId, request);
        return new ShortLinkSearchQuery(
                request.archived() != null && request.archived(),
                request.enabled(),
                request.keyword(),
                request.tag(),
                applicationId
        );
    }

    public ShortLinkSearchQuery resolveBrowseForApiKey(ApiKeyActor actor, ShortLinkService.BrowseLinksRequest request) {
        Long effectiveApplicationId = resolveAuthorizedApplicationId(
                actor,
                request.requestedApplicationId(),
                request.pathApplicationId(),
                false
        );
        if (request.pathApplicationId() != null) {
            applicationScopePort.requireApplicationExists(actor.tenantId(), effectiveApplicationId);
        }
        return new ShortLinkSearchQuery(
                request.archived() != null && request.archived(),
                request.enabled(),
                request.keyword(),
                request.tag(),
                effectiveApplicationId
        );
    }

    public PageQuery pageQuery(ShortLinkService.BrowseLinksRequest request) {
        if (request == null) {
            return PageQuery.of(0, 20, 100);
        }
        return PageQuery.of(request.page(), request.size(), request.maxPageSize());
    }

    private Long resolveRequestedApplicationId(long tenantId, ShortLinkService.BrowseLinksRequest request) {
        if (request.pathApplicationId() == null) {
            return request.requestedApplicationId();
        }
        if (request.requestedApplicationId() != null && !request.pathApplicationId().equals(request.requestedApplicationId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求中的 applicationId 与路径不一致");
        }
        applicationScopePort.requireApplicationExists(tenantId, request.pathApplicationId());
        return request.pathApplicationId();
    }

    private static Long resolveAuthorizedApplicationId(
            ApiKeyActor actor,
            Long requestApplicationId,
            Long pathApplicationId,
            boolean applicationRequired
    ) {
        Long principalApplicationId = actor.applicationId();
        Long requestedApplicationId = pathApplicationId != null ? pathApplicationId : requestApplicationId;
        if (principalApplicationId != null) {
            if (requestedApplicationId != null && !principalApplicationId.equals(requestedApplicationId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 无权访问该应用");
            }
            return principalApplicationId;
        }
        if (requestedApplicationId == null && applicationRequired) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationId 不能为空");
        }
        return requestedApplicationId;
    }

    private static ShortLinkService.CreateLinkRequest requireCreateRequest(
            ShortLinkService.ScopedCreateLinkRequest request
    ) {
        if (request == null || request.createRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CreateLinkRequest 不能为空");
        }
        return request.createRequest();
    }

    private static ShortLinkService.CreateLinkRequest withApplicationId(
            ShortLinkService.CreateLinkRequest createRequest,
            long applicationId
    ) {
        return new ShortLinkService.CreateLinkRequest(
                createRequest.originalUrl(),
                createRequest.note(),
                createRequest.expiresAt(),
                createRequest.enabled(),
                createRequest.customCode(),
                createRequest.tags(),
                createRequest.redirectStatusCode(),
                createRequest.previewEnabled(),
                createRequest.unavailableLandingUrl(),
                createRequest.queryForwardMode(),
                createRequest.queryForwardAllowlist(),
                applicationId,
                createRequest.domainId(),
                createRequest.lifecycleState()
        );
    }
}
```

- [ ] **Step 4: Refactor `ShortLinkApplicationService` constructor**

Replace field:

```java
    private final ApplicationScopePort applicationScopePort;
```

with:

```java
    private final ShortLinkActorScopeResolver actorScopeResolver;
```

Replace constructor parameter:

```java
            ApplicationScopePort applicationScopePort
```

with:

```java
            ShortLinkActorScopeResolver actorScopeResolver
```

Replace assignment:

```java
        this.applicationScopePort = applicationScopePort;
```

with:

```java
        this.actorScopeResolver = actorScopeResolver;
```

Remove the `ApplicationScopePort` import.

- [ ] **Step 5: Delegate scope resolution from facade methods**

Replace `createForUser(...)` with:

```java
    @Override
    public LinkDto createForUser(UserActor actor, ScopedCreateLinkRequest request) {
        CreateLinkRequest scopedRequest = actorScopeResolver.resolveCreateForUser(actor, request);
        return create(actor.tenantId(), CreatedBy.user(actor.userId()), scopedRequest);
    }
```

Replace `createForApiKey(...)` with:

```java
    @Override
    public LinkDto createForApiKey(ApiKeyActor actor, ScopedCreateLinkRequest request) {
        CreateLinkRequest scopedRequest = actorScopeResolver.resolveCreateForApiKey(actor, request);
        return create(actor.tenantId(), CreatedBy.apiKey(actor.apiKeyId()), scopedRequest);
    }
```

Replace `browseForUser(...)` with:

```java
    @Override
    public PageResult<LinkDto> browseForUser(UserActor actor, BrowseLinksRequest request) {
        return search(
                actor.tenantId(),
                actorScopeResolver.resolveBrowseForUser(actor.tenantId(), request),
                actorScopeResolver.pageQuery(request)
        );
    }
```

Replace `browseForApiKey(...)` with:

```java
    @Override
    public PageResult<LinkDto> browseForApiKey(ApiKeyActor actor, BrowseLinksRequest request) {
        return search(
                actor.tenantId(),
                actorScopeResolver.resolveBrowseForApiKey(actor, request),
                actorScopeResolver.pageQuery(request)
        );
    }
```

Replace `exportCsvForUser(...)` with:

```java
    @Override
    public ShortLinkCsvExport exportCsvForUser(UserActor actor, BrowseLinksRequest request) {
        return exportCsv(
                actor.tenantId(),
                actorScopeResolver.resolveBrowseForUser(actor.tenantId(), request),
                actorScopeResolver.pageQuery(request)
        );
    }
```

Delete these private methods from `ShortLinkApplicationService`:

```java
withUserApplicationScope(...)
withApiKeyApplicationScope(...)
buildSearchQuery(long tenantId, BrowseLinksRequest request)
buildSearchQuery(ApiKeyActor actor, BrowseLinksRequest request)
resolveRequestedApplicationId(...)
resolveAuthorizedApplicationId(...)
requireCreateRequest(...)
buildPageQuery(...)
withApplicationId(...)
```

Remove now-unused imports:

```java
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
```

- [ ] **Step 6: Update application service tests**

In `ShortLinkApplicationServiceTest`, replace constructor arguments that pass `ApplicationScopePort` with a `ShortLinkActorScopeResolver` mock.

For tests that currently verify scope behavior through `ShortLinkApplicationService`, move that expectation to `ShortLinkActorScopeResolverTest`. Keep service tests focused on delegation. Example service-level assertion:

```java
        verify(actorScopeResolver).resolveBrowseForApiKey(actor, request);
        verify(searchHandler).handle(actor.tenantId(), expectedQuery, expectedPage);
```

- [ ] **Step 7: Run resolver and application service tests**

Run:

```bash
cd server && mvn -q -pl shortlink/application -am -Dtest=ShortLinkActorScopeResolverTest,ShortLinkApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 8: Commit facade split**

Run:

```bash
git add server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolver.java \
  server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java \
  server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkActorScopeResolverTest.java \
  server/shortlink/application/src/test/java/com/linkforge/shortlink/application/ShortLinkApplicationServiceTest.java
git commit -m "refactor: extract shortlink actor scope resolution"
```

Expected: Commit succeeds.

---

### Task 6: Add Shortlink DDD Regression Guardrails

**Files:**

- Modify: `server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java`

- [ ] **Step 1: Add source-level guard for direct create/update event publication**

Add this test after `app_security_source_should_not_gain_new_accounts_internal_imports()`:

```java
    @Test
    void shortlink_create_update_application_code_should_dispatch_domain_events_instead_of_publishing_directly() throws Exception {
        Path shortlinkCommandDir = resolveFromCurrentWorkspace(
                "shortlink/application/src/main/java/com/linkforge/shortlink/application/command",
                "server/shortlink/application/src/main/java/com/linkforge/shortlink/application/command"
        );
        Path shortlinkApprovalDir = resolveFromCurrentWorkspace(
                "shortlink/application/src/main/java/com/linkforge/shortlink/application/approval",
                "server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval"
        );

        List<Path> sources;
        try (var commandStream = Files.walk(shortlinkCommandDir);
             var approvalStream = Files.walk(shortlinkApprovalDir)) {
            sources = java.util.stream.Stream
                    .concat(commandStream, approvalStream)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String text = Files.readString(source);
            if (text.contains("ShortLinkEventPublisher")
                    || text.contains(".created(")
                    || text.contains(".updated(")) {
                violations.add(source.toString());
            }
        }

        assertThat(violations)
                .withFailMessage("Shortlink application code must dispatch aggregate domain events instead of directly publishing create/update events: %s", violations)
                .isEmpty();
    }
```

- [ ] **Step 2: Run the guardrail test**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ArchitectureTest#shortlink_create_update_application_code_should_dispatch_domain_events_instead_of_publishing_directly -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 3: Run all architecture tests**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 4: Commit guardrail**

Run:

```bash
git add server/app/src/test/java/com/linkforge/architecture/ArchitectureTest.java
git commit -m "test: guard shortlink domain event publishing"
```

Expected: Commit succeeds.

---

### Task 7: Final Verification

**Files:** none

- [ ] **Step 1: Run focused shortlink verification**

Run:

```bash
cd server && mvn -q -pl app,shortlink/domain,shortlink/application -am -Dtest=ArchitectureTest,ShortLinkTest,ShortLinkDomainEventDispatcherTest,CreateShortLinkCommandHandlerTest,UpdateShortLinkCommandHandlerTest,LinkDestinationChangeApprovalExecutorTest,ShortLinkActorScopeResolverTest,ShortLinkApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 2: Run full backend verification**

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

- [ ] **Step 4: Review remaining direct event publisher usage**

Run:

```bash
rg -n "ShortLinkEventPublisher|\\.created\\(|\\.updated\\(" server/shortlink/application/src/main/java server/shortlink/application/src/test/java
```

Expected: production references remain only in `ShortLinkDomainEventDispatcher` and the `ShortLinkEventPublisher` port interface. Test references may remain for constructor guards and dispatcher mocks. There should be no direct `eventPublisher.created(...)` or `eventPublisher.updated(...)` calls in create/update/approval application code.

- [ ] **Step 5: Record completion note**

Add a short note to the branch final response:

```text
Completed the second shortlink DDD slice: created/updated mutation facts now originate from the ShortLink aggregate and are translated by ShortLinkDomainEventDispatcher. ShortLinkApplicationService keeps its compatibility facade role, while actor/application scope resolution moved to ShortLinkActorScopeResolver.
```

Expected: The implementation branch is ready for review, merge, or PR according to the finishing-development-branch workflow.

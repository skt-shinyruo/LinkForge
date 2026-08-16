package com.linkforge.shortlink.application.eventing;

import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.PatchValue;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkPatch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ShortLinkDomainEventDispatcherTest {

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
        link.applyUpdate(
                new ShortLinkPatch(
                        PatchValue.set(HttpUrl.of("https://example.com/updated")),
                        null, null, null, null, null, null, null, null, null
                ),
                false,
                LocalDateTime.parse("2026-04-28T04:05:06")
        );

        dispatcher.publish(link, Instant.parse("2026-04-28T00:00:00Z"));

        verify(publisher).updated(link, Instant.parse("2026-04-28T04:05:06Z"));
        verifyNoMoreInteractions(publisher);
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void publish_shouldTranslateOwnershipChangeAsUpdatedEventUsingEventTime() {
        ShortLinkEventPublisher publisher = mock(ShortLinkEventPublisher.class);
        ShortLinkDomainEventDispatcher dispatcher = new ShortLinkDomainEventDispatcher(publisher);
        ShortLink link = activeLink();
        link.reconcileOwnership(2001L, 3001L, LocalDateTime.parse("2026-04-28T05:06:07"));

        dispatcher.publish(link, Instant.parse("2026-04-28T00:00:00Z"));

        verify(publisher).updated(link, Instant.parse("2026-04-28T05:06:07Z"));
        verifyNoMoreInteractions(publisher);
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void publish_shouldTranslateArchivedRestoredAndDeletedEvents() {
        ShortLinkEventPublisher publisher = mock(ShortLinkEventPublisher.class);
        ShortLinkDomainEventDispatcher dispatcher = new ShortLinkDomainEventDispatcher(publisher);
        ShortLink link = activeLink();

        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));
        link.restore();
        link.archive(LocalDateTime.parse("2026-04-28T02:03:04"));
        link.delete(LocalDateTime.parse("2026-04-28T03:04:05"));

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
        ShortLink link = newLinkWithCreatedEvent();
        link.pullDomainEvents();
        return link;
    }

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
}

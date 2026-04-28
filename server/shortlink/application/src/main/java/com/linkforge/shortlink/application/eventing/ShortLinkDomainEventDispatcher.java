package com.linkforge.shortlink.application.eventing;

import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkDomainEvent;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;
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
        if (event instanceof ShortLinkCreated) {
            publisher.created(link, fallback);
            return;
        }
        if (event instanceof ShortLinkUpdated updated) {
            publisher.updated(link, toInstant(updated.updatedAtUtc(), fallback));
            return;
        }
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

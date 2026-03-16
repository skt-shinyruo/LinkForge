package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.domain.ShortLink;

import java.time.Instant;

public interface ShortLinkEventPublisher {

    void created(ShortLink link, Instant occurredAtUtc);

    void updated(ShortLink link, Instant occurredAtUtc);

    void archived(ShortLink link, Instant occurredAtUtc);

    void restored(ShortLink link, Instant occurredAtUtc);

    void deleted(ShortLink link, Instant occurredAtUtc);
}


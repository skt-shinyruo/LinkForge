package com.linkforge.shortlink.infrastructure.eventing;

import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkEntityMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ShortLinkEventPublisherAdapter implements ShortLinkEventPublisher {

    private final ShortLinkEventAppender appender;

    public ShortLinkEventPublisherAdapter(ShortLinkEventAppender appender) {
        this.appender = appender;
    }

    @Override
    public void created(ShortLink link, Instant occurredAtUtc) {
        ShortLinkEntity e = ShortLinkEntityMapper.toEntity(link);
        appender.appendCreated(e, occurredAtUtc);
    }

    @Override
    public void updated(ShortLink link, Instant occurredAtUtc) {
        ShortLinkEntity e = ShortLinkEntityMapper.toEntity(link);
        appender.appendUpdated(e, occurredAtUtc);
    }

    @Override
    public void archived(ShortLink link, Instant occurredAtUtc) {
        ShortLinkEntity e = ShortLinkEntityMapper.toEntity(link);
        appender.appendArchived(e, occurredAtUtc);
    }

    @Override
    public void restored(ShortLink link, Instant occurredAtUtc) {
        ShortLinkEntity e = ShortLinkEntityMapper.toEntity(link);
        appender.appendRestored(e, occurredAtUtc);
    }

    @Override
    public void deleted(ShortLink link, Instant occurredAtUtc) {
        ShortLinkEntity e = ShortLinkEntityMapper.toEntity(link);
        appender.appendDeleted(e, occurredAtUtc);
    }
}


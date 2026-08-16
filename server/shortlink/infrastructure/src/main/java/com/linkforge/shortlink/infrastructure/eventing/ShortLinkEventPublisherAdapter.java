package com.linkforge.shortlink.infrastructure.eventing;

import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkEntityMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 把领域层短链转换为稳定的集成事件契约，并交给事件存储追加。
 *
 * <p>应用层在聚合保存成功后直接调用本端口。本适配器必须与聚合保存处于同一事务：任何转换、序列化或追加
 * 失败都应回滚事务，使调用方能够重新加载聚合并重试；这里不进行异步发送，也不吞掉异常。</p>
 */
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

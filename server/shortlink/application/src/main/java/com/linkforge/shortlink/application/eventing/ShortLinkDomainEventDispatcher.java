package com.linkforge.shortlink.application.eventing;

import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkDomainEvent;
import com.linkforge.shortlink.domain.event.ShortLinkOwnershipChanged;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 将短链聚合内部事件按记录顺序翻译为集成事件发布调用。
 *
 * <p>{@link #publish(ShortLink, Instant)} 先通过 {@link ShortLink#pullDomainEvents()} 破坏性取出全部事件，
 * 再逐条调用 {@link ShortLinkEventPublisher}。因此发布器必须在保存聚合的同一事务中做 durable append：
 * 任一调用失败时应回滚整个业务事务，并通过重新执行用例、重新加载聚合来重试；失败后复用当前内存聚合
 * 不能恢复已被取走的事件。</p>
 *
 * <p>dispatcher 不生成幂等键，也不跨聚合排序。重复调用已清空且没有新操作的聚合不会再次发布；若调用方
 * 人为重建并重复发布同一业务变化，是否去重由集成事件 ID 与消费方负责。新增
 * {@link ShortLinkDomainEvent} 子类型时必须同步扩展本类，否则该事件会被取出但没有对应发布调用。</p>
 */
@Component
public class ShortLinkDomainEventDispatcher {

    private final ShortLinkEventPublisher publisher;

    public ShortLinkDomainEventDispatcher(ShortLinkEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 取出并按顺序发布聚合当前积累的全部领域事件。
     *
     * <p>更新、ownership、归档和删除事件携带的 {@link LocalDateTime} 按 UTC 解释；事件时间为空，或创建、
     * 恢复事件本身没有时间时，使用 {@code fallbackOccurredAtUtc}。fallback 为空则以调用瞬间的系统时间兜底。
     * 聚合没有待发布事件时不调用 publisher。</p>
     *
     * @param link 非空的、已经完成持久化写入准备的短链聚合
     * @param fallbackOccurredAtUtc 可选 UTC 兜底时间
     * @throws NullPointerException {@code link} 为空时抛出
     * @throws RuntimeException publisher 追加失败时原样传播，调用方事务应回滚
     */
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
        if (event instanceof ShortLinkOwnershipChanged ownershipChanged) {
            publisher.updated(link, toInstant(ownershipChanged.changedAtUtc(), fallback));
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

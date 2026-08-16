package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.domain.ShortLink;

import java.time.Instant;

/**
 * 将短链领域变化追加为跨上下文集成事件的事务端口。
 *
 * <p>每个方法接收业务操作后的聚合快照和 UTC 发生时间。实现必须在保存聚合的同一事务中持久化事件；
 * 序列化或追加失败必须向上抛出并回滚业务事务，不能直接进行无法参与事务的外部发布，也不能 fail-open。
 * </p>
 *
 * <p>该端口不承诺以聚合和事件类型自动去重；同一方法被重复调用可能生成不同事件 ID。调用方必须只在
 * 对应持久化操作成功后调用一次，消费方则在公开事件 ID 边界实现幂等。</p>
 */
public interface ShortLinkEventPublisher {

    /**
     * 追加短链创建事件。
     *
     * @param link 非空的创建后完整聚合快照
     * @param occurredAtUtc 非空的 UTC 发生时间
     */
    void created(ShortLink link, Instant occurredAtUtc);

    /**
     * 追加短链更新事件。
     *
     * @param link 非空的更新后完整聚合快照
     * @param occurredAtUtc 非空 UTC 时间
     */
    void updated(ShortLink link, Instant occurredAtUtc);

    /**
     * 追加短链归档事件。
     *
     * @param link 非空的归档后完整聚合快照
     * @param occurredAtUtc 非空的归档操作 UTC 时间
     */
    void archived(ShortLink link, Instant occurredAtUtc);

    /**
     * 追加短链恢复事件。
     *
     * @param link 非空的恢复后完整聚合快照
     * @param occurredAtUtc 非空的 UTC 发生时间
     */
    void restored(ShortLink link, Instant occurredAtUtc);

    /**
     * 追加短链删除事件；调用时聚合快照仍可用于构造删除事件载荷。
     *
     * @param link 非空的删除前最终聚合快照
     * @param occurredAtUtc 非空的删除操作 UTC 时间
     */
    void deleted(ShortLink link, Instant occurredAtUtc);
}

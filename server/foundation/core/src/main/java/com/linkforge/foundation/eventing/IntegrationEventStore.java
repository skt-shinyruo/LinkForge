package com.linkforge.foundation.eventing;

import java.time.Instant;
import java.util.List;

/**
 * 单体内 append-only 集成事件日志的存取契约。
 *
 * <p>递增 {@code seq} 是消费游标而不是 exactly-once 保证；消费者处理完成但 checkpoint 提交前
 * 失败会重放，必须依赖 eventId、唯一键或幂等 upsert。</p>
 */
public interface IntegrationEventStore {

    /** 返回当前最大序号；空表由持久化实现返回 0。该值仅适合估计追赶边界，不能替代消费确认。 */
    long loadMaxSeq();

    /**
     * 按 {@code (lastSeqExclusive,+∞)} 有界读取所有 producer 的事件。
     *
     * <p>结果必须按 seq 升序，供调用方在成功处理后推进 checkpoint；{@code limit} 由调用方提供有限正值。</p>
     */
    List<IntegrationEventRow> listAfterSeq(long lastSeqExclusive, int limit);

    /** 按 producer 和排他游标有界读取；返回按 seq 升序，允许单调推进 checkpoint。 */
    List<IntegrationEventRow> listAfterSeqByProducer(String producer, long lastSeqExclusive, int limit);

    /**
     * 追加一条事件；{@code occurredAtUtc} 必须是 UTC instant，payloadJson 必须与 eventType 版本匹配。
     * 调用方在业务事务中使用时，插入与业务数据一起提交或回滚。
     *
     * <p>当前持久化 schema 对 eventId 有唯一约束；同一 ID 的重复追加会以重复键失败，生产者重试应使用稳定
     * ID 并按自身事务策略处理该结果。不同 eventId 的重复业务事件以及消费者在 checkpoint 前失败的重放仍
     * 可能发生，消费者仍必须保持幂等。</p>
     */
    void append(
            String eventId,
            String producer,
            String eventType,
            Long tenantId,
            String aggregateType,
            Long aggregateId,
            Instant occurredAtUtc,
            String payloadJson
    );
}

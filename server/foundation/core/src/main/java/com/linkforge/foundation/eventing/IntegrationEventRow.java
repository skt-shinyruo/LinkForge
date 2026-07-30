package com.linkforge.foundation.eventing;

import java.time.Instant;

/**
 * append-only 集成事件表的一行不可变快照。
 *
 * <p>{@code seq} 是全局递增消费游标，{@code eventId} 用于下游幂等去重，{@code occurredAtUtc} 必须为 UTC
 * instant。租户和聚合字段可为空以表达全局事件；{@code payloadJson} 是由 {@code eventType} 定义版本的
 * wire payload，消费者不得仅凭 Java 类名解析。</p>
 */
public record IntegrationEventRow(
        long seq,
        String eventId,
        String producer,
        String eventType,
        Long tenantId,
        String aggregateType,
        Long aggregateId,
        Instant occurredAtUtc,
        String payloadJson
) {
}

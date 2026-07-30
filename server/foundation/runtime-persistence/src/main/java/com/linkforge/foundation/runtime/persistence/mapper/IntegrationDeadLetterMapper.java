package com.linkforge.foundation.runtime.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * integration_consumer_dead_letter 表的失败诊断 upsert 映射。
 *
 * <p>重复键只更新 attempts 和 lastError，保留首次写入的事件快照；该 SQL 不提供自动重放能力。</p>
 */
@Mapper
public interface IntegrationDeadLetterMapper {

    /** 按 {@code (consumer, seq)} 插入或更新一次失败诊断。 */
    int upsertFailure(
            @Param("consumer") String consumer,
            @Param("seq") long seq,
            @Param("eventId") String eventId,
            @Param("producer") String producer,
            @Param("eventType") String eventType,
            @Param("occurredAtUtc") Instant occurredAtUtc,
            @Param("payloadJson") String payloadJson,
            @Param("attempts") int attempts,
            @Param("lastError") String lastError
    );
}

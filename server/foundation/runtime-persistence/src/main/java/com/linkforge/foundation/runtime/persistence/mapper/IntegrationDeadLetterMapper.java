package com.linkforge.foundation.runtime.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface IntegrationDeadLetterMapper {

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


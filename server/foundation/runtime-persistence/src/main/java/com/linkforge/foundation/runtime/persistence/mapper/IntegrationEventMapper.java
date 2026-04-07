package com.linkforge.foundation.runtime.persistence.mapper;

import com.linkforge.foundation.eventing.IntegrationEventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface IntegrationEventMapper {

    long loadMaxSeq();

    List<IntegrationEventRow> listAfterSeq(
            @Param("lastSeqExclusive") long lastSeqExclusive,
            @Param("limit") int limit
    );

    int insert(
            @Param("eventId") String eventId,
            @Param("producer") String producer,
            @Param("eventType") String eventType,
            @Param("tenantId") Long tenantId,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") Long aggregateId,
            @Param("occurredAtUtc") Instant occurredAtUtc,
            @Param("payloadJson") String payloadJson
    );
}


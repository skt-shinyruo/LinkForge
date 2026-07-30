package com.linkforge.foundation.runtime.persistence.mapper;

import com.linkforge.foundation.eventing.IntegrationEventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * integration_events 表的机械 MyBatis 映射。
 *
 * <p>查询按 {@code seq ASC} 返回，供消费者使用排他游标推进 checkpoint；插入参加调用线程已有的 Spring
 * 事务，不在 mapper 层创建独立事务。</p>
 */
@Mapper
public interface IntegrationEventMapper {

    /** 空表返回 0。 */
    long loadMaxSeq();

    /** 按排他序号读取所有 producer 的有限批次，结果按 seq 升序。 */
    List<IntegrationEventRow> listAfterSeq(
            @Param("lastSeqExclusive") long lastSeqExclusive,
            @Param("limit") int limit
    );

    /** 按 producer 和排他序号读取有限批次，结果按 seq 升序。 */
    List<IntegrationEventRow> listAfterSeqByProducer(
            @Param("producer") String producer,
            @Param("lastSeqExclusive") long lastSeqExclusive,
            @Param("limit") int limit
    );

    /**
     * 追加事件行；表的 eventId 唯一键会拒绝同 ID 的重复插入，mapper 不将重复键转换为成功。
     * 消费者仍须处理不同 ID 的重复业务事件与 checkpoint 前重放。
     */
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

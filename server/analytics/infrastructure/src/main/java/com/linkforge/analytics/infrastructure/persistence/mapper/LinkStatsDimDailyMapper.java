package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 链接维度日聚合的批量 upsert mapper。
 *
 * <p>每行是一个维度值的当前 PV/近似 UV 快照，不是增量；SQL 的单调覆盖使 dirty stream 的重复交付安全。</p>
 */
@Mapper
public interface LinkStatsDimDailyMapper {

    /** 写入一个或多个维度值的 UTC 日快照。 */
    int batchUpsert(List<LinkStatsDimDailyUpsertRow> rows);
}

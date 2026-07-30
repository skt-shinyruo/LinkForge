package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 链接日 PV/UV 快照的批量 upsert mapper。
 *
 * <p>SQL 使用 {@code GREATEST} 保留最大快照值，允许 dirty stream 重放和同日多次刷新；它不累加入参，
 * 因而调用方必须传入 Redis 中读取到的累计值。</p>
 */
@Mapper
public interface LinkStatsDailyMapper {

    /** 持久化同一 UTC 日期的链接聚合快照。 */
    int batchUpsert(List<LinkStatsDailyUpsertRow> rows);
}

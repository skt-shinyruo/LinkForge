package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 租户、应用、域范围 UV 日快照的批量 upsert mapper。
 *
 * <p>范围 UV 来自独立 HLL，而不是把链接 UV 相加；SQL 同样以最大值覆盖，适合重复 flush。</p>
 */
@Mapper
public interface AnalyticsScopeStatsDailyMapper {

    /** 写入范围成员在一个 UTC 日期中的近似 UV 快照。 */
    int batchUpsert(List<AnalyticsScopeStatsDailyUpsertRow> rows);
}

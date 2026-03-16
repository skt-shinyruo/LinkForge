package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LinkStatsDimDailyMapper {

    int batchUpsert(List<LinkStatsDimDailyUpsertRow> rows);
}


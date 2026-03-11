package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LinkVisitEventMapper {

    int batchInsertIgnore(List<LinkVisitEventInsertRow> rows);

    int deleteOld(int retentionDays);
}


package com.linkforge.shortlink.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LinkCacheOutboxMapper {

    int enqueueRefresh(String code);

    List<LinkCacheOutboxPendingRow> listPending(int limit);

    int markDone(String code);

    int markRetry(String code, int attempts, String lastError, long delaySeconds);

    int deleteDoneOlderThanDays(int retentionDays, int limit);

    LinkCacheOutboxStatsRow loadStats();

    String findStatusByCode(String code);
}


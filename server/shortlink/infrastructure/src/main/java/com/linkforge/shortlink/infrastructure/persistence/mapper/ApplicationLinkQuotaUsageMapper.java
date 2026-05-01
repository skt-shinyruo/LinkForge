package com.linkforge.shortlink.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface ApplicationLinkQuotaUsageMapper {

    Integer acquireMonthlyLinkUsageLock(@Param("lockName") String lockName, @Param("timeoutSeconds") int timeoutSeconds);

    Integer releaseMonthlyLinkUsageLock(@Param("lockName") String lockName);

    int ensureMonthlyLinkUsage(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("monthStartUtc") LocalDate monthStartUtc,
            @Param("fromInclusiveUtc") LocalDateTime fromInclusiveUtc,
            @Param("toExclusiveUtc") LocalDateTime toExclusiveUtc
    );

    int incrementMonthlyLinkUsage(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("monthStartUtc") LocalDate monthStartUtc,
            @Param("monthlyLinkLimit") long monthlyLinkLimit
    );
}

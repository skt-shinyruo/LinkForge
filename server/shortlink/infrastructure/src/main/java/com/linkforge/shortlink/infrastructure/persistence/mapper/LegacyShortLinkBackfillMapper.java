package com.linkforge.shortlink.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** durable legacy ownership backfill store 使用的 MyBatis 语句。 */
@Mapper
public interface LegacyShortLinkBackfillMapper {

    int ensureCheckpoint(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("domainId") long domainId
    );

    CheckpointRow lockCheckpoint(@Param("tenantId") long tenantId);

    List<Long> findUntrackedLegacyLinkIds(
            @Param("tenantId") long tenantId,
            @Param("afterLinkId") long afterLinkId,
            @Param("limit") int limit
    );

    int insertWorkItems(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("domainId") long domainId,
            @Param("linkIds") List<Long> linkIds
    );

    int updateCheckpoint(
            @Param("tenantId") long tenantId,
            @Param("lastScannedLinkId") long lastScannedLinkId,
            @Param("scanExhausted") boolean scanExhausted
    );

    List<WorkItemRow> findActionable(@Param("tenantId") long tenantId, @Param("limit") int limit);

    int recordOutcome(
            @Param("tenantId") long tenantId,
            @Param("linkId") long linkId,
            @Param("status") String status,
            @Param("lastError") String lastError
    );

    ProgressRow progress(@Param("tenantId") long tenantId);

    record CheckpointRow(
            long tenantId,
            long applicationId,
            long domainId,
            long lastScannedLinkId,
            boolean scanExhausted
    ) {
    }

    record WorkItemRow(long tenantId, long linkId, long applicationId, long domainId) {
    }

    record ProgressRow(
            long lastScannedLinkId,
            boolean scanExhausted,
            long pendingCount,
            long retryableCount,
            long permanentFailureCount,
            long reconciledCount,
            long alreadyReconciledCount,
            long notFoundCount
    ) {
    }
}

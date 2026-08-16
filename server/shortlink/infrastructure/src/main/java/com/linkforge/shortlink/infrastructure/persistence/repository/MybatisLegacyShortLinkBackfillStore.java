package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.migration.LegacyShortLinkBackfillProgress;
import com.linkforge.shortlink.application.port.LegacyShortLinkBackfillStore;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LegacyShortLinkBackfillMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 有界 legacy ownership reconciliation 的 MySQL checkpoint/work-item 实现。 */
@Repository
public class MybatisLegacyShortLinkBackfillStore implements LegacyShortLinkBackfillStore {

    private final LegacyShortLinkBackfillMapper mapper;

    public MybatisLegacyShortLinkBackfillStore(LegacyShortLinkBackfillMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public List<WorkItem> takeBatch(long tenantId, long applicationId, long domainId, int limit) {
        if (tenantId <= 0 || applicationId <= 0 || domainId <= 0 || limit <= 0) {
            throw new IllegalArgumentException("legacy backfill batch arguments are invalid");
        }
        mapper.ensureCheckpoint(tenantId, applicationId, domainId);
        LegacyShortLinkBackfillMapper.CheckpointRow checkpoint = mapper.lockCheckpoint(tenantId);
        if (checkpoint == null) {
            throw new IllegalStateException("legacy backfill checkpoint was not created");
        }
        if (checkpoint.applicationId() != applicationId || checkpoint.domainId() != domainId) {
            throw new IllegalStateException("legacy backfill target binding changed after checkpoint creation");
        }

        List<LegacyShortLinkBackfillMapper.WorkItemRow> actionable = mapper.findActionable(tenantId, limit);
        // 持续重试即使占满返回批次，也不能阻止 keyset cursor 发现后续链接。
        int discoveryCapacity = Math.max(limit - actionable.size(), 1);
        discover(checkpoint, discoveryCapacity);
        actionable = mapper.findActionable(tenantId, limit);
        return actionable.stream()
                .map(row -> new WorkItem(row.tenantId(), row.linkId(), row.applicationId(), row.domainId()))
                .toList();
    }

    @Override
    @Transactional
    public void recordOutcome(long tenantId, long linkId, Outcome outcome, String detail) {
        if (outcome == null) {
            throw new IllegalArgumentException("legacy backfill outcome is required");
        }
        int changed = mapper.recordOutcome(tenantId, linkId, outcome.name(), truncate(detail));
        if (changed != 1) {
            throw new IllegalStateException("legacy backfill work item no longer exists");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyShortLinkBackfillProgress progress(long tenantId) {
        LegacyShortLinkBackfillMapper.ProgressRow row = mapper.progress(tenantId);
        if (row == null) {
            return new LegacyShortLinkBackfillProgress(0L, false, 0L, 0L, 0L, 0L, 0L, 0L);
        }
        return new LegacyShortLinkBackfillProgress(
                row.lastScannedLinkId(),
                row.scanExhausted(),
                row.pendingCount(),
                row.retryableCount(),
                row.permanentFailureCount(),
                row.reconciledCount(),
                row.alreadyReconciledCount(),
                row.notFoundCount()
        );
    }

    private void discover(LegacyShortLinkBackfillMapper.CheckpointRow checkpoint, int capacity) {
        List<Long> discovered = new ArrayList<>(mapper.findUntrackedLegacyLinkIds(
                checkpoint.tenantId(),
                checkpoint.lastScannedLinkId(),
                capacity
        ));
        long nextCursor = checkpoint.lastScannedLinkId();
        if (!discovered.isEmpty()) {
            mapper.insertWorkItems(
                    checkpoint.tenantId(),
                    checkpoint.applicationId(),
                    checkpoint.domainId(),
                    discovered
            );
            nextCursor = discovered.get(discovered.size() - 1);
        }

        int remaining = capacity - discovered.size();
        boolean exhausted = false;
        if (remaining > 0) {
            List<Long> wrapped = mapper.findUntrackedLegacyLinkIds(checkpoint.tenantId(), 0L, remaining);
            if (!wrapped.isEmpty()) {
                mapper.insertWorkItems(
                        checkpoint.tenantId(),
                        checkpoint.applicationId(),
                        checkpoint.domainId(),
                        wrapped
                );
                nextCursor = wrapped.get(wrapped.size() - 1);
            }
            exhausted = wrapped.size() < remaining;
        }
        mapper.updateCheckpoint(checkpoint.tenantId(), nextCursor, exhausted);
    }

    private static String truncate(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String normalized = detail.trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }
}

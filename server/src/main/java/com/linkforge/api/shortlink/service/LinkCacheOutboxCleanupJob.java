package com.linkforge.api.shortlink.service;

import com.linkforge.api.shortlink.cache.LinkCacheOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LinkCacheOutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LinkCacheOutboxCleanupJob.class);

    private final LinkCacheOutboxRepository outbox;
    private final Counter deletedCounter;
    private final int retentionDays;
    private final int batchSize;
    private final int maxBatches;

    public LinkCacheOutboxCleanupJob(
            LinkCacheOutboxRepository outbox,
            MeterRegistry meterRegistry,
            @Value("${APP_LINK_CACHE_OUTBOX_DONE_RETENTION_DAYS:7}") int retentionDays,
            @Value("${APP_LINK_CACHE_OUTBOX_CLEANUP_BATCH_SIZE:1000}") int batchSize,
            @Value("${APP_LINK_CACHE_OUTBOX_CLEANUP_MAX_BATCHES:20}") int maxBatches
    ) {
        this.outbox = outbox;
        this.deletedCounter = Counter.builder("linkforge.shortlink.cache_outbox.cleanup.deleted")
                .description("link_cache_outbox cleanup deleted rows")
                .register(meterRegistry);
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
    }

    @Scheduled(fixedDelayString = "${APP_LINK_CACHE_OUTBOX_CLEANUP_DELAY_MS:3600000}") // 1h
    @SchedulerLock(name = "lf:job:link-cache-outbox:cleanup", lockAtMostFor = "PT30M")
    public void cleanup() {
        int days = normalizeRetentionDays(retentionDays);
        if (days <= 0) {
            return;
        }
        int batch = normalizeBatchSize(batchSize);
        int loops = normalizeMaxBatches(maxBatches);

        long totalDeleted = 0;
        try {
            for (int i = 0; i < loops; i++) {
                int deleted = outbox.deleteDoneOlderThanDays(days, batch);
                if (deleted <= 0) {
                    break;
                }
                totalDeleted += deleted;
                deletedCounter.increment(deleted);
                if (deleted < batch) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("link cache outbox cleanup failed: retentionDays={}, err={}", days, e.getMessage());
            return;
        }

        if (totalDeleted > 0) {
            log.info("link cache outbox cleanup ok: deleted={}, retentionDays={}", totalDeleted, days);
        }
    }

    private static int normalizeRetentionDays(int raw) {
        if (raw <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(raw, 3650));
    }

    private static int normalizeBatchSize(int raw) {
        if (raw <= 0) {
            return 1000;
        }
        return Math.max(1, Math.min(raw, 50_000));
    }

    private static int normalizeMaxBatches(int raw) {
        if (raw <= 0) {
            return 20;
        }
        return Math.max(1, Math.min(raw, 200));
    }
}


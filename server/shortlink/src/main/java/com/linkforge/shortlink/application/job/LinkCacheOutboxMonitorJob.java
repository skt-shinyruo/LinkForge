package com.linkforge.shortlink.application.job;

import com.linkforge.shortlink.infrastructure.outbox.LinkCacheOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class LinkCacheOutboxMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(LinkCacheOutboxMonitorJob.class);

    private final LinkCacheOutboxRepository outbox;
    private final AtomicLong pendingTotal = new AtomicLong(0);
    private final AtomicLong pendingReady = new AtomicLong(0);
    private final AtomicLong pendingLagSeconds = new AtomicLong(0);
    private final long warnPendingTotal;
    private final long warnPendingReady;
    private final long warnLagSeconds;
    private final long warnMinIntervalMs;
    private volatile long lastWarnAtMs = 0;
    private volatile long lastStatsErrorWarnAtMs = 0;

    public LinkCacheOutboxMonitorJob(
            LinkCacheOutboxRepository outbox,
            MeterRegistry meterRegistry,
            @Value("${APP_LINK_CACHE_OUTBOX_PENDING_WARN_THRESHOLD:5000}") long warnPendingTotal,
            @Value("${APP_LINK_CACHE_OUTBOX_READY_WARN_THRESHOLD:1000}") long warnPendingReady,
            @Value("${APP_LINK_CACHE_OUTBOX_LAG_WARN_SECONDS:60}") long warnLagSeconds,
            @Value("${APP_LINK_CACHE_OUTBOX_WARN_MIN_INTERVAL_SECONDS:60}") long warnMinIntervalSeconds
    ) {
        this.outbox = outbox;
        this.warnPendingTotal = Math.max(warnPendingTotal, 0);
        this.warnPendingReady = Math.max(warnPendingReady, 0);
        this.warnLagSeconds = Math.max(warnLagSeconds, 0);
        this.warnMinIntervalMs = Math.max(warnMinIntervalSeconds, 5) * 1000L;

        Gauge.builder("linkforge.shortlink.cache_outbox.pending.total", pendingTotal, AtomicLong::get)
                .description("link_cache_outbox pending rows (total)")
                .register(meterRegistry);
        Gauge.builder("linkforge.shortlink.cache_outbox.pending.ready", pendingReady, AtomicLong::get)
                .description("link_cache_outbox pending rows ready to process (available_at <= now)")
                .register(meterRegistry);
        Gauge.builder("linkforge.shortlink.cache_outbox.pending.lag.seconds", pendingLagSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .description("lag seconds since oldest available_at among pending rows (clamped to >=0)")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${APP_LINK_CACHE_OUTBOX_MONITOR_DELAY_MS:10000}") // 10s
    @SchedulerLock(name = "lf:job:link-cache-outbox:monitor", lockAtMostFor = "PT2M")
    public void monitor() {
        LinkCacheOutboxRepository.OutboxStats stats;
        try {
            stats = outbox.loadStats();
        } catch (Exception e) {
            warnStatsQueryFailed(e);
            return;
        }
        if (stats == null) {
            return;
        }

        pendingTotal.set(Math.max(stats.pendingTotal(), 0));
        pendingReady.set(Math.max(stats.pendingReady(), 0));
        pendingLagSeconds.set(Math.max(stats.pendingLagSeconds(), 0));

        maybeWarnBacklog();
    }

    private void warnStatsQueryFailed(Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastStatsErrorWarnAtMs < warnMinIntervalMs) {
            return;
        }
        lastStatsErrorWarnAtMs = now;
        log.warn("link cache outbox monitor query failed: err={}", e == null ? null : e.getMessage());
    }

    private void maybeWarnBacklog() {
        boolean exceed = (warnPendingTotal > 0 && pendingTotal.get() >= warnPendingTotal)
                || (warnPendingReady > 0 && pendingReady.get() >= warnPendingReady)
                || (warnLagSeconds > 0 && pendingLagSeconds.get() >= warnLagSeconds);
        if (!exceed) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastWarnAtMs < warnMinIntervalMs) {
            return;
        }
        lastWarnAtMs = now;

        log.warn(
                "link cache outbox backlog: pendingTotal={}, pendingReady={}, lagSeconds={}, thresholds(total={}, ready={}, lagSeconds={})",
                pendingTotal.get(),
                pendingReady.get(),
                pendingLagSeconds.get(),
                warnPendingTotal,
                warnPendingReady,
                warnLagSeconds
        );
    }
}

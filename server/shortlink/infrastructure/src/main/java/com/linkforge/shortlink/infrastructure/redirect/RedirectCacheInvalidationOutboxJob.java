package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class RedirectCacheInvalidationOutboxJob {

    static final int BATCH_LIMIT = 100;
    private static final int MAX_BATCHES = 10;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(RedirectCacheInvalidationOutboxJob.class);

    private final RedirectCacheInvalidationOutboxRepository outbox;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final Clock clock;

    public RedirectCacheInvalidationOutboxJob(
            RedirectCacheInvalidationOutboxRepository outbox,
            RedirectCacheSyncPort redirectCacheSync,
            Clock clock
    ) {
        this.outbox = outbox;
        this.redirectCacheSync = redirectCacheSync;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${APP_SHORTLINK_REDIRECT_CACHE_INVALIDATION_OUTBOX_DELAY_MS:1000}")
    @SchedulerLock(name = "lf:job:shortlink:redirect-cache-invalidation-outbox", lockAtMostFor = "PT2M")
    public void process() {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int n = processOnce();
            total += n;
            if (n <= 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("redirect cache invalidation outbox processed: count={}", total);
        }
    }

    int processOnce() {
        LocalDateTime nowUtc = nowUtc();
        List<RedirectCacheInvalidationOutboxRow> rows = outbox.listDue(nowUtc, BATCH_LIMIT);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int handled = 0;
        for (RedirectCacheInvalidationOutboxRow row : rows) {
            if (row == null) {
                continue;
            }
            try {
                redirectCacheSync.evict(row.tenantId(), row.domainId(), row.code());
                outbox.markProcessed(row.id(), nowUtc());
            } catch (RuntimeException ex) {
                int attempts = row.attempts() + 1;
                outbox.markFailed(
                        row.id(),
                        attempts,
                        truncate(errorMessage(ex), MAX_ERROR_LENGTH),
                        nextAttemptAt(attempts)
                );
            }
            handled++;
        }
        return handled;
    }

    private LocalDateTime nextAttemptAt(int attempts) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 1L << Math.min(Math.max(attempts - 1, 0), 8));
        return nowUtc().plusSeconds(seconds);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String errorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getName() : message;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

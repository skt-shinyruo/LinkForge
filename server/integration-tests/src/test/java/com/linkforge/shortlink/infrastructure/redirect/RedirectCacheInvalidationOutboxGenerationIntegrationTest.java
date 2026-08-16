package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.LinkForgeApplication;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.infrastructure.persistence.mapper.RedirectCacheInvalidationOutboxMapper;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class RedirectCacheInvalidationOutboxGenerationIntegrationTest extends SharedIntegrationTestSupport {

    private static final long TENANT_ID = 7001L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.analytics.dimensions.enabled", () -> "false");
        registry.add("app.analytics.events.enabled", () -> "false");
    }

    @Autowired
    RedirectCacheInvalidationOutboxRepository outbox;

    @Autowired
    RedirectCacheInvalidationOutboxMapper outboxMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RedirectCacheSyncPort cacheSync;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.update("DELETE FROM redirect_cache_invalidation_outbox");
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void concurrentEnqueue_shouldAdvanceOneGenerationPerCommittedIntent() throws Exception {
        String code = uniqueCode("concurrent");
        outbox.enqueue(TENANT_ID, null, code);
        int concurrentEnqueues = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentEnqueues);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrentEnqueues; i++) {
                futures.add(executor.submit(() -> {
                    await(start);
                    new TransactionTemplate(transactionManager).executeWithoutResult(
                            ignored -> outbox.enqueue(TENANT_ID, null, code)
                    );
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        RedirectCacheInvalidationOutboxRow row = onlyDueRow();
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.generation()).isEqualTo(concurrentEnqueues + 1L);
        assertThat(row.attempts()).isZero();
    }

    @Test
    void additiveMigration_shouldKeepOldWriterAndExistingRowSemantics() {
        String code = uniqueCode("legacy");
        jdbcTemplate.update("""
                INSERT INTO redirect_cache_invalidation_outbox (
                    tenant_id, domain_id, domain_scope, code, status, attempts,
                    last_error, next_attempt_at, processed_at
                ) VALUES (?, NULL, 0, ?, 'PENDING', 0, NULL, UTC_TIMESTAMP(), NULL)
                """, TENANT_ID, code);

        RedirectCacheInvalidationOutboxRow row = onlyDueRow();
        assertThat(row.generation()).isEqualTo(1L);
        assertThat(row.status()).isEqualTo("PENDING");

        outbox.enqueue(TENANT_ID, null, code);
        assertThat(onlyDueRow().generation()).isEqualTo(2L);
    }

    @Test
    void staleWorkerCompletion_shouldNotConsumeNewGeneration() throws Exception {
        String code = uniqueCode("race");
        String cacheKey = "link:code:" + code;
        // 两代记录都保持为未来到期，避免其他 Spring 上下文的残留 worker 抢先消费测试数据。
        LocalDateTime firstDueAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        jdbcTemplate.update("""
                INSERT INTO redirect_cache_invalidation_outbox (
                    tenant_id, domain_id, domain_scope, code, status, generation,
                    attempts, last_error, next_attempt_at, processed_at
                ) VALUES (?, NULL, 0, ?, 'PENDING', 1, 0, NULL, ?, NULL)
                """, TENANT_ID, code, firstDueAt);
        RedirectCacheInvalidationOutboxRow firstClaim = onlyDueRowAt(firstDueAt.plusMinutes(1));
        redis.opsForValue().set(cacheKey, "old-cache");

        CountDownLatch firstEvictionFinished = new CountDownLatch(1);
        CountDownLatch allowOldWorkerToComplete = new CountDownLatch(1);
        RedirectCacheSyncPort blockingCacheSync = (tenantId, domainId, shortCode) -> {
            redis.delete(cacheKey);
            firstEvictionFinished.countDown();
            await(allowOldWorkerToComplete);
        };
        RedirectCacheInvalidationOutboxJob blockedWorker = new RedirectCacheInvalidationOutboxJob(
                outbox,
                blockingCacheSync,
                Clock.fixed(firstDueAt.plusMinutes(1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
        RedirectCacheInvalidationOutboxRepository futureOutbox = new RedirectCacheInvalidationOutboxRepository(
                outboxMapper,
                Clock.fixed(firstDueAt.plusHours(1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> oldWorker = executor.submit(blockedWorker::processOnce);
            awaitWorkerGate(firstEvictionFinished, oldWorker);

            futureOutbox.enqueue(TENANT_ID, null, code);
            redis.opsForValue().set(cacheKey, "newer-stale-cache");
            allowOldWorkerToComplete.countDown();
            assertThat(oldWorker.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            allowOldWorkerToComplete.countDown();
            executor.shutdownNow();
        }

        RedirectCacheInvalidationOutboxRow reopened = onlyDueRowAt(firstDueAt.plusHours(2));
        assertThat(reopened.id()).isEqualTo(firstClaim.id());
        assertThat(reopened.generation()).isEqualTo(firstClaim.generation() + 1L);
        assertThat(reopened.status()).isEqualTo("PENDING");
        assertThat(redis.opsForValue().get(cacheKey)).isEqualTo("newer-stale-cache");

        RedirectCacheInvalidationOutboxJob nextWorker = new RedirectCacheInvalidationOutboxJob(
                outbox,
                cacheSync,
                Clock.fixed(firstDueAt.plusHours(2).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
        assertThat(nextWorker.processOnce()).isEqualTo(1);
        assertThat(redis.opsForValue().get(cacheKey)).isNull();
        assertThat(status(firstClaim.id())).isEqualTo("PROCESSED");
    }

    @Test
    void staleWorkerFailure_shouldNotDelayNewGeneration() {
        String code = uniqueCode("failure");
        outbox.enqueue(TENANT_ID, null, code);
        RedirectCacheInvalidationOutboxRow firstClaim = onlyDueRow();
        outbox.enqueue(TENANT_ID, null, code);

        boolean updated = outbox.markFailed(
                firstClaim.id(),
                firstClaim.generation(),
                firstClaim.attempts() + 1,
                "old failure",
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)
        );

        assertThat(updated).isFalse();
        RedirectCacheInvalidationOutboxRow current = onlyDueRow();
        assertThat(current.generation()).isEqualTo(firstClaim.generation() + 1L);
        assertThat(current.attempts()).isZero();
        assertThat(current.status()).isEqualTo("PENDING");
    }

    private RedirectCacheInvalidationOutboxRow onlyDueRow() {
        return onlyDueRowAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
    }

    private RedirectCacheInvalidationOutboxRow onlyDueRowAt(LocalDateTime dueAt) {
        List<RedirectCacheInvalidationOutboxRow> rows = outbox.listDue(dueAt, 10);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private String status(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM redirect_cache_invalidation_outbox WHERE id = ?",
                String.class,
                id
        );
    }

    private static String uniqueCode(String prefix) {
        return prefix + Long.toUnsignedString(System.nanoTime(), 36);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for test gate", ex);
        }
    }

    private static void awaitWorkerGate(CountDownLatch latch, Future<Integer> worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!latch.await(100, TimeUnit.MILLISECONDS)) {
            if (worker.isDone()) {
                assertThat(worker.get(1, TimeUnit.SECONDS))
                        .as("old worker exited before reaching the eviction gate")
                        .isEqualTo(1);
                throw new AssertionError("old worker handled a row without reaching the eviction gate");
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("old worker did not reach the eviction gate within 30 seconds");
            }
        }
    }
}

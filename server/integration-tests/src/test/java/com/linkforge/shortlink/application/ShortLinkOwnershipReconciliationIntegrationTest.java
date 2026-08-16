package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.shortlink.application.migration.ShortLinkOwnershipReconciliationResult;
import com.linkforge.shortlink.application.migration.ShortLinkOwnershipReconciliationService;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkOwnershipReconciliationIntegrationTest extends ApplicationAwareShortLinkIntegrationTestSupport {

    private static final long TENANT_ID = 73_001L;
    private static final long APPLICATION_ID = 73_101L;
    private static final long DOMAIN_ID = 73_201L;
    private static final long LINK_ID = 73_301L;
    private static final String HOSTNAME = "legacy-reconcile.example.test";
    private static final String CODE = "legacyReconcile";

    @Autowired
    ShortLinkOwnershipReconciliationService reconciliationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redis;

    @SpyBean
    ShortLinkRepository shortLinkRepository;

    @SpyBean
    RedirectCacheInvalidationOutboxPort cacheInvalidationOutbox;

    @BeforeEach
    void setUpLegacyLinkAndTargetScope() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        jdbcTemplate.update(
                "INSERT INTO applications (id, tenant_id, application_key, display_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                APPLICATION_ID,
                TENANT_ID,
                "legacy-reconcile",
                "Legacy Reconcile"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO domains (id, tenant_id, application_id, hostname, scope, status, trust_class)
                        VALUES (?, ?, ?, ?, 'APPLICATION_DEDICATED', 'ACTIVE', 'FIRST_PARTY')
                        """,
                DOMAIN_ID,
                TENANT_ID,
                APPLICATION_ID,
                HOSTNAME
        );
        jdbcTemplate.update(
                "INSERT INTO application_quotas (application_id, monthly_link_limit, monthly_click_limit) VALUES (?, 10000, 1000000)",
                APPLICATION_ID
        );
        insertLegacyLink(LINK_ID, CODE);
    }

    @Test
    void reconcile_shouldAtomicallyMoveOwnershipAndRemainIdempotent() {
        String unscopedKey = "link:code:" + CODE;
        String scopedKey = "link:host:" + HOSTNAME + ":code:" + CODE;
        redis.opsForValue().set(unscopedKey, "stale-unscoped");
        redis.opsForValue().set(scopedKey, "stale-scoped");

        ShortLinkOwnershipReconciliationResult first = reconciliationService.reconcile(
                TENANT_ID,
                LINK_ID,
                APPLICATION_ID,
                DOMAIN_ID
        );
        ShortLinkOwnershipReconciliationResult repeated = reconciliationService.reconcile(
                TENANT_ID,
                LINK_ID,
                APPLICATION_ID,
                DOMAIN_ID
        );

        assertThat(first.status()).isEqualTo(ShortLinkOwnershipReconciliationResult.Status.RECONCILED);
        assertThat(repeated.status()).isEqualTo(ShortLinkOwnershipReconciliationResult.Status.ALREADY_RECONCILED);
        assertThat(linkState()).isEqualTo(new LinkState(APPLICATION_ID, DOMAIN_ID, 1L));
        assertThat(monthlyUsage()).isEqualTo(1L);
        assertThat(integrationEventCount()).isEqualTo(1);
        assertThat(outboxRows()).containsExactlyInAnyOrder(
                new OutboxRow(0L, "PENDING", 1L),
                new OutboxRow(DOMAIN_ID, "PENDING", 1L)
        );
        assertThat(redis.opsForValue().get(unscopedKey)).isNull();
        assertThat(redis.opsForValue().get(scopedKey)).isNull();
    }

    @Test
    void concurrentReconciliation_shouldAllowOnlyOneCasAndOneSetOfSideEffects() throws Exception {
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        AtomicInteger gatedReads = new AtomicInteger();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<com.linkforge.shortlink.domain.ShortLink> loaded =
                    (Optional<com.linkforge.shortlink.domain.ShortLink>) invocation.callRealMethod();
            if (gatedReads.incrementAndGet() <= 2) {
                bothLoaded.await(10, TimeUnit.SECONDS);
            }
            return loaded;
        }).when(shortLinkRepository).findByTenantIdAndId(anyLong(), anyLong());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ShortLinkOwnershipReconciliationResult> first = executor.submit(this::reconcileTarget);
            Future<ShortLinkOwnershipReconciliationResult> second = executor.submit(this::reconcileTarget);

            List<ShortLinkOwnershipReconciliationResult.Status> statuses = List.of(
                    first.get(15, TimeUnit.SECONDS).status(),
                    second.get(15, TimeUnit.SECONDS).status()
            );

            assertThat(statuses).containsExactlyInAnyOrder(
                    ShortLinkOwnershipReconciliationResult.Status.RECONCILED,
                    ShortLinkOwnershipReconciliationResult.Status.RETRYABLE_CONFLICT
            );
            assertThat(reconcileTarget().status())
                    .isEqualTo(ShortLinkOwnershipReconciliationResult.Status.ALREADY_RECONCILED);
            assertThat(linkState()).isEqualTo(new LinkState(APPLICATION_ID, DOMAIN_ID, 1L));
            assertThat(monthlyUsage()).isEqualTo(1L);
            assertThat(integrationEventCount()).isEqualTo(1);
            assertThat(outboxRows()).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void durableOutboxFailure_shouldRollbackOwnershipQuotaAndEventWithoutEvictingCache() {
        String unscopedKey = "link:code:" + CODE;
        String scopedKey = "link:host:" + HOSTNAME + ":code:" + CODE;
        redis.opsForValue().set(unscopedKey, "stale-unscoped");
        redis.opsForValue().set(scopedKey, "stale-scoped");
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(cacheInvalidationOutbox)
                .enqueue(TENANT_ID, DOMAIN_ID, CODE);

        assertThatThrownBy(this::reconcileTarget)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");

        assertThat(linkState()).isEqualTo(new LinkState(0L, 0L, 0L));
        assertThat(monthlyUsageRowCount()).isZero();
        assertThat(integrationEventCount()).isZero();
        assertThat(outboxRows()).isEmpty();
        assertThat(redis.opsForValue().get(unscopedKey)).isEqualTo("stale-unscoped");
        assertThat(redis.opsForValue().get(scopedKey)).isEqualTo("stale-scoped");
    }

    @Test
    void unauthorizedTargetScope_shouldFailBeforeOwnershipAndSideEffects() {
        assertThatThrownBy(() -> reconciliationService.reconcile(
                TENANT_ID,
                LINK_ID,
                APPLICATION_ID,
                DOMAIN_ID + 1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("域名不存在");

        assertThat(linkState()).isEqualTo(new LinkState(0L, 0L, 0L));
        assertThat(monthlyUsageRowCount()).isZero();
        assertThat(integrationEventCount()).isZero();
        assertThat(outboxRows()).isEmpty();
    }

    private ShortLinkOwnershipReconciliationResult reconcileTarget() {
        return reconciliationService.reconcile(TENANT_ID, LINK_ID, APPLICATION_ID, DOMAIN_ID);
    }

    private void insertLegacyLink(long linkId, String code) {
        jdbcTemplate.update(
                """
                        INSERT INTO short_links (
                            id, tenant_id, application_id, domain_id, code, lifecycle_state, original_url, note,
                            enabled, expires_at, archived_at, redirect_status_code, preview_enabled,
                            unavailable_landing_url, query_forward_mode, query_forward_allowlist,
                            created_by_type, created_by, version
                        ) VALUES (?, ?, NULL, NULL, ?, 'ACTIVE', ?, 'legacy', b'1', NULL, NULL, NULL,
                                  b'0', NULL, NULL, NULL, 'USER', 1001, 0)
                        """,
                linkId,
                TENANT_ID,
                code,
                "https://example.com/" + code
        );
    }

    private LinkState linkState() {
        return jdbcTemplate.queryForObject(
                "SELECT application_id, domain_id, version FROM short_links WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> new LinkState(
                        rs.getLong("application_id"),
                        rs.getLong("domain_id"),
                        rs.getLong("version")
                ),
                TENANT_ID,
                LINK_ID
        );
    }

    private long monthlyUsage() {
        Long value = jdbcTemplate.queryForObject(
                """
                        SELECT used_count
                        FROM application_link_monthly_usages
                        WHERE tenant_id = ? AND application_id = ? AND month_start = DATE_FORMAT(UTC_DATE(), '%Y-%m-01')
                        """,
                Long.class,
                TENANT_ID,
                APPLICATION_ID
        );
        return value == null ? 0L : value;
    }

    private int monthlyUsageRowCount() {
        Integer value = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM application_link_monthly_usages
                        WHERE tenant_id = ? AND application_id = ? AND month_start = DATE_FORMAT(UTC_DATE(), '%Y-%m-01')
                        """,
                Integer.class,
                TENANT_ID,
                APPLICATION_ID
        );
        return value == null ? 0 : value;
    }

    private int integrationEventCount() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_events WHERE producer = 'shortlink' AND aggregate_id = ?",
                Integer.class,
                LINK_ID
        );
        return value == null ? 0 : value;
    }

    private java.util.List<OutboxRow> outboxRows() {
        return jdbcTemplate.query(
                """
                        SELECT domain_scope, status, generation
                        FROM redirect_cache_invalidation_outbox
                        WHERE tenant_id = ? AND code = ?
                        ORDER BY domain_scope
                        """,
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("domain_scope"),
                        rs.getString("status"),
                        rs.getLong("generation")
                ),
                TENANT_ID,
                CODE
        );
    }

    private record LinkState(long applicationId, long domainId, long version) {
    }

    private record OutboxRow(long domainScope, String status, long generation) {
    }
}

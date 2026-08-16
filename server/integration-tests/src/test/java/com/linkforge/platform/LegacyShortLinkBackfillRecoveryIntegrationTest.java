package com.linkforge.platform;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.shortlink.application.migration.LegacyShortLinkBackfillBatchResult;
import com.linkforge.shortlink.application.migration.LegacyShortLinkBackfillService;
import com.linkforge.shortlink.application.migration.ShortLinkOwnershipReconciliationService;
import com.linkforge.shortlink.application.port.LegacyShortLinkBackfillStore;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class LegacyShortLinkBackfillRecoveryIntegrationTest extends PlatformPersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 82_003L;
    private static final long FIRST_LINK_ID = 92_001L;
    private static final long ALTERNATE_APPLICATION_ID = 82_101L;
    private static final long ALTERNATE_DOMAIN_ID = 82_201L;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    LegacyShortLinkBackfillService backfillService;

    @Autowired
    ShortLinkOwnershipReconciliationService reconciliationService;

    @Autowired
    LegacyApplicationProvisioningPort legacyApplicationProvisioningPort;

    @SpyBean
    LegacyShortLinkBackfillStore backfillStore;

    @SpyBean
    ShortLinkRepository shortLinkRepository;

    @BeforeEach
    void setUpTenant() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
    }

    @Test
    void batches_shouldBeBoundedAdvanceAStableCheckpointAndConvergeIdempotently() {
        LongStream.range(FIRST_LINK_ID, FIRST_LINK_ID + 5).forEach(this::insertLegacyLink);

        LegacyShortLinkBackfillBatchResult first = backfillService.reconcileNextBatch(TENANT_ID, 2);
        LegacyShortLinkBackfillBatchResult second = backfillService.reconcileNextBatch(TENANT_ID, 2);
        LegacyShortLinkBackfillBatchResult third = backfillService.reconcileNextBatch(TENANT_ID, 2);
        LegacyShortLinkBackfillBatchResult empty = backfillService.reconcileNextBatch(TENANT_ID, 2);

        assertThat(first.attemptedCount()).isEqualTo(2);
        assertThat(second.attemptedCount()).isEqualTo(2);
        assertThat(third.attemptedCount()).isEqualTo(1);
        assertThat(empty.attemptedCount()).isZero();
        assertThat(first.progress().lastScannedLinkId())
                .isLessThan(second.progress().lastScannedLinkId());
        assertThat(second.progress().lastScannedLinkId())
                .isLessThan(third.progress().lastScannedLinkId());
        assertThat(empty.progress().converged()).isTrue();
        assertThat(first.applicationId()).isEqualTo(empty.applicationId());
        assertThat(first.domainId()).isEqualTo(empty.domainId());

        assertThat(scopedLinkCount(first.applicationId(), first.domainId())).isEqualTo(5);
        assertThat(versionOneLinkCount()).isEqualTo(5);
        assertThat(integrationEventCount()).isEqualTo(5);
        assertThat(outboxCount()).isEqualTo(10);
        assertThat(monthlyUsage(first.applicationId())).isEqualTo(5L);
    }

    @Test
    void crashAfterReconciliationCommit_shouldResumePendingItemWithoutDuplicatingSideEffects() {
        insertLegacyLink(FIRST_LINK_ID);
        AtomicBoolean crashOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (crashOnce.compareAndSet(true, false)) {
                throw new SimulatedProcessCrash();
            }
            return invocation.callRealMethod();
        }).when(backfillStore).recordOutcome(anyLong(), anyLong(), any(), any());

        assertThatThrownBy(() -> backfillService.reconcileNextBatch(TENANT_ID, 1))
                .isInstanceOf(SimulatedProcessCrash.class);

        long applicationId = legacyApplicationId();
        long domainId = legacyDomainId();
        assertThat(scopedLinkCount(applicationId, domainId)).isEqualTo(1);
        assertThat(workItemStatus()).isEqualTo("PENDING");
        assertThat(integrationEventCount()).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(monthlyUsage(applicationId)).isEqualTo(1L);

        LegacyShortLinkBackfillBatchResult resumed = backfillService.reconcileNextBatch(TENANT_ID, 1);
        LegacyShortLinkBackfillBatchResult empty = backfillService.reconcileNextBatch(TENANT_ID, 1);

        assertThat(resumed.alreadyReconciledCount()).isEqualTo(1);
        assertThat(empty.attemptedCount()).isZero();
        assertThat(empty.progress().converged()).isTrue();
        assertThat(workItemStatus()).isEqualTo("ALREADY_RECONCILED");
        assertThat(versionOneLinkCount()).isEqualTo(1);
        assertThat(integrationEventCount()).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(monthlyUsage(applicationId)).isEqualTo(1L);
    }

    @Test
    void retryableConflict_shouldRemainActionableUntilLaterBatchReconcilesIt() {
        insertLegacyLink(FIRST_LINK_ID);
        AtomicBoolean conflictOnce = new AtomicBoolean(true);
        doAnswer(invocation -> conflictOnce.compareAndSet(true, false)
                ? false
                : invocation.callRealMethod())
                .when(shortLinkRepository).update(any());

        LegacyShortLinkBackfillBatchResult conflicted = backfillService.reconcileNextBatch(TENANT_ID, 1);
        LegacyShortLinkBackfillBatchResult retried = backfillService.reconcileNextBatch(TENANT_ID, 1);
        LegacyShortLinkBackfillBatchResult empty = backfillService.reconcileNextBatch(TENANT_ID, 1);

        assertThat(conflicted.retryableCount()).isEqualTo(1);
        assertThat(conflicted.progress().retryableCount()).isEqualTo(1);
        assertThat(retried.reconciledCount()).isEqualTo(1);
        assertThat(empty.attemptedCount()).isZero();
        assertThat(empty.progress().converged()).isTrue();
        assertThat(workItemStatus()).isEqualTo("RECONCILED");
        assertThat(workItemAttempts()).isEqualTo(2);
        assertThat(versionOneLinkCount()).isEqualTo(1);
        assertThat(integrationEventCount()).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(monthlyUsage(empty.applicationId())).isEqualTo(1L);
    }

    @Test
    void persistentRetryable_shouldNotStarveUndiscoveredLinkWhenBatchSizeIsOne() {
        long secondLinkId = FIRST_LINK_ID + 1;
        insertLegacyLink(FIRST_LINK_ID);
        insertLegacyLink(secondLinkId);
        doAnswer(invocation -> {
            ShortLink link = invocation.getArgument(0);
            return link.id() == FIRST_LINK_ID ? false : invocation.callRealMethod();
        }).when(shortLinkRepository).update(any());

        LegacyShortLinkBackfillBatchResult retryable = backfillService.reconcileNextBatch(TENANT_ID, 1);
        LegacyShortLinkBackfillBatchResult progressed = backfillService.reconcileNextBatch(TENANT_ID, 1);

        assertThat(retryable.retryableCount()).isEqualTo(1);
        assertThat(progressed.reconciledCount()).isEqualTo(1);
        assertThat(workItemStatus(FIRST_LINK_ID)).isEqualTo("RETRYABLE");
        assertThat(workItemStatus(secondLinkId)).isEqualTo("RECONCILED");
        assertThat(scopedLinkCount(progressed.applicationId(), progressed.domainId())).isEqualTo(1);
        assertThat(integrationEventCount()).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(monthlyUsage(progressed.applicationId())).isEqualTo(1L);
    }

    @Test
    void onlineReconciliationToAnotherScope_shouldRemainVisibleAsPermanentFailure() {
        insertLegacyLink(FIRST_LINK_ID);
        insertAlternateScope();
        LegacyApplicationBindingView defaultBinding =
                legacyApplicationProvisioningPort.ensureLegacyDefaultBinding(TENANT_ID);
        assertThat(backfillStore.takeBatch(
                TENANT_ID,
                defaultBinding.applicationId(),
                defaultBinding.domainId(),
                1
        )).hasSize(1);

        reconciliationService.reconcile(
                TENANT_ID,
                FIRST_LINK_ID,
                ALTERNATE_APPLICATION_ID,
                ALTERNATE_DOMAIN_ID
        );
        LegacyShortLinkBackfillBatchResult failed = backfillService.reconcileNextBatch(TENANT_ID, 1);
        LegacyShortLinkBackfillBatchResult converged = backfillService.reconcileNextBatch(TENANT_ID, 1);

        assertThat(failed.permanentFailureCount()).isEqualTo(1);
        assertThat(converged.progress().converged()).isTrue();
        assertThat(converged.progress().permanentFailureCount()).isEqualTo(1);

        backfillStore.recordOutcome(
                TENANT_ID,
                FIRST_LINK_ID,
                LegacyShortLinkBackfillStore.Outcome.RETRYABLE,
                "late retry from a concurrent worker"
        );

        assertThat(workItemStatus()).isEqualTo("PERMANENT_FAILURE");
        assertThat(workItemError()).contains("another scope");
        assertThat(backfillStore.progress(TENANT_ID).permanentFailureCount()).isEqualTo(1);
        assertThat(backfillStore.progress(TENANT_ID).retryableCount()).isZero();
        assertThat(linkScope()).isEqualTo(new LinkScope(ALTERNATE_APPLICATION_ID, ALTERNATE_DOMAIN_ID, 1L));
        assertThat(integrationEventCount()).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(monthlyUsage(ALTERNATE_APPLICATION_ID)).isEqualTo(1L);
    }

    @Test
    void concurrentWorkers_shouldPreserveTerminalOutcomeAndRetryConflictWithoutDuplicateEffects() throws Exception {
        insertLegacyLink(FIRST_LINK_ID);
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        AtomicInteger gatedReads = new AtomicInteger();
        doAnswer(invocation -> {
            Object loaded = invocation.callRealMethod();
            if (gatedReads.incrementAndGet() <= 2) {
                bothLoaded.await(10, TimeUnit.SECONDS);
            }
            return loaded;
        }).when(shortLinkRepository).findByTenantIdAndId(anyLong(), anyLong());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LegacyShortLinkBackfillBatchResult> first = executor.submit(
                    () -> backfillService.reconcileNextBatch(TENANT_ID, 1)
            );
            Future<LegacyShortLinkBackfillBatchResult> second = executor.submit(
                    () -> backfillService.reconcileNextBatch(TENANT_ID, 1)
            );
            List<LegacyShortLinkBackfillBatchResult> results = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );

            assertThat(results.stream().mapToInt(LegacyShortLinkBackfillBatchResult::reconciledCount).sum())
                    .isEqualTo(1);
            assertThat(results.stream().mapToInt(LegacyShortLinkBackfillBatchResult::retryableCount).sum())
                    .isEqualTo(1);
            LegacyShortLinkBackfillBatchResult empty = backfillService.reconcileNextBatch(TENANT_ID, 1);
            assertThat(empty.progress().converged()).isTrue();
            assertThat(workItemStatus()).isEqualTo("RECONCILED");
            assertThat(workItemAttempts()).isEqualTo(2);
            assertThat(versionOneLinkCount()).isEqualTo(1);
            assertThat(integrationEventCount()).isEqualTo(1);
            assertThat(outboxCount()).isEqualTo(2);
            assertThat(monthlyUsage(empty.applicationId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertLegacyLink(long linkId) {
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
                "legacy" + linkId,
                "https://example.com/" + linkId
        );
    }

    private void insertAlternateScope() {
        jdbcTemplate.update(
                "INSERT INTO applications (id, tenant_id, application_key, display_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                ALTERNATE_APPLICATION_ID,
                TENANT_ID,
                "alternate-reconcile",
                "Alternate Reconcile"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO domains (id, tenant_id, application_id, hostname, scope, status, trust_class)
                        VALUES (?, ?, ?, ?, 'APPLICATION_DEDICATED', 'ACTIVE', 'FIRST_PARTY')
                        """,
                ALTERNATE_DOMAIN_ID,
                TENANT_ID,
                ALTERNATE_APPLICATION_ID,
                "alternate-reconcile.example.test"
        );
        jdbcTemplate.update(
                "INSERT INTO application_quotas (application_id, monthly_link_limit, monthly_click_limit) VALUES (?, 10000, 1000000)",
                ALTERNATE_APPLICATION_ID
        );
    }

    private int scopedLinkCount(long applicationId, long domainId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM short_links WHERE tenant_id = ? AND application_id = ? AND domain_id = ?",
                Integer.class,
                TENANT_ID,
                applicationId,
                domainId
        );
    }

    private int versionOneLinkCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM short_links WHERE tenant_id = ? AND version = 1",
                Integer.class,
                TENANT_ID
        );
    }

    private int integrationEventCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_events WHERE producer = 'shortlink' AND tenant_id = ?",
                Integer.class,
                TENANT_ID
        );
    }

    private int outboxCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM redirect_cache_invalidation_outbox WHERE tenant_id = ?",
                Integer.class,
                TENANT_ID
        );
    }

    private long monthlyUsage(long applicationId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT used_count
                        FROM application_link_monthly_usages
                        WHERE tenant_id = ? AND application_id = ? AND month_start = DATE_FORMAT(UTC_DATE(), '%Y-%m-01')
                        """,
                Long.class,
                TENANT_ID,
                applicationId
        );
    }

    private long legacyApplicationId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM applications WHERE tenant_id = ? AND application_key = 'legacy-default'",
                Long.class,
                TENANT_ID
        );
    }

    private long legacyDomainId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM domains WHERE tenant_id = ? AND application_id = ?",
                Long.class,
                TENANT_ID,
                legacyApplicationId()
        );
    }

    private String workItemStatus() {
        return workItemStatus(FIRST_LINK_ID);
    }

    private String workItemStatus(long linkId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM shortlink_ownership_backfill_items WHERE tenant_id = ? AND link_id = ?",
                String.class,
                TENANT_ID,
                linkId
        );
    }

    private String workItemError() {
        return jdbcTemplate.queryForObject(
                "SELECT last_error FROM shortlink_ownership_backfill_items WHERE tenant_id = ? AND link_id = ?",
                String.class,
                TENANT_ID,
                FIRST_LINK_ID
        );
    }

    private int workItemAttempts() {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM shortlink_ownership_backfill_items WHERE tenant_id = ? AND link_id = ?",
                Integer.class,
                TENANT_ID,
                FIRST_LINK_ID
        );
    }

    private LinkScope linkScope() {
        return jdbcTemplate.queryForObject(
                "SELECT application_id, domain_id, version FROM short_links WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> new LinkScope(
                        rs.getLong("application_id"),
                        rs.getLong("domain_id"),
                        rs.getLong("version")
                ),
                TENANT_ID,
                FIRST_LINK_ID
        );
    }

    private record LinkScope(long applicationId, long domainId, long version) {
    }

    private static final class SimulatedProcessCrash extends RuntimeException {
    }
}

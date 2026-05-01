package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.shortlink.application.port.ApplicationLinkQuotaReservationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApplicationLinkQuotaReservationIntegrationTest extends ApplicationAwareShortLinkIntegrationTestSupport {

    @Autowired
    ApplicationLinkQuotaReservationPort quotaReservationPort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void tryReserveMonthlyLink_shouldAllowOnlyLimitConcurrentReservations() throws Exception {
        long tenantId = 91001L;
        long applicationId = 92001L;
        LocalDate monthStart = LocalDate.parse("2026-04-01");
        LocalDateTime fromInclusive = LocalDateTime.parse("2026-04-01T00:00:00");
        LocalDateTime toExclusive = LocalDateTime.parse("2026-05-01T00:00:00");
        int workers = 8;
        long limit = 3L;
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, tenantId);

        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> quotaReservationPort.tryReserveMonthlyLink(
                        tenantId,
                        applicationId,
                        monthStart,
                        fromInclusive,
                        toExclusive,
                        limit
                )));
            }

            long successfulReservations = 0L;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successfulReservations++;
                }
            }

            assertThat(successfulReservations).isEqualTo(limit);
            Long usedCount = jdbcTemplate.queryForObject(
                    """
                            SELECT used_count
                            FROM application_link_monthly_usages
                            WHERE tenant_id = ?
                              AND application_id = ?
                              AND month_start = ?
                            """,
                    Long.class,
                    tenantId,
                    applicationId,
                    monthStart
            );
            assertThat(usedCount).isEqualTo(limit);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tryReserveMonthlyLink_shouldSeedFromExistingLinksBeforeReserving() {
        long tenantId = 91002L;
        long applicationId = 92002L;
        LocalDate monthStart = LocalDate.parse("2026-04-01");
        LocalDateTime fromInclusive = LocalDateTime.parse("2026-04-01T00:00:00");
        LocalDateTime toExclusive = LocalDateTime.parse("2026-05-01T00:00:00");
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, tenantId);
        insertShortLink(93001L, tenantId, applicationId, "seedA1", "2026-04-15T10:00:00");
        insertShortLink(93002L, tenantId, applicationId, "seedA2", "2026-04-16T10:00:00");

        boolean reserved = quotaReservationPort.tryReserveMonthlyLink(
                tenantId,
                applicationId,
                monthStart,
                fromInclusive,
                toExclusive,
                3L
        );

        assertThat(reserved).isTrue();
        Long usedCount = jdbcTemplate.queryForObject(
                """
                        SELECT used_count
                        FROM application_link_monthly_usages
                        WHERE tenant_id = ?
                          AND application_id = ?
                          AND month_start = ?
                        """,
                Long.class,
                tenantId,
                applicationId,
                monthStart
        );
        assertThat(usedCount).isEqualTo(3L);
        assertThat(quotaReservationPort.tryReserveMonthlyLink(
                tenantId,
                applicationId,
                monthStart,
                fromInclusive,
                toExclusive,
                3L
        )).isFalse();
    }

    private void insertShortLink(
            long id,
            long tenantId,
            long applicationId,
            String code,
            String createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO short_links (
                            id,
                            tenant_id,
                            application_id,
                            domain_id,
                            code,
                            lifecycle_state,
                            original_url,
                            enabled,
                            created_by_type,
                            created_by,
                            version,
                            created_at
                        ) VALUES (?, ?, ?, NULL, ?, 'ACTIVE', ?, b'1', 'USER', 1, 0, ?)
                        """,
                id,
                tenantId,
                applicationId,
                code,
                "https://example.com/" + code,
                LocalDateTime.parse(createdAt)
        );
    }
}

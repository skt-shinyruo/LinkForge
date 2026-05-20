package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.ApplicationClickQuotaReservationPort;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.redirect.LinkMeta;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectQuotaGuardTest {

    @Test
    void unavailableReason_shouldReuseApplicationQuotaLookupWithinTtl() {
        MutableClock clock = new MutableClock("2026-04-24T10:15:30Z");
        RecordingApplicationScopePort applicationScopePort = new RecordingApplicationScopePort(
                quota(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)))
        );
        RecordingQuotaReservationPort quotaReservationPort = new RecordingQuotaReservationPort();
        RedirectQuotaGuard guard = new RedirectQuotaGuard(
                clock,
                applicationScopePort,
                quotaReservationPort,
                false,
                30L
        );

        RedirectResolution.UnavailableReason first = guard.unavailableReason(meta(22L, 33L));
        RedirectResolution.UnavailableReason second = guard.unavailableReason(meta(22L, 33L));

        assertThat(first).isNull();
        assertThat(second).isNull();
        assertThat(applicationScopePort.findApplicationQuotaCalls()).isEqualTo(1);
        assertThat(quotaReservationPort.monthlyClickLimits()).containsExactly(10L, 10L);
    }

    @Test
    void unavailableReason_shouldCacheNoQuotaLimitResultWithinTtl() {
        MutableClock clock = new MutableClock("2026-04-24T10:15:30Z");
        RecordingApplicationScopePort applicationScopePort = new RecordingApplicationScopePort(quota(Optional.empty()));
        RecordingQuotaReservationPort quotaReservationPort = new RecordingQuotaReservationPort();
        RedirectQuotaGuard guard = new RedirectQuotaGuard(
                clock,
                applicationScopePort,
                quotaReservationPort,
                false,
                30L
        );

        RedirectResolution.UnavailableReason first = guard.unavailableReason(meta(22L, 33L));
        RedirectResolution.UnavailableReason second = guard.unavailableReason(meta(22L, 33L));

        assertThat(first).isNull();
        assertThat(second).isNull();
        assertThat(applicationScopePort.findApplicationQuotaCalls()).isEqualTo(1);
        assertThat(quotaReservationPort.monthlyClickLimits()).isEmpty();
    }

    @Test
    void unavailableReason_shouldRefreshApplicationQuotaLookupAfterTtl() {
        MutableClock clock = new MutableClock("2026-04-24T10:15:30Z");
        RecordingApplicationScopePort applicationScopePort = new RecordingApplicationScopePort(
                quota(Optional.of(new ApplicationQuotaView(33L, 100L, 10L))),
                quota(Optional.of(new ApplicationQuotaView(33L, 100L, 20L)))
        );
        RecordingQuotaReservationPort quotaReservationPort = new RecordingQuotaReservationPort();
        RedirectQuotaGuard guard = new RedirectQuotaGuard(
                clock,
                applicationScopePort,
                quotaReservationPort,
                false,
                30L
        );

        RedirectResolution.UnavailableReason first = guard.unavailableReason(meta(22L, 33L));
        clock.advanceSeconds(31L);
        RedirectResolution.UnavailableReason second = guard.unavailableReason(meta(22L, 33L));

        assertThat(first).isNull();
        assertThat(second).isNull();
        assertThat(applicationScopePort.findApplicationQuotaCalls()).isEqualTo(2);
        assertThat(quotaReservationPort.monthlyClickLimits()).containsExactly(10L, 20L);
    }

    @Test
    void unavailableReason_shouldNotCacheApplicationQuotaLookupExceptions() {
        MutableClock clock = new MutableClock("2026-04-24T10:15:30Z");
        RecordingApplicationScopePort applicationScopePort = new RecordingApplicationScopePort(
                failure(new IllegalStateException("platform unavailable")),
                quota(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)))
        );
        RecordingQuotaReservationPort quotaReservationPort = new RecordingQuotaReservationPort();
        RedirectQuotaGuard guard = new RedirectQuotaGuard(
                clock,
                applicationScopePort,
                quotaReservationPort,
                false,
                30L
        );

        RedirectResolution.UnavailableReason first = guard.unavailableReason(meta(22L, 33L));
        RedirectResolution.UnavailableReason second = guard.unavailableReason(meta(22L, 33L));

        assertThat(first).isEqualTo(RedirectResolution.UnavailableReason.QUOTA_EXCEEDED);
        assertThat(second).isNull();
        assertThat(applicationScopePort.findApplicationQuotaCalls()).isEqualTo(2);
        assertThat(quotaReservationPort.monthlyClickLimits()).containsExactly(10L);
    }

    private static QuotaOutcome quota(Optional<ApplicationQuotaView> quota) {
        return () -> quota;
    }

    private static QuotaOutcome failure(RuntimeException exception) {
        return () -> {
            throw exception;
        };
    }

    private static LinkMeta meta(long tenantId, long applicationId) {
        return new LinkMeta(
                11L,
                tenantId,
                "abc123",
                "https://example.com/live",
                true,
                LocalDateTime.parse("2026-05-01T00:00:00"),
                302,
                false,
                "https://example.com/unavailable",
                "ALLOWLIST",
                "utm_source",
                "go.example.test",
                applicationId,
                44L
        );
    }

    private interface QuotaOutcome {

        Optional<ApplicationQuotaView> apply();
    }

    private static final class RecordingApplicationScopePort implements ApplicationScopePort {

        private final List<QuotaOutcome> outcomes;
        private int findApplicationQuotaCalls;

        private RecordingApplicationScopePort(QuotaOutcome... outcomes) {
            this.outcomes = List.of(outcomes);
        }

        @Override
        public void requireApplicationExists(long tenantId, long applicationId) {
        }

        @Override
        public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
        }

        @Override
        public Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId) {
            findApplicationQuotaCalls++;
            int index = Math.min(findApplicationQuotaCalls - 1, outcomes.size() - 1);
            return outcomes.get(index).apply();
        }

        private int findApplicationQuotaCalls() {
            return findApplicationQuotaCalls;
        }
    }

    private static final class RecordingQuotaReservationPort implements ApplicationClickQuotaReservationPort {

        private final List<Long> monthlyClickLimits = new ArrayList<>();

        @Override
        public boolean tryReserveMonthlyClick(
                long tenantId,
                long applicationId,
                LocalDate fromInclusiveUtc,
                LocalDate toExclusiveUtc,
                long monthlyClickLimit
        ) {
            monthlyClickLimits.add(monthlyClickLimit);
            return true;
        }

        private List<Long> monthlyClickLimits() {
            return monthlyClickLimits;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(String instant) {
            this.instant = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}

package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.ApplicationClickQuotaReservationPort;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedirectServiceAuthoritativeFallbackTest {

    @Test
    void resolve_shouldNotQueryAuthorityOnNegativeCacheHit() {
        RecordingLinkCache cache = new RecordingLinkCache(LinkCachePort.LookupResult.negativeHit());
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        RedirectService service = redirectService(
                cache,
                shortLinkReadPort,
                visit -> {
                },
                Clock.systemUTC()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("missing123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.NOT_FOUND);
        verifyNoInteractions(shortLinkReadPort);
    }

    @Test
    void resolve_shouldUseShortLinkReadPortBeforeProjectionOnCacheMiss() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(false, "ACTIVE");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));

        VisitRecorderPort visitRecorderPort = visit -> {
        };

        RedirectService service = redirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                Clock.systemUTC()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.meta()).isEqualTo(authoritative);
        assertThat(cache.cachedMeta).isEqualTo(authoritative);
        verify(shortLinkReadPort).findRedirectMetaByHostAndCode("go.example.test", "abc123");
    }

    @Test
    void resolve_shouldPropagateAuthoritativeFailureWithoutNegativeCaching() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        RuntimeException failure = new IllegalStateException("primary unavailable");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenThrow(failure);
        RedirectService service = redirectService(
                cache,
                shortLinkReadPort,
                visit -> {
                },
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        )).isSameAs(failure);
        assertThat(cache.markedNotFoundCode).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PRE_RELEASE", "DISABLED"})
    void resolve_shouldTreatNonActiveLifecycleAsUnavailable(String lifecycleState) {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(false, lifecycleState);
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        VisitRecorderPort visitRecorderPort = visit -> {
        };
        RedirectService service = redirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                Clock.systemUTC()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
        assertThat(resolution.unavailableReason()).isEqualTo(RedirectResolution.UnavailableReason.DISABLED);
    }

    @Test
    void resolve_shouldReturnUnavailableAndSkipAnalyticsWhenMonthlyClickQuotaReached() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(false, "ACTIVE");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(22L, 33L))
                .thenReturn(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)));
        ApplicationClickQuotaReservationPort quotaReservationPort = mock(ApplicationClickQuotaReservationPort.class);
        when(quotaReservationPort.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        )).thenReturn(false);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC);
        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                quotaGuard(clock, applicationScopePort, quotaReservationPort, false),
                OperationalMetrics.noop()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
        assertThat(resolution.unavailableReason()).isEqualTo(RedirectResolution.UnavailableReason.QUOTA_EXCEEDED);
        assertThat(recorded.get()).isNull();
    }

    @Test
    void resolve_shouldReserveMonthlyClickQuotaOnlyForActualRedirects() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(true, "ACTIVE");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(22L, 33L))
                .thenReturn(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)));
        ApplicationClickQuotaReservationPort quotaReservationPort = mock(ApplicationClickQuotaReservationPort.class);
        when(quotaReservationPort.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        )).thenReturn(true);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC);
        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                quotaGuard(clock, applicationScopePort, quotaReservationPort, false),
                OperationalMetrics.noop()
        );

        RedirectResolution preview = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", true, false, null)
        );
        RedirectResolution redirect = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", true, true, null)
        );

        assertThat(preview.kind()).isEqualTo(RedirectResolution.Kind.PREVIEW);
        assertThat(redirect.kind()).isEqualTo(RedirectResolution.Kind.REDIRECT);
        verify(quotaReservationPort).tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        );
        assertThat(recorded.get()).isNotNull();
    }

    @Test
    void resolve_shouldReturnUnavailableAndSkipAnalyticsWhenQuotaReservationRejected() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(false, "ACTIVE");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(22L, 33L))
                .thenReturn(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)));
        ApplicationClickQuotaReservationPort quotaReservationPort = mock(ApplicationClickQuotaReservationPort.class);
        when(quotaReservationPort.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        )).thenReturn(false);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC);
        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                quotaGuard(clock, applicationScopePort, quotaReservationPort, false),
                OperationalMetrics.noop()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
        assertThat(resolution.unavailableReason()).isEqualTo(RedirectResolution.UnavailableReason.QUOTA_EXCEEDED);
        assertThat(recorded.get()).isNull();
    }

    @Test
    void resolve_shouldReturnUnavailableWhenQuotaReservationBackendFails() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(false, "ACTIVE");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(22L, 33L))
                .thenReturn(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)));
        ApplicationClickQuotaReservationPort quotaReservationPort = mock(ApplicationClickQuotaReservationPort.class);
        when(quotaReservationPort.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        )).thenThrow(new IllegalStateException("redis unavailable"));
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC);
        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                quotaGuard(clock, applicationScopePort, quotaReservationPort, false),
                OperationalMetrics.noop()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
        assertThat(resolution.unavailableReason()).isEqualTo(RedirectResolution.UnavailableReason.QUOTA_EXCEEDED);
        assertThat(recorded.get()).isNull();
    }

    @Test
    void resolve_shouldReturnUnavailableWhenApplicationQuotaLookupFails() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(false, "ACTIVE");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(22L, 33L))
                .thenThrow(new IllegalStateException("platform db unavailable"));
        ApplicationClickQuotaReservationPort quotaReservationPort = mock(ApplicationClickQuotaReservationPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC);
        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                quotaGuard(clock, applicationScopePort, quotaReservationPort, false),
                OperationalMetrics.noop()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
        assertThat(resolution.unavailableReason()).isEqualTo(RedirectResolution.UnavailableReason.QUOTA_EXCEEDED);
        assertThat(recorded.get()).isNull();
    }

    @Test
    void resolve_shouldRedirectOnQuotaBackendFailureWhenFailOpenIsExplicit() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        LinkMeta authoritative = authoritative(false, "ACTIVE");
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(22L, 33L))
                .thenReturn(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)));
        ApplicationClickQuotaReservationPort quotaReservationPort = mock(ApplicationClickQuotaReservationPort.class);
        when(quotaReservationPort.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        )).thenThrow(new IllegalStateException("redis unavailable"));
        RedirectQuotaGuard quotaGuard = new RedirectQuotaGuard(
                Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC),
                applicationScopePort,
                quotaReservationPort,
                true,
                30L,
                10_000L,
                OperationalMetrics.noop()
        );
        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC),
                quotaGuard,
                OperationalMetrics.noop()
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.REDIRECT);
        assertThat(recorded.get()).isNotNull();
    }

    private static final class RecordingLinkCache implements LinkCachePort {

        private final LookupResult lookupResult;
        private LinkMeta cachedMeta;
        private String markedNotFoundCode;

        private RecordingLinkCache() {
            this(LookupResult.miss());
        }

        private RecordingLinkCache(LookupResult lookupResult) {
            this.lookupResult = lookupResult;
        }

        @Override
        public LookupResult lookup(String host, String code) {
            return lookupResult;
        }

        @Override
        public boolean tryPut(String host, LinkMeta meta) {
            cachedMeta = meta;
            return true;
        }

        @Override
        public void markNotFound(String host, String code) {
            markedNotFoundCode = code;
        }

        @Override
        public boolean tryEvict(String host, String code) {
            return true;
        }
    }

    private static RedirectService redirectService(
            LinkCachePort cache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock
    ) {
        return new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                mock(RedirectQuotaGuard.class),
                OperationalMetrics.noop()
        );
    }

    private static RedirectQuotaGuard quotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort quotaReservationPort,
            boolean failOpen
    ) {
        return new RedirectQuotaGuard(
                clock,
                applicationScopePort,
                quotaReservationPort,
                failOpen,
                30L,
                10_000L,
                OperationalMetrics.noop()
        );
    }

    private static LinkMeta authoritative(boolean previewEnabled, String lifecycleState) {
        return new LinkMeta(
                11L,
                22L,
                "abc123",
                "https://example.com/live",
                true,
                null,
                302,
                previewEnabled,
                "https://example.com/unavailable",
                "ALLOWLIST",
                "utm_source",
                "go.example.test",
                33L,
                44L,
                lifecycleState
        );
    }
}

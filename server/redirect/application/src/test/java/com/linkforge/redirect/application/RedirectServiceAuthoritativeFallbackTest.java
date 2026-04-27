package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectServiceAuthoritativeFallbackTest {

    @Test
    void resolve_shouldUseShortLinkReadPortBeforeProjectionOnCacheMiss() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        ShortLinkReadPort.RedirectLinkView authoritative = new ShortLinkReadPort.RedirectLinkView(
                22L,
                11L,
                "abc123",
                "go.example.test",
                "https://example.com/live",
                true,
                Instant.parse("2026-03-18T10:15:30Z"),
                302,
                false,
                "https://example.com/unavailable",
                "ALLOWLIST",
                "utm_source",
                33L,
                44L
        );
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));

        LinkMeta expected = new LinkMeta(
                11L,
                22L,
                "abc123",
                "https://example.com/live",
                true,
                LocalDateTime.parse("2026-03-18T10:15:30"),
                302,
                false,
                "https://example.com/unavailable",
                "ALLOWLIST",
                "utm_source",
                "go.example.test",
                33L,
                44L
        );
        VisitRecorderPort visitRecorderPort = visit -> {
        };

        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                Clock.systemUTC()
        );

        LinkMeta resolved = service.resolve("go.example.test", "abc123");

        assertThat(resolved).isEqualTo(expected);
        assertThat(cache.cachedMeta).isEqualTo(expected);
        verify(shortLinkReadPort).findRedirectMetaByHostAndCode("go.example.test", "abc123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PRE_RELEASE", "DISABLED"})
    void resolve_shouldTreatNonActiveLifecycleAsUnavailable(String lifecycleState) {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        ShortLinkReadPort.RedirectLinkView authoritative = new ShortLinkReadPort.RedirectLinkView(
                22L,
                11L,
                "abc123",
                "go.example.test",
                "https://example.com/live",
                true,
                null,
                302,
                false,
                "https://example.com/unavailable",
                "ALLOWLIST",
                "utm_source",
                33L,
                44L,
                lifecycleState
        );
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        VisitRecorderPort visitRecorderPort = visit -> {
        };
        RedirectService service = new RedirectService(
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
        ShortLinkReadPort.RedirectLinkView authoritative = new ShortLinkReadPort.RedirectLinkView(
                22L,
                11L,
                "abc123",
                "go.example.test",
                "https://example.com/live",
                true,
                null,
                302,
                false,
                "https://example.com/unavailable",
                "ALLOWLIST",
                "utm_source",
                33L,
                44L
        );
        when(shortLinkReadPort.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(authoritative));
        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(22L, 33L))
                .thenReturn(Optional.of(new ApplicationQuotaView(33L, 100L, 10L)));
        ApplicationClickUsagePort applicationClickUsagePort = mock(ApplicationClickUsagePort.class);
        when(applicationClickUsagePort.countApplicationClicks(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01")
        )).thenReturn(10L);
        RedirectService service = new RedirectService(
                cache,
                shortLinkReadPort,
                visitRecorderPort,
                Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), java.time.ZoneOffset.UTC),
                applicationScopePort,
                applicationClickUsagePort
        );

        RedirectResolution resolution = service.resolve(
                new ResolveRedirectRequest("abc123", "go.example.test", false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
        assertThat(resolution.unavailableReason()).isEqualTo(RedirectResolution.UnavailableReason.QUOTA_EXCEEDED);
        assertThat(recorded.get()).isNull();
    }

    private static final class RecordingLinkCache implements LinkCachePort {

        private LinkMeta cachedMeta;

        @Override
        public LookupResult lookup(String code) {
            return LookupResult.miss();
        }

        @Override
        public boolean tryPut(LinkMeta meta) {
            cachedMeta = meta;
            return true;
        }

        @Override
        public void markNotFound(String code) {
        }

        @Override
        public boolean tryEvict(String code) {
            return true;
        }
    }
}

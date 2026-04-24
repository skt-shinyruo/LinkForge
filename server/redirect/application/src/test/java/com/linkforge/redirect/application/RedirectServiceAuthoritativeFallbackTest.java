package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.shortlink.application.ShortLinkReadService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectServiceAuthoritativeFallbackTest {

    @Test
    void resolve_shouldUseShortLinkReadServiceBeforeProjectionOnCacheMiss() {
        RecordingLinkCache cache = new RecordingLinkCache();
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        ShortLinkReadService.RedirectLinkMeta authoritative = new ShortLinkReadService.RedirectLinkMeta(
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
        when(shortLinkReadService.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
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
        VisitRecorderPort recorder = (tenantId, linkId, visitContext) -> {
        };

        RedirectService service = new RedirectService(
                cache,
                shortLinkReadService,
                recorder,
                Clock.systemUTC()
        );

        LinkMeta resolved = service.resolve("go.example.test", "abc123");

        assertThat(resolved).isEqualTo(expected);
        assertThat(cache.cachedMeta).isEqualTo(expected);
        verify(shortLinkReadService).findRedirectMetaByHostAndCode("go.example.test", "abc123");
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

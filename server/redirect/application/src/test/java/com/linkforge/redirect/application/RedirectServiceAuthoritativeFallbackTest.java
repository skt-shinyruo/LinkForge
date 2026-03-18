package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
import com.linkforge.redirect.application.projection.LinkMetaProjectionPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectServiceAuthoritativeFallbackTest {

    @Test
    void resolve_shouldUseAuthoritativeSourceBeforeProjectionOnCacheMiss() {
        RecordingLinkCache cache = new RecordingLinkCache();
        LinkMeta expected = new LinkMeta(
                11L,
                22L,
                "abc123",
                "https://example.com/live",
                true,
                LocalDateTime.parse("2026-03-18T10:15:30"),
                302,
                false,
                null,
                null,
                null
        );
        LinkMetaSourcePort authoritativeSource = code -> Optional.of(expected);
        AtomicInteger projectionCalls = new AtomicInteger();
        LinkMetaProjectionPort projection = code -> {
            projectionCalls.incrementAndGet();
            return Optional.empty();
        };
        VisitRecorderPort recorder = (tenantId, linkId, visitContext) -> {
        };

        RedirectService service = new RedirectService(
                cache,
                projection,
                authoritativeSource,
                recorder,
                Clock.systemUTC()
        );

        LinkMeta resolved = service.resolve("abc123");

        assertThat(resolved).isEqualTo(expected);
        assertThat(cache.cachedMeta).isEqualTo(expected);
        assertThat(projectionCalls.get()).isZero();
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

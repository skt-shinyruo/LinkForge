package com.linkforge.redirect.interfaces.web;

import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.redirect.application.RedirectResolution;
import com.linkforge.redirect.application.ResolveRedirectRequest;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.RedirectUrlBuilder;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedirectControllerExpiryBoundaryTest {

    @Test
    void expiresAt_equal_now_should_be_treated_as_expired() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        LinkMeta meta = new LinkMeta(
                1L,
                1L,
                "abc123",
                "https://example.com",
                true,
                nowUtc,
                null,
                false,
                null,
                null,
                null,
                null
        );

        AtomicInteger recorded = new AtomicInteger();
        VisitRecorderPort visitRecorderPort = visit -> recorded.incrementAndGet();

        RedirectService redirectService = new RedirectService(
                new LinkCachePort() {
                    @Override
                    public LookupResult lookup(String code) {
                        return LookupResult.miss();
                    }

                    @Override
                    public boolean tryPut(LinkMeta meta) {
                        return true;
                    }

                    @Override
                    public void markNotFound(String code) {
                        // no-op
                    }

                    @Override
                    public boolean tryEvict(String code) {
                        return true;
                    }
                },
                shortLinkReadPort(meta),
                visitRecorderPort,
                clock
        );

        RedirectProperties props = new RedirectProperties();
        props.setDefaultStatusCode(302);
        RedirectUrlBuilder urlBuilder = new RedirectUrlBuilder(props);
        RedirectController controller = new RedirectController(
                redirectService,
                new RedirectHttpRequestMapper(),
                new RedirectHttpResponseWriter(
                        props,
                        urlBuilder,
                        new RedirectHtmlPageRenderer(props, new RedirectConfirmHrefBuilder())
                )
        );

        assertThatThrownBy(() -> controller.redirect("abc123", null))
                .isInstanceOf(RedirectBusinessException.class)
                .extracting(e -> ((RedirectBusinessException) e).getErrorCode())
                .isEqualTo(RedirectErrorCode.LINK_EXPIRED);

        assertThat(recorded.get()).isEqualTo(0);
    }

    @Test
    void resolve_should_classify_equal_expiry_as_unavailable() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LinkMeta meta = new LinkMeta(
                1L,
                1L,
                "abc123",
                "https://example.com",
                true,
                nowUtc,
                null,
                false,
                null,
                null,
                null,
                null
        );

        RedirectService redirectService = new RedirectService(
                new LinkCachePort() {
                    @Override
                    public LookupResult lookup(String code) {
                        return LookupResult.miss();
                    }

                    @Override
                    public boolean tryPut(LinkMeta meta) {
                        return true;
                    }

                    @Override
                    public void markNotFound(String code) {
                    }

                    @Override
                    public boolean tryEvict(String code) {
                        return true;
                    }
                },
                shortLinkReadPort(meta),
                visit -> {
                },
                clock
        );

        assertThat(redirectService.resolve(new ResolveRedirectRequest("abc123", null, false, false, null)).kind())
                .isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
    }

    private static ShortLinkReadPort shortLinkReadPort(LinkMeta meta) {
        return new ShortLinkReadPort() {
            @Override
            public Optional<RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
                return Optional.of(new RedirectLinkView(
                        meta.tenantId(),
                        meta.id(),
                        meta.code(),
                        meta.hostname(),
                        meta.originalUrl(),
                        meta.enabled(),
                        meta.expiresAt() == null ? null : meta.expiresAt().toInstant(ZoneOffset.UTC),
                        meta.redirectStatusCode(),
                        meta.previewEnabled(),
                        meta.unavailableLandingUrl(),
                        meta.queryForwardMode(),
                        meta.queryForwardAllowlist(),
                        meta.applicationId(),
                        meta.domainId(),
                        meta.lifecycleState()
                ));
            }

            @Override
            public Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
                return Optional.empty();
            }

            @Override
            public Map<Long, ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
                return Map.of();
            }
        };
    }
}

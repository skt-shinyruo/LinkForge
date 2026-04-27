package com.linkforge.redirect.interfaces.web;

import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.RedirectUrlBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectControllerTimezoneTest {

    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void should_not_expire_future_utc_link_when_jvm_timezone_not_utc() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        LinkMeta meta = new LinkMeta(
                1L,
                1L,
                "abc123",
                "https://example.com",
                true,
                LocalDateTime.now(ZoneOffset.UTC).plusHours(1),
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
                Clock.systemUTC()
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

        ResponseEntity<?> resp = controller.redirect("abc123", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resp.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com"));
        assertThat(recorded.get()).isEqualTo(1);
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

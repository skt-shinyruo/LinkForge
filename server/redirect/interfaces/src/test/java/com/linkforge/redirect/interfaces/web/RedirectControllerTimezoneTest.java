package com.linkforge.redirect.interfaces.web;

import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.RedirectUrlBuilder;
import com.linkforge.redirect.application.projection.LinkMetaProjectionPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
                null
        );

        AtomicInteger recorded = new AtomicInteger();
        VisitRecorderPort recorder = (tenantId, linkId, visitContext) -> recorded.incrementAndGet();

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
                (LinkMetaProjectionPort) code -> Optional.of(meta),
                recorder,
                Clock.systemUTC()
        );

        RedirectProperties props = new RedirectProperties();
        props.setDefaultStatusCode(302);
        RedirectUrlBuilder urlBuilder = new RedirectUrlBuilder(props);
        RedirectController controller = new RedirectController(redirectService, props, urlBuilder, Clock.systemUTC());

        ResponseEntity<?> resp = controller.redirect("abc123", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resp.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com"));
        assertThat(recorded.get()).isEqualTo(1);
    }
}

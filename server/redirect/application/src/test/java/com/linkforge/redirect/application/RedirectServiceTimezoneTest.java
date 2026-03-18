package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
import com.linkforge.redirect.application.projection.LinkMetaProjectionPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectServiceTimezoneTest {

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
    void should_treat_expiresAt_as_utc_when_checking_availability() {
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

        AtomicInteger calls = new AtomicInteger();
        VisitRecorderPort recorder = (tenantId, linkId, visitContext) -> calls.incrementAndGet();

        RedirectService service = new RedirectService(
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
                (LinkMetaProjectionPort) code -> Optional.empty(),
                (LinkMetaSourcePort) code -> Optional.empty(),
                recorder,
                Clock.systemUTC()
        );

        service.recordVisitIfAvailable(meta, null);

        assertThat(calls.get()).isEqualTo(1);
    }
}

package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

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
                null,
                null
        );

        AtomicReference<RedirectVisitRecord> recorded = new AtomicReference<>();
        VisitRecorderPort visitRecorderPort = recorded::set;

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
                new EmptyShortLinkReadPort(),
                visitRecorderPort,
                Clock.fixed(Instant.parse("2026-04-24T10:15:30Z"), ZoneOffset.UTC)
        );

        RedirectVisitInput visitInput = new RedirectVisitInput(
                "1.2.3.4",
                "Mozilla/5.0",
                "https://ref.example.com/path",
                "zh-CN,zh;q=0.9",
                java.util.Map.of("utm_source", "newsletter")
        );

        service.recordVisitIfAvailable(meta, visitInput);

        assertThat(recorded.get()).isEqualTo(new RedirectVisitRecord(
                1L,
                1L,
                Instant.parse("2026-04-24T10:15:30Z").toEpochMilli(),
                null,
                null,
                "abc123",
                "https://example.com",
                new VisitContext(
                        "1.2.3.4",
                        "Mozilla/5.0",
                        "https://ref.example.com/path",
                        "zh-CN,zh;q=0.9",
                        java.util.Map.of("utm_source", "newsletter")
                )
        ));
    }

    private static final class EmptyShortLinkReadPort implements ShortLinkReadPort {

        @Override
        public java.util.Optional<RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Map<Long, ShortLinkSummary> listSummaries(long tenantId, java.util.List<Long> linkIds) {
            return java.util.Map.of();
        }
    }
}

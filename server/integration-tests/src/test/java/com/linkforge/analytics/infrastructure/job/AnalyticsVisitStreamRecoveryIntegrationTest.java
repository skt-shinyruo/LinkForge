package com.linkforge.analytics.infrastructure.job;

import com.linkforge.LinkForgeApplication;
import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.analytics.application.port.AnalyticsVisitEventAppender;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AnalyticsVisitStreamRecoveryIntegrationTest extends SharedIntegrationTestSupport {

    private static final String GROUP = "lf-visit-ingest";
    private static final long TENANT_ID = 41L;
    private static final long LINK_ID = 9_001L;
    private static final int INGEST_BATCH_SIZE = 40;
    private static final int INGEST_MAX_BATCHES = 2;
    private static final int MAX_EVENTS_PER_SCHEDULE = INGEST_BATCH_SIZE * INGEST_MAX_BATCHES;
    private static final int CRASHED_CONSUMER_EVENTS = 60;
    private static final int PRODUCTION_ROUNDS = 10;
    private static final int EVENTS_PER_PRODUCTION_ROUND = 100;
    private static final long STREAM_MAX_LEN = 500L;
    private static final long APPROXIMATE_TRIM_TOLERANCE = 100L;

    @DynamicPropertySource
    static void analyticsProperties(DynamicPropertyRegistry registry) {
        registry.add("app.scheduling.enabled", () -> "false");
        registry.add("app.analytics.events.enabled", () -> "true");
        registry.add("app.analytics.events.sample-rate", () -> "1");
        registry.add("app.analytics.visit-stream.max-len", () -> String.valueOf(STREAM_MAX_LEN));
        registry.add("app.analytics.visit-stream.peak-events-per-second", () -> "5");
        registry.add("app.analytics.visit-stream.recovery-window-seconds", () -> "100");
        registry.add("app.analytics.visit-stream.safety-margin-percent", () -> "0");
        registry.add("app.analytics.events.ingest-batch-size", () -> String.valueOf(INGEST_BATCH_SIZE));
        registry.add("app.analytics.events.ingest-max-batches", () -> String.valueOf(INGEST_MAX_BATCHES));
        registry.add("app.analytics.events.ingest-time-budget-ms", () -> "5000");
        registry.add("app.analytics.events.pending-reclaim-min-idle-ms", () -> "0");
        registry.add("app.analytics.events.pending-reclaim-count", () -> String.valueOf(INGEST_BATCH_SIZE));
    }

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AnalyticsEventIngestJob ingestJob;

    @Autowired
    AnalyticsVisitEventAppender eventAppender;

    @Test
    void sustainedProductionAndScheduledConsumption_shouldStayBoundedRecoverAndTrimTheStream() {
        String streamKey = AnalyticsKeys.visitEventStreamKey();
        for (int i = 0; i < CRASHED_CONSUMER_EVENTS; i++) {
            appendVisit(i);
        }

        redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), GROUP);
        redis.opsForStream().read(
                Consumer.from(GROUP, "crashed-consumer"),
                StreamReadOptions.empty().count(CRASHED_CONSUMER_EVENTS),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed())
        );
        assertThat(pending(streamKey)).isEqualTo(CRASHED_CONSUMER_EVENTS);

        int produced = CRASHED_CONSUMER_EVENTS;
        for (int round = 0; round < PRODUCTION_ROUNDS; round++) {
            for (int offset = 0; offset < EVENTS_PER_PRODUCTION_ROUND; offset++) {
                appendVisit(produced + offset);
            }
            produced += EVENTS_PER_PRODUCTION_ROUND;

            int before = persistedVisitCount();
            ingestJob.ingest();
            int scheduled = persistedVisitCount() - before;

            assertThat(scheduled)
                    .as("production round %s must respect the configured scheduling bound", round)
                    .isBetween(1, MAX_EVENTS_PER_SCHEDULE);
        }

        assertThat(lag(streamKey) + pending(streamKey)).isPositive();

        int maxDrainSchedules = (produced / MAX_EVENTS_PER_SCHEDULE) + 2;
        for (int schedule = 0; schedule < maxDrainSchedules && lag(streamKey) + pending(streamKey) > 0; schedule++) {
            int before = persistedVisitCount();
            ingestJob.ingest();
            int scheduled = persistedVisitCount() - before;

            assertThat(scheduled)
                    .as("drain schedule %s must respect the configured scheduling bound", schedule)
                    .isBetween(0, MAX_EVENTS_PER_SCHEDULE);
        }

        Long streamLength = redis.opsForStream().size(streamKey);
        assertThat(persistedVisitCount()).isEqualTo(produced);
        assertThat(pending(streamKey)).isZero();
        assertThat(lag(streamKey)).isZero();
        assertThat(streamLength)
                .isNotNull()
                .isLessThan((long) produced)
                .isLessThanOrEqualTo(STREAM_MAX_LEN + APPROXIMATE_TRIM_TOLERANCE);
    }

    private void appendVisit(int sequence) {
        eventAppender.append(new AnalyticsVisitEventService.RedirectVisitEvent(
                TENANT_ID,
                LINK_ID,
                1_710_000_000_000L + sequence,
                101L,
                201L,
                "recovery",
                "https://example.com/recovery",
                "198.51.100.10",
                "LinkForge recovery integration test",
                "https://example.com/source",
                "zh-CN",
                Map.of("utm_source", "recovery")
        ));
    }

    private int persistedVisitCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_visit_events WHERE tenant_id = ? AND link_id = ?",
                Integer.class,
                TENANT_ID,
                LINK_ID
        );
        return count == null ? 0 : count;
    }

    private long pending(String streamKey) {
        PendingMessagesSummary summary = redis.opsForStream().pending(streamKey, GROUP);
        return summary == null ? 0L : summary.getTotalPendingMessages();
    }

    private long lag(String streamKey) {
        Object rawLag = redis.opsForStream().groups(streamKey).stream()
                .filter(group -> GROUP.equals(group.groupName()))
                .findFirst()
                .orElseThrow()
                .getRaw()
                .get("lag");
        return rawLag instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(rawLag));
    }
}

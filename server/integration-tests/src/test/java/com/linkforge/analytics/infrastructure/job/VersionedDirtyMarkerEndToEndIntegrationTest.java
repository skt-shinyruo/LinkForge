package com.linkforge.analytics.infrastructure.job;

import com.linkforge.LinkForgeApplication;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class VersionedDirtyMarkerEndToEndIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void analyticsProperties(DynamicPropertyRegistry registry) {
        registry.add("app.scheduling.enabled", () -> "false");
        registry.add("app.analytics.redis-key-ttl-days", () -> "7");
        registry.add("app.analytics.dimensions.enabled", () -> "true");
        registry.add("app.analytics.dimensions.types", () -> "language");
        registry.add("app.analytics.dirty-marker.legacy-write-enabled", () -> "false");
        registry.add("app.analytics.dirty-marker.legacy-read-enabled", () -> "true");
    }

    @Autowired
    VisitRecorderPort visitRecorder;

    @Autowired
    AnalyticsRedirectEventProjectorJob projector;

    @Autowired
    AnalyticsFlushJob statsFlush;

    @Autowired
    AnalyticsDimensionFlushJob dimensionFlush;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v2Marker_shouldBoundHotIdentitySurviveGenerationRaceAndConvergeWithLegacyDrain() {
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        long occurredAt = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        String member = AnalyticsKeys.dirtyLinkMember(41L, 9_001L);

        visitRecorder.recordVisit(visit(occurredAt, "203.0.113.1"));
        visitRecorder.recordVisit(visit(occurredAt + 1, "203.0.113.2"));
        projector.project();

        String markerKey = AnalyticsKeys.statsDirtyMarkerV2Key(day);
        String firstSeenKey = AnalyticsKeys.statsDirtyMarkerV2FirstSeenKey(day);
        assertThat(redis.opsForValue().get(AnalyticsKeys.pvKey(41L, 9_001L, day))).isEqualTo("2");
        assertThat(redis.opsForHyperLogLog().size(AnalyticsKeys.uvKey(41L, 9_001L, day))).isEqualTo(2L);
        assertThat(redis.opsForHash().size(markerKey)).isEqualTo(1L);
        assertThat(redis.opsForHash().get(markerKey, member)).isEqualTo("2");
        assertThat(redis.opsForHash().get(firstSeenKey, member)).isNotNull();
        assertThat(redis.getExpire(markerKey)).isPositive();
        assertThat(redis.hasKey(AnalyticsKeys.statsDirtyStreamKey(day))).isFalse();

        VersionedDirtyMarkerStore markerStore = new VersionedDirtyMarkerStore(redis);
        List<VersionedDirtyMarkerStore.Claim> claimed = markerStore.claim(markerKey, firstSeenKey, 10);
        redis.opsForValue().increment(AnalyticsKeys.pvKey(41L, 9_001L, day));
        redis.opsForHash().increment(markerKey, member, 1L);
        VersionedDirtyMarkerStore.Completion raced = markerStore.complete(markerKey, firstSeenKey, claimed);

        assertThat(raced).isEqualTo(new VersionedDirtyMarkerStore.Completion(0L, 1L));
        assertThat(redis.opsForHash().get(markerKey, member)).isEqualTo("3");
        assertThat(redis.opsForHash().get(firstSeenKey, member)).isNotNull();

        redis.opsForStream().add(StreamRecords.newRecord()
                .in(AnalyticsKeys.statsDirtyStreamKey(day))
                .ofStrings(Map.of("member", member, "ts", String.valueOf(System.currentTimeMillis()))));

        statsFlush.flush();
        dimensionFlush.flush();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT pv, uv FROM link_stats_daily WHERE tenant_id = 41 AND link_id = 9001 AND day = ?",
                day
        );
        assertThat(((Number) row.get("pv")).longValue()).isEqualTo(3L);
        assertThat(((Number) row.get("uv")).longValue()).isEqualTo(2L);
        assertThat(redis.opsForHash().hasKey(markerKey, member)).isFalse();
        PendingMessagesSummary pending = redis.opsForStream()
                .pending(AnalyticsKeys.statsDirtyStreamKey(day), "lf-stats-flush");
        assertThat(pending.getTotalPendingMessages()).isZero();

        Long dimensionPv = jdbc.queryForObject(
                """
                        SELECT pv FROM link_stats_dim_daily
                        WHERE tenant_id = 41 AND link_id = 9001 AND day = ?
                          AND dim_type = 'language' AND dim_value = 'en-US'
                        """,
                Long.class,
                day
        );
        assertThat(dimensionPv).isEqualTo(2L);

        statsFlush.flush();
        assertThat(jdbc.queryForObject(
                "SELECT pv FROM link_stats_daily WHERE tenant_id = 41 AND link_id = 9001 AND day = ?",
                Long.class,
                day
        )).isEqualTo(3L);
    }

    @Test
    void claim_shouldEventuallyReachColdTailWhileEarlierMarkersKeepConflicting() {
        String markerKey = "stats:dirty:v2:link:fairness";
        String firstSeenKey = markerKey + ":first-seen";
        int markerCount = 64;
        int limit = 8;
        Map<String, String> generations = new LinkedHashMap<>();
        Map<String, String> firstSeen = new LinkedHashMap<>();
        for (int i = 0; i < markerCount; i++) {
            String member = "41:" + (10_000 + i);
            generations.put(member, "1");
            firstSeen.put(member, String.valueOf(1_710_000_000_000L + i));
        }
        redis.opsForHash().putAll(markerKey, generations);
        redis.opsForHash().putAll(firstSeenKey, firstSeen);

        Set<String> claimedMembers = new HashSet<>();
        for (int round = 0; round < markerCount && claimedMembers.size() < markerCount; round++) {
            VersionedDirtyMarkerStore markerStore = new VersionedDirtyMarkerStore(redis);
            List<VersionedDirtyMarkerStore.Claim> claims = markerStore.claim(markerKey, firstSeenKey, limit);

            assertThat(claims).hasSizeLessThanOrEqualTo(limit);
            claimedMembers.addAll(claims.stream().map(VersionedDirtyMarkerStore.Claim::member).toList());
            claims.forEach(claim -> redis.opsForHash().increment(markerKey, claim.member(), 1L));
            assertThat(markerStore.complete(markerKey, firstSeenKey, claims))
                    .isEqualTo(new VersionedDirtyMarkerStore.Completion(0L, claims.size()));
        }

        assertThat(claimedMembers).containsExactlyInAnyOrderElementsOf(generations.keySet());
    }

    private static RedirectVisitRecord visit(long occurredAt, String ip) {
        return new RedirectVisitRecord(
                41L,
                9_001L,
                occurredAt,
                501L,
                601L,
                "marker-e2e",
                "https://example.com/target",
                new VisitContext(ip, "Mozilla/5.0", "https://ref.example/path", "en-US", Map.of())
        );
    }
}

package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证真实 Redis Lua 投影契约，包括 at-least-once 重放。
 * 记录先进入 consumer-group pending；随后模拟 Lua 已提交但进程在 ACK 前退出，再由 projector 重放并确认消息。
 */
class AnalyticsProjectionReplayIntegrationTest extends SharedIntegrationTestSupport {

    private static final String GROUP = "lf-visit-projector";
    private static final String CONSUMER = "lf-visit-projector-consumer";

    @Test
    void replayed_stream_record_should_not_overcount_aggregates_or_dirty_markers() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        String streamKey = AnalyticsKeys.visitEventStreamKey();
        LocalDate day = LocalDate.now(ZoneOffset.UTC);

        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("requestId", "replay-request-1");
            event.put("ts", String.valueOf(day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()));
            event.put("tenantId", "41");
            event.put("linkId", "9001");
            event.put("applicationId", "501");
            event.put("domainId", "601");
            event.put("visitorKey", "visitor-1");
            event.put("language", "zh-CN");

            RecordId id = redis.opsForStream().add(
                    StreamRecords.newRecord().in(streamKey).ofStrings(event));
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), GROUP);
            MapRecord<String, Object, Object> record = redis.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            ).get(0);
            assertThat(record.getId()).isEqualTo(id);
            PendingMessagesSummary pendingBeforeProjection = redis.opsForStream().pending(streamKey, GROUP);
            assertThat(pendingBeforeProjection.getTotalPendingMessages()).isEqualTo(1L);

            AnalyticsProperties properties = new AnalyticsProperties();
            properties.setRedisKeyTtlDays(7);
            properties.getDimensions().setEnabled(true);
            properties.getDimensions().setTypes(List.of("language"));
            AnalyticsRedisAggregateWriter writer = new AnalyticsRedisAggregateWriter(redis, properties);
            AnalyticsRedirectEventProjectorJob projector = new AnalyticsRedirectEventProjectorJob(
                    redis, properties, writer);

            writer.write(event);
            assertThat(redis.opsForStream().pending(streamKey, GROUP).getTotalPendingMessages()).isEqualTo(1L);
            assertThat(projector.projectRecords(streamKey, List.of(record))).isTrue();

            assertThat(redis.opsForValue().get(AnalyticsKeys.pvKey(41, 9001, day))).isEqualTo("1");
            assertThat(redis.opsForHyperLogLog().size(AnalyticsKeys.uvKey(41, 9001, day))).isEqualTo(1L);
            assertThat(redis.opsForHash().get(
                    AnalyticsKeys.dimPvHashKey(41, 9001, day, "language"), "zh-CN"
            )).isEqualTo("1");
            assertThat(redis.opsForHash().get(
                    AnalyticsKeys.statsDirtyMarkerV2Key(day), AnalyticsKeys.dirtyLinkMember(41, 9001)
            )).isEqualTo("1");
            assertThat(redis.opsForHash().size(AnalyticsKeys.scopeDirtyMarkerV2Key(day))).isEqualTo(3L);
            assertThat(redis.opsForHash().get(
                    AnalyticsKeys.dimDirtyMarkerV2Key(day), AnalyticsKeys.dirtyLinkMember(41, 9001)
            )).isEqualTo("1");
            assertThat(redis.opsForValue().get(AnalyticsKeys.projectionDedupKey("replay-request-1")))
                    .isEqualTo("1");
            assertThat(redis.opsForStream().pending(streamKey, GROUP).getTotalPendingMessages()).isZero();
            assertThat(redis.getExpire(AnalyticsKeys.pvKey(41, 9001, day))).isPositive();
            assertThat(redis.getExpire(AnalyticsKeys.uvKey(41, 9001, day))).isPositive();
            assertThat(redis.getExpire(AnalyticsKeys.dimPvHashKey(41, 9001, day, "language"))).isPositive();
            assertThat(redis.getExpire(AnalyticsKeys.statsDirtyMarkerV2Key(day))).isPositive();
            assertThat(redis.getExpire(AnalyticsKeys.projectionDedupKey("replay-request-1"))).isPositive();
        } finally {
            connectionFactory.destroy();
        }
    }
}

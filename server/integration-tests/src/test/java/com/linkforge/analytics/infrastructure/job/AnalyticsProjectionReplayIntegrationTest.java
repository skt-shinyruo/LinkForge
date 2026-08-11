package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the real Redis Lua projection contract, including at-least-once replay.
 * The first call represents a projection that completed before its ACK was lost;
 * the same stream record is then delivered again and must be a no-op.
 */
@Testcontainers
class AnalyticsProjectionReplayIntegrationTest {

    private static final String GROUP = "lf-visit-projector";
    private static final String CONSUMER = "lf-visit-projector-consumer";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6.2-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)))
            .withStartupAttempts(3);

    @Test
    void replay_after_projection_before_ack_should_not_overcount_pv_or_dirty_signals() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        String streamKey = AnalyticsKeys.visitEventStreamKey();
        LocalDate day = LocalDate.now(ZoneOffset.UTC);

        try {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
            Map<String, String> event = new LinkedHashMap<>();
            event.put("requestId", "replay-request-1");
            event.put("ts", String.valueOf(day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()));
            event.put("tenantId", "41");
            event.put("linkId", "9001");
            event.put("applicationId", "501");
            event.put("domainId", "601");
            event.put("visitorKey", "visitor-1");

            RecordId id = redis.opsForStream().add(
                    StreamRecords.newRecord().in(streamKey).ofStrings(event));
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), GROUP);
            MapRecord<String, Object, Object> record = redis.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            ).get(0);
            assertThat(record.getId()).isEqualTo(id);

            AnalyticsProperties properties = new AnalyticsProperties();
            properties.setRedisKeyTtlDays(7);
            AnalyticsRedisAggregateWriter writer = new AnalyticsRedisAggregateWriter(redis, properties);
            AnalyticsRedirectEventProjectorJob projector = new AnalyticsRedirectEventProjectorJob(
                    redis, properties, writer);

            assertThat(projector.projectRecords(streamKey, List.of(record))).isTrue();
            assertThat(projector.projectRecords(streamKey, List.of(record))).isTrue();

            assertThat(redis.opsForValue().get(AnalyticsKeys.pvKey(41, 9001, day))).isEqualTo("1");
            assertThat(redis.opsForHyperLogLog().size(AnalyticsKeys.uvKey(41, 9001, day))).isEqualTo(1L);
            assertThat(redis.opsForStream().size(AnalyticsKeys.statsDirtyStreamKey(day))).isEqualTo(1L);
            assertThat(redis.opsForStream().size(AnalyticsKeys.scopeDirtyStreamKey(day))).isEqualTo(3L);
            assertThat(redis.opsForValue().get(AnalyticsKeys.projectionDedupKey("replay-request-1")))
                    .isEqualTo("1");
        } finally {
            connectionFactory.destroy();
        }
    }
}

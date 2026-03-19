package com.linkforge.analytics.infrastructure;

import com.linkforge.analytics.application.VisitorFingerprint;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.web.RequestId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitRecorderServiceTest {

    @Test
    void recordVisit_should_use_server_owned_event_request_id_and_enqueue_dirty_streams() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setSalt("salt-test");
        properties.setRedisKeyTtlDays(7);
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(java.util.List.of("referer_domain"));
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(1.0d);
        properties.getEvents().setStreamMaxLen(0L);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        @SuppressWarnings("unchecked")
        HyperLogLogOperations<String, String> hllOps = mock(HyperLogLogOperations.class);
        when(redis.opsForHyperLogLog()).thenReturn(hllOps);
        when(hllOps.add(anyString(), any(String[].class))).thenReturn(1L);

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.add(anyString(), any(String[].class))).thenReturn(1L);

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment(anyString(), any(), eq(1L))).thenReturn(1L);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(
                RecordId.of("1-0"),
                RecordId.of("2-0"),
                RecordId.of("3-0")
        );

        when(redis.expireAt(anyString(), any(Date.class))).thenReturn(true);

        VisitRecorderService service = new VisitRecorderService(redis, properties);
        VisitContext visitContext = new VisitContext(
                "1.2.3.4",
                "ua-test",
                "https://Example.COM/p",
                "zh-CN,zh;q=0.9",
                Map.of()
        );

        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String dayRaw = day.format(DateTimeFormatter.BASIC_ISO_DATE);
        String statsDirtyStreamKey = "stats:dirty:flush:" + dayRaw;
        String dimDirtyStreamKey = "stats:dirty:dim:" + dayRaw;

        RequestId.set("client-request-id");
        try {
            service.recordVisit(1L, 2L, visitContext);
        } finally {
            RequestId.clear();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<MapRecord> recordCaptor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOps, times(3)).add(recordCaptor.capture());

        @SuppressWarnings("unchecked")
        java.util.List<MapRecord<String, Object, Object>> records = recordCaptor.getAllValues().stream()
                .map(record -> (MapRecord<String, Object, Object>) record)
                .toList();

        MapRecord<String, Object, Object> eventRecord = records.stream()
                .filter(record -> AnalyticsKeys.visitEventStreamKey().equals(record.getStream()))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(eventRecord.getValue().get("requestId")))
                .isNotBlank()
                .isNotEqualTo("client-request-id");

        MapRecord<String, Object, Object> statsDirtyRecord = records.stream()
                .filter(record -> statsDirtyStreamKey.equals(record.getStream()))
                .findFirst()
                .orElseThrow();
        assertThat(statsDirtyRecord.getValue()).containsEntry("member", "1:2");

        MapRecord<String, Object, Object> dimDirtyRecord = records.stream()
                .filter(record -> dimDirtyStreamKey.equals(record.getStream()))
                .findFirst()
                .orElseThrow();
        assertThat(dimDirtyRecord.getValue()).containsEntry("member", "1:2");

        verify(redis).expireAt(eq(statsDirtyStreamKey), any(Date.class));
        verify(redis).expireAt(eq(dimDirtyStreamKey), any(Date.class));
    }

    @Test
    void recordVisit_should_retry_expireAt_on_later_visits_even_when_not_first_pv_or_new_uv_or_new_active() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setRedisKeyTtlDays(7);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L, 2L);

        @SuppressWarnings("unchecked")
        HyperLogLogOperations<String, String> hllOps = mock(HyperLogLogOperations.class);
        when(redis.opsForHyperLogLog()).thenReturn(hllOps);
        when(hllOps.add(anyString(), any(String[].class))).thenReturn(1L, 0L);

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.add(anyString(), any(String[].class))).thenReturn(1L, 0L);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1-0"), RecordId.of("2-0"));

        // Simulate expireAt failing (no exception). We only care that it gets retried.
        when(redis.expireAt(anyString(), any(Date.class))).thenReturn(false);

        VisitRecorderService service = new VisitRecorderService(redis, properties);
        VisitContext visitContext = new VisitContext("1.2.3.4", "ua-test", null, null, Map.of());

        service.recordVisit(1L, 2L, visitContext);
        service.recordVisit(1L, 2L, visitContext);

        ArgumentCaptor<String> pvKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(2)).increment(pvKeyCaptor.capture());
        String pvKey = pvKeyCaptor.getAllValues().get(0);

        ArgumentCaptor<String> uvKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(hllOps, times(2)).add(uvKeyCaptor.capture(), any(String[].class));
        String uvKey = uvKeyCaptor.getAllValues().get(0);

        ArgumentCaptor<String> activeKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(setOps, times(2)).add(activeKeyCaptor.capture(), any(String[].class));
        String activeKey = activeKeyCaptor.getAllValues().get(0);

        verify(redis, times(2)).expireAt(eq(pvKey), any(Date.class));
        verify(redis, times(2)).expireAt(eq(uvKey), any(Date.class));
        verify(redis, times(2)).expireAt(eq(activeKey), any(Date.class));
    }

    @Test
    void recordVisit_when_dimensions_enabled_should_pfadd_dim_uv_hll_using_same_visitor_fingerprint() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setSalt("salt-test");
        properties.setRedisKeyTtlDays(7);
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(java.util.List.of("referer_domain"));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        @SuppressWarnings("unchecked")
        HyperLogLogOperations<String, String> hllOps = mock(HyperLogLogOperations.class);
        when(redis.opsForHyperLogLog()).thenReturn(hllOps);
        when(hllOps.add(anyString(), any(String[].class))).thenReturn(1L);

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.add(anyString(), any(String[].class))).thenReturn(1L);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(
                RecordId.of("1-0"),
                RecordId.of("2-0"),
                RecordId.of("3-0")
        );

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment(anyString(), any(), eq(1L))).thenReturn(1L);

        when(redis.expireAt(anyString(), any(Date.class))).thenReturn(true);

        VisitRecorderService service = new VisitRecorderService(redis, properties);
        VisitContext visitContext = new VisitContext(
                "1.2.3.4",
                "ua-test",
                "https://Example.COM/p",
                "zh-CN,zh;q=0.9",
                Map.of()
        );

        long tenantId = 1L;
        long linkId = 2L;

        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String visitor = VisitorFingerprint.fingerprint(day, visitContext, properties.getSalt());
        String expectedDimUvKey = AnalyticsKeys.dimUvHllKey(tenantId, linkId, day, "referer_domain", "example.com");

        service.recordVisit(tenantId, linkId, visitContext);

        ArgumentCaptor<String> hllKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String[]> hllValuesCaptor = ArgumentCaptor.forClass(String[].class);
        verify(hllOps, times(2)).add(hllKeyCaptor.capture(), hllValuesCaptor.capture());

        assertThat(hllKeyCaptor.getAllValues())
                .anyMatch(k -> k != null && k.equals(expectedDimUvKey));

        assertThat(hllValuesCaptor.getAllValues())
                .hasSize(2)
                .allSatisfy(v -> assertThat(v).containsExactly(visitor));
    }
}

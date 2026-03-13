package com.linkforge.analytics.application;

import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitRecorderServiceTest {

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
}


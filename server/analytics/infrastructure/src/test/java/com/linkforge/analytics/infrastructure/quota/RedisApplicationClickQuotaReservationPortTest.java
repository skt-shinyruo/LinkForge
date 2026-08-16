package com.linkforge.analytics.infrastructure.quota;

import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsQueryMapper;
import com.linkforge.contract.analytics.AnalyticsKeys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisApplicationClickQuotaReservationPortTest {

    @Test
    void tryReserveMonthlyClick_shouldSeedRedisCounterFromMysqlBaselineAndReturnScriptDecision() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsQueryMapper queryMapper = mock(AnalyticsQueryMapper.class);
        when(queryMapper.countApplicationPv(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01")
        )).thenReturn(9L);
        when(redis.execute(
                any(DefaultRedisScript.class),
                eq(List.of(AnalyticsKeys.applicationClickQuotaKey(22L, 33L, LocalDate.parse("2026-04-01")))),
                eq("10"),
                eq(String.valueOf(LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
        )).thenReturn(-1L);
        when(redis.execute(
                any(DefaultRedisScript.class),
                eq(List.of(AnalyticsKeys.applicationClickQuotaKey(22L, 33L, LocalDate.parse("2026-04-01")))),
                eq("10"),
                eq("9"),
                eq(String.valueOf(LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
        )).thenReturn(1L);
        RedisApplicationClickQuotaReservationPort port = new RedisApplicationClickQuotaReservationPort(
                redis,
                queryMapper
        );

        boolean reserved = port.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        );

        assertThat(reserved).isTrue();
        verify(queryMapper).countApplicationPv(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01")
        );
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<DefaultRedisScript> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redis).execute(
                scriptCaptor.capture(),
                eq(List.of("quota:click:application:22:33:202604")),
                eq("10"),
                eq("9"),
                eq(String.valueOf(LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
        );
        assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
    }

    @Test
    void tryReserveMonthlyClick_shouldNotQueryMysqlWhenRedisCounterExists() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsQueryMapper queryMapper = mock(AnalyticsQueryMapper.class);
        when(redis.execute(
                any(DefaultRedisScript.class),
                eq(List.of(AnalyticsKeys.applicationClickQuotaKey(22L, 33L, LocalDate.parse("2026-04-01")))),
                eq("10"),
                eq(String.valueOf(LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
        )).thenReturn(1L);
        RedisApplicationClickQuotaReservationPort port = new RedisApplicationClickQuotaReservationPort(
                redis,
                queryMapper
        );

        boolean reserved = port.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        );

        assertThat(reserved).isTrue();
        verifyNoInteractions(queryMapper);
    }

    @Test
    void tryReserveMonthlyClick_shouldRejectWhenRedisScriptRejects() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsQueryMapper queryMapper = mock(AnalyticsQueryMapper.class);
        when(queryMapper.countApplicationPv(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01")
        )).thenReturn(10L);
        when(redis.execute(
                any(DefaultRedisScript.class),
                any(List.class),
                eq("10"),
                any(String.class)
        )).thenReturn(-1L);
        when(redis.execute(any(DefaultRedisScript.class), any(List.class), eq("10"), eq("10"), any(String.class)))
                .thenReturn(0L);
        RedisApplicationClickQuotaReservationPort port = new RedisApplicationClickQuotaReservationPort(
                redis,
                queryMapper
        );

        boolean reserved = port.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        );

        assertThat(reserved).isFalse();
    }

    @Test
    void tryReserveMonthlyClick_shouldFailOpenWhenRedisReservationFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsQueryMapper queryMapper = mock(AnalyticsQueryMapper.class);
        when(queryMapper.countApplicationPv(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01")
        )).thenReturn(9L);
        when(redis.execute(any(DefaultRedisScript.class), any(List.class), eq("10"), any(String.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        RedisApplicationClickQuotaReservationPort port = new RedisApplicationClickQuotaReservationPort(
                redis,
                queryMapper
        );

        boolean reserved = port.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        );

        assertThat(reserved).isTrue();
    }

    @Test
    void tryReserveMonthlyClick_shouldFailOpenWithoutMysqlQueryWhenRedisReturnsNull() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsQueryMapper queryMapper = mock(AnalyticsQueryMapper.class);
        when(redis.execute(
                any(DefaultRedisScript.class),
                eq(List.of(AnalyticsKeys.applicationClickQuotaKey(22L, 33L, LocalDate.parse("2026-04-01")))),
                eq("10"),
                eq(String.valueOf(LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
        )).thenReturn(null);
        RedisApplicationClickQuotaReservationPort port = new RedisApplicationClickQuotaReservationPort(
                redis,
                queryMapper
        );

        boolean reserved = port.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        );

        assertThat(reserved).isTrue();
        verifyNoInteractions(queryMapper);
    }
}

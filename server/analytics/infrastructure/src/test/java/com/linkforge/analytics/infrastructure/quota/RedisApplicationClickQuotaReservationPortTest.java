package com.linkforge.analytics.infrastructure.quota;

import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
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
import static org.mockito.Mockito.when;

class RedisApplicationClickQuotaReservationPortTest {

    @Test
    void tryReserveMonthlyClick_shouldSeedRedisCounterFromMysqlBaselineAndReturnScriptDecision() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        when(queryRepository.countApplicationPv(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01")
        )).thenReturn(9L);
        when(redis.execute(
                any(DefaultRedisScript.class),
                eq(List.of(AnalyticsKeys.applicationClickQuotaKey(22L, 33L, LocalDate.parse("2026-04-01")))),
                eq("10"),
                eq("9"),
                eq(String.valueOf(LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
        )).thenReturn(1L);
        RedisApplicationClickQuotaReservationPort port = new RedisApplicationClickQuotaReservationPort(
                redis,
                queryRepository
        );

        boolean reserved = port.tryReserveMonthlyClick(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01"),
                10L
        );

        assertThat(reserved).isTrue();
        verify(queryRepository).countApplicationPv(
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
    void tryReserveMonthlyClick_shouldRejectWhenRedisScriptRejects() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        when(queryRepository.countApplicationPv(
                22L,
                33L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-05-01")
        )).thenReturn(10L);
        when(redis.execute(any(DefaultRedisScript.class), any(List.class), eq("10"), eq("10"), any(String.class)))
                .thenReturn(0L);
        RedisApplicationClickQuotaReservationPort port = new RedisApplicationClickQuotaReservationPort(
                redis,
                queryRepository
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
}

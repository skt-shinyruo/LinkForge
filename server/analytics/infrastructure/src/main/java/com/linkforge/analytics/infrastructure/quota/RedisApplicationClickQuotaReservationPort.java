package com.linkforge.analytics.infrastructure.quota;

import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.contract.analytics.ApplicationClickQuotaReservationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class RedisApplicationClickQuotaReservationPort implements ApplicationClickQuotaReservationPort {

    private static final Logger log = LoggerFactory.getLogger(RedisApplicationClickQuotaReservationPort.class);

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            """
                    local limit = tonumber(ARGV[1])
                    local baseline = tonumber(ARGV[2])
                    local expireAt = tonumber(ARGV[3])

                    if limit == nil or limit <= 0 then
                      return 1
                    end
                    if baseline == nil or baseline < 0 then
                      baseline = 0
                    end

                    if redis.call('EXISTS', KEYS[1]) == 0 then
                      redis.call('SET', KEYS[1], baseline)
                      if expireAt ~= nil and expireAt > 0 then
                        redis.call('EXPIREAT', KEYS[1], expireAt)
                      end
                    end

                    local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if current >= limit then
                      return 0
                    end

                    current = redis.call('INCR', KEYS[1])
                    if current > limit then
                      return 0
                    end
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redis;
    private final AnalyticsQueryRepository queryRepository;

    public RedisApplicationClickQuotaReservationPort(
            StringRedisTemplate redis,
            AnalyticsQueryRepository queryRepository
    ) {
        this.redis = redis;
        this.queryRepository = queryRepository;
    }

    @Override
    public boolean tryReserveMonthlyClick(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc,
            long monthlyClickLimit
    ) {
        if (monthlyClickLimit <= 0) {
            return true;
        }
        if (tenantId <= 0 || applicationId <= 0 || fromInclusiveUtc == null || toExclusiveUtc == null
                || !toExclusiveUtc.isAfter(fromInclusiveUtc)) {
            return false;
        }

        long baseline = Math.max(0L, queryRepository.countApplicationPv(
                tenantId,
                applicationId,
                fromInclusiveUtc,
                toExclusiveUtc
        ));
        String key = AnalyticsKeys.applicationClickQuotaKey(tenantId, applicationId, fromInclusiveUtc);
        long expireAtEpochSecond = toExclusiveUtc.plusDays(2).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        try {
            Long result = redis.execute(
                    SCRIPT,
                    List.of(key),
                    String.valueOf(monthlyClickLimit),
                    String.valueOf(baseline),
                    String.valueOf(expireAtEpochSecond)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.debug(
                    "redis application click quota reservation failed: tenantId={}, applicationId={}, monthStart={}, err={}",
                    tenantId,
                    applicationId,
                    fromInclusiveUtc,
                    e.getMessage()
            );
            return false;
        }
    }
}

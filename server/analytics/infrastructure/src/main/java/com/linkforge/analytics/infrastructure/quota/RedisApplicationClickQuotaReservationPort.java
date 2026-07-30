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

/**
 * 以 Redis 原子脚本预留应用月度点击额度的适配器。
 *
 * <p>计数器首次缺失时先从 MySQL 日表读取已累计 PV，再在一个 Lua 脚本中初始化并预留一次点击，避免
 * 多实例竞争造成明显超额。计数器保留到月末后两天，给异步日表回填留出缓冲。</p>
 *
 * <p>这是可用性优先的控制点：Redis、MySQL 基线查询、脚本返回异常或未知结果都会 fail-open，允许
 * 重定向继续；只有脚本明确返回额度耗尽才拒绝。因而它不能作为精确计费或硬性反作弊边界。</p>
 */
@Component
public class RedisApplicationClickQuotaReservationPort implements ApplicationClickQuotaReservationPort {

    private static final Logger log = LoggerFactory.getLogger(RedisApplicationClickQuotaReservationPort.class);

    private static final long SCRIPT_REJECTED = 0L;
    private static final long SCRIPT_RESERVED = 1L;
    private static final long SCRIPT_COUNTER_MISSING = -1L;

    private static final DefaultRedisScript<Long> RESERVE_EXISTING_COUNTER_SCRIPT = new DefaultRedisScript<>(
            """
                    local limit = tonumber(ARGV[1])
                    local expireAt = tonumber(ARGV[2])

                    if limit == nil or limit <= 0 then
                      return 1
                    end
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                      return -1
                    end

                    local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if current >= limit then
                      return 0
                    end

                    current = redis.call('INCR', KEYS[1])
                    if expireAt ~= nil and expireAt > 0 then
                      redis.call('EXPIREAT', KEYS[1], expireAt)
                    end
                    if current > limit then
                      return 0
                    end
                    return 1
                    """,
            Long.class
    );

    private static final DefaultRedisScript<Long> SEED_AND_RESERVE_SCRIPT = new DefaultRedisScript<>(
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

    /**
     * 尝试为一次点击预留配额。
     *
     * @return {@code false} 仅表示 Redis 脚本明确判定已达上限；无上限、无效参数和基础设施失败均允许
     */
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
            return true;
        }

        String key = AnalyticsKeys.applicationClickQuotaKey(tenantId, applicationId, fromInclusiveUtc);
        long expireAtEpochSecond = toExclusiveUtc.plusDays(2).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        Long existingCounterResult;
        try {
            existingCounterResult = redis.execute(
                    RESERVE_EXISTING_COUNTER_SCRIPT,
                    List.of(key),
                    String.valueOf(monthlyClickLimit),
                    String.valueOf(expireAtEpochSecond)
            );
        } catch (Exception e) {
            log.debug(
                    "redis application click quota reservation failed; allow redirect: tenantId={}, applicationId={}, monthStart={}, err={}",
                    tenantId,
                    applicationId,
                    fromInclusiveUtc,
                    e.getMessage()
            );
            return true;
        }
        if (existingCounterResult == null) {
            log.debug(
                    "redis application click quota reservation returned null; allow redirect: tenantId={}, applicationId={}, monthStart={}",
                    tenantId,
                    applicationId,
                    fromInclusiveUtc
            );
            return true;
        }
        if (existingCounterResult == SCRIPT_RESERVED) {
            return true;
        }
        if (existingCounterResult == SCRIPT_REJECTED) {
            return false;
        }
        if (existingCounterResult != SCRIPT_COUNTER_MISSING) {
            log.debug(
                    "redis application click quota reservation returned unexpected result; allow redirect: tenantId={}, applicationId={}, monthStart={}, result={}",
                    tenantId,
                    applicationId,
                    fromInclusiveUtc,
                    existingCounterResult
            );
            return true;
        }

        long baseline;
        try {
            baseline = Math.max(0L, queryRepository.countApplicationPv(
                    tenantId,
                    applicationId,
                    fromInclusiveUtc,
                    toExclusiveUtc
            ));
        } catch (Exception e) {
            log.debug(
                    "application click quota baseline query failed; allow redirect: tenantId={}, applicationId={}, monthStart={}, err={}",
                    tenantId,
                    applicationId,
                    fromInclusiveUtc,
                    e.getMessage()
            );
            return true;
        }

        try {
            Long seededResult = redis.execute(
                    SEED_AND_RESERVE_SCRIPT,
                    List.of(key),
                    String.valueOf(monthlyClickLimit),
                    String.valueOf(baseline),
                    String.valueOf(expireAtEpochSecond)
            );
            if (seededResult != null && seededResult == SCRIPT_REJECTED) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug(
                    "redis application click quota reservation failed; allow redirect: tenantId={}, applicationId={}, monthStart={}, err={}",
                    tenantId,
                    applicationId,
                    fromInclusiveUtc,
                    e.getMessage()
            );
            return true;
        }
    }
}

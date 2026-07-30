package com.linkforge.redirect.infrastructure.risk;

import com.linkforge.redirect.application.risk.RateLimiterPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis 的固定窗口计数限流器（原子 {@code INCR + EXPIRE}）。
 *
 * <p>Lua 保证只有计数器第一次创建时设置 TTL，避免并发请求把窗口不断向后延长。Redis 异常按原样向上
 * 抛出，由应用层的 risk-control fail-open 策略决定响应，不能在这里静默放行。</p>
 */
@Component
public class RedisFixedWindowRateLimiter implements RateLimiterPort {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            """
                    local c = redis.call('INCR', KEYS[1])
                    if c == 1 then
                      redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    return c
                    """,
            Long.class
    );

    private final StringRedisTemplate redis;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 对当前窗口 key 执行原子递增。
     *
     * <p>无效参数不访问 Redis 并返回 {@code 0}；其余 Redis 异常保留给调用方，以便实现配置的
     * fail-open/fail-closed 决策。</p>
     */
    @Override
    public long increment(String key, int ttlSeconds) {
        if (key == null || key.isBlank() || ttlSeconds <= 0) {
            return 0L;
        }
        Long r = redis.execute(SCRIPT, List.of(key), String.valueOf(ttlSeconds));
        return r == null ? 0L : r;
    }
}

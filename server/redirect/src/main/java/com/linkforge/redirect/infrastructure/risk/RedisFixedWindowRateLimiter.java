package com.linkforge.redirect.infrastructure.risk;

import com.linkforge.redirect.application.risk.RateLimiterPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis 的固定窗口计数限流器（原子 INCR + EXPIRE）。
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

    @Override
    public long increment(String key, int ttlSeconds) {
        if (key == null || key.isBlank() || ttlSeconds <= 0) {
            return 0L;
        }
        Long r = redis.execute(SCRIPT, List.of(key), String.valueOf(ttlSeconds));
        return r == null ? 0L : r;
    }
}

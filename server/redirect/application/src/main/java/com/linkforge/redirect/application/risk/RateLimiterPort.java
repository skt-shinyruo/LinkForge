package com.linkforge.redirect.application.risk;

/**
 * Rate limiter port for Redirect Edge risk control.
 *
 * <p>Application layer depends on this abstraction; infrastructure provides an implementation (e.g. Redis).</p>
 */
public interface RateLimiterPort {

    /**
     * Atomically increments a counter identified by {@code key} and sets TTL when first seen.
     *
     * @param key        unique key for the current window
     * @param ttlSeconds TTL seconds for the counter key
     * @return current counter value after increment, or 0 if disabled/invalid params
     */
    long increment(String key, int ttlSeconds);
}


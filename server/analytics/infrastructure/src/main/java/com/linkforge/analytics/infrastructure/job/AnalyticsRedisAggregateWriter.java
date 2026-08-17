package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Writes one redirect directly to the Redis PV/UV aggregates. */
@Component
public class AnalyticsRedisAggregateWriter {

    private static final DefaultRedisScript<Long> WRITE_SCRIPT = new DefaultRedisScript<>("""
            local ttl = tonumber(ARGV[1]) or 0
            local visitor = ARGV[2] or ''
            local scopeCount = tonumber(ARGV[3]) or 0

            if redis.call('SETNX', KEYS[1], '1') == 0 then
                return 0
            end

            local argIndex = 4
            local scopeMembers = {}
            for i = 1, scopeCount do
                scopeMembers[i] = ARGV[argIndex]
                argIndex = argIndex + 1
            end
            local dirtyMember = ARGV[argIndex]
            local eventTs = ARGV[argIndex + 1]

            local keyIndex = 2
            redis.call('INCR', KEYS[keyIndex])
            keyIndex = keyIndex + 1
            if visitor ~= '' then
                redis.call('PFADD', KEYS[keyIndex], visitor)
                keyIndex = keyIndex + 1
                for i = 1, scopeCount do
                    redis.call('PFADD', KEYS[keyIndex], visitor)
                    keyIndex = keyIndex + 1
                end
            end

            local statsMarker = KEYS[keyIndex]
            local statsFirstSeen = KEYS[keyIndex + 1]
            local scopeMarker = KEYS[keyIndex + 2]
            local scopeFirstSeen = KEYS[keyIndex + 3]
            redis.call('HINCRBY', statsMarker, dirtyMember, 1)
            redis.call('HSETNX', statsFirstSeen, dirtyMember, eventTs)
            for i = 1, scopeCount do
                redis.call('HINCRBY', scopeMarker, scopeMembers[i], 1)
                redis.call('HSETNX', scopeFirstSeen, scopeMembers[i], eventTs)
            end

            if ttl > 0 then
                for i = 1, #KEYS do
                    redis.call('EXPIRE', KEYS[i], ttl)
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AnalyticsProperties properties;
    private final OperationalMetrics metrics;

    public AnalyticsRedisAggregateWriter(StringRedisTemplate redis, AnalyticsProperties properties) {
        this(redis, properties, OperationalMetrics.noop());
    }

    @Autowired
    public AnalyticsRedisAggregateWriter(
            StringRedisTemplate redis,
            AnalyticsProperties properties,
            OperationalMetrics metrics
    ) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * Atomically deduplicates a request and updates link and scope aggregates.
     * Redis failures propagate to the fail-open boundary in AnalyticsVisitEventService.
     */
    public void write(
            long tenantId,
            long linkId,
            long eventMillis,
            Long applicationId,
            Long domainId,
            String visitor,
            String requestId
    ) {
        if (redis == null || tenantId <= 0L || linkId <= 0L || requestId == null || requestId.isBlank()) {
            return;
        }
        long occurredAt = eventMillis > 0L ? eventMillis : System.currentTimeMillis();
        LocalDate day = java.time.Instant.ofEpochMilli(occurredAt).atOffset(ZoneOffset.UTC).toLocalDate();
        String visitorKey = visitor == null || visitor.isBlank() ? null : visitor.trim();
        List<Scope> scopes = scopes(tenantId, applicationId, domainId, day, visitorKey);

        List<String> keys = new ArrayList<>(8 + scopes.size());
        keys.add(AnalyticsKeys.projectionDedupKey(requestId));
        keys.add(AnalyticsKeys.pvKey(tenantId, linkId, day));
        if (visitorKey != null) {
            keys.add(AnalyticsKeys.uvKey(tenantId, linkId, day));
            scopes.stream().map(Scope::uvKey).forEach(keys::add);
        }
        keys.add(AnalyticsKeys.statsDirtyMarkerV2Key(day));
        keys.add(AnalyticsKeys.statsDirtyMarkerV2FirstSeenKey(day));
        keys.add(AnalyticsKeys.scopeDirtyMarkerV2Key(day));
        keys.add(AnalyticsKeys.scopeDirtyMarkerV2FirstSeenKey(day));

        List<String> args = new ArrayList<>(6 + scopes.size());
        args.add(String.valueOf(ttlSeconds(day)));
        args.add(visitorKey == null ? "" : visitorKey);
        args.add(String.valueOf(scopes.size()));
        scopes.stream().map(Scope::member).forEach(args::add);
        args.add(AnalyticsKeys.dirtyLinkMember(tenantId, linkId));
        args.add(String.valueOf(System.currentTimeMillis()));

        long startedAt = System.nanoTime();
        try {
            Long applied = redis.execute(WRITE_SCRIPT, keys, args.toArray());
            if (applied == null) {
                throw new IllegalStateException("analytics aggregate script returned null");
            }
            String result = applied == 0L ? "deduplicated" : "applied";
            metrics.increment("linkforge.analytics.projection.events", "result", result);
            metrics.record("linkforge.analytics.projection.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt), "result", result);
        } catch (RuntimeException failure) {
            metrics.increment("linkforge.analytics.projection.events", "result", "failure");
            throw failure;
        }
    }

    private List<Scope> scopes(long tenantId, Long applicationId, Long domainId, LocalDate day, String visitor) {
        if (visitor == null) {
            return List.of();
        }
        List<Scope> scopes = new ArrayList<>(3);
        scopes.add(new Scope(AnalyticsKeys.tenantScopeUvKey(tenantId, day), AnalyticsKeys.tenantScopeMember(tenantId)));
        if (applicationId != null && applicationId > 0L) {
            scopes.add(new Scope(AnalyticsKeys.applicationScopeUvKey(tenantId, applicationId, day),
                    AnalyticsKeys.applicationScopeMember(tenantId, applicationId)));
        }
        if (domainId != null && domainId > 0L) {
            scopes.add(new Scope(AnalyticsKeys.domainScopeUvKey(tenantId, domainId, day),
                    AnalyticsKeys.domainScopeMember(tenantId, domainId)));
        }
        return scopes;
    }

    private long ttlSeconds(LocalDate day) {
        Date expiresAt = expiresAt(day);
        return expiresAt == null ? 0L : Math.max((expiresAt.getTime() - System.currentTimeMillis() + 999L) / 1_000L, 1L);
    }

    private Date expiresAt(LocalDate day) {
        long days = properties == null ? 0L : properties.getRedisKeyTtlDays();
        return days <= 0L ? null : Date.from(day.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private record Scope(String uvKey, String member) {
    }
}

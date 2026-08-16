package com.linkforge.redirect.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinkCacheServiceTest {

    private static final String HOST = "go.example.test";
    private static final String CODE = "abc123";
    private static final String CACHE_KEY = "link:host:" + HOST + ":code:" + CODE;

    @Test
    void lookup_shouldReturnMissWhenRedisReadFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(CACHE_KEY)).thenThrow(new IllegalStateException("redis unavailable"));

        LinkCachePort.LookupResult result = cache(redis).lookup(HOST, CODE);

        assertThat(result).isEqualTo(LinkCachePort.LookupResult.miss());
    }

    @Test
    void lookup_shouldReturnMissWhenCachedValueIsMalformed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(CACHE_KEY)).thenReturn("{not-json");

        LinkCachePort.LookupResult result = cache(redis).lookup(HOST, CODE);

        assertThat(result).isEqualTo(LinkCachePort.LookupResult.miss());
        verify(redis).delete(CACHE_KEY);
    }

    @Test
    void lookup_shouldExposeMissNegativeAndPositiveStatesWithoutCollapsingThem() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectMapper mapper = new ObjectMapper();
        LinkMeta meta = meta(CODE, HOST);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(CACHE_KEY))
                .thenReturn(null)
                .thenReturn("__lf_not_found__")
                .thenReturn(mapper.writeValueAsString(meta));
        LinkCacheService cache = cache(redis, mapper, properties(60, 60));

        assertThat(cache.lookup(HOST, CODE)).isEqualTo(LinkCachePort.LookupResult.miss());
        assertThat(cache.lookup(HOST, CODE)).isEqualTo(LinkCachePort.LookupResult.negativeHit());
        assertThat(cache.lookup(HOST, CODE)).isEqualTo(LinkCachePort.LookupResult.hit(meta));
    }

    @Test
    void malformedValue_shouldRemainMissWhenBestEffortCleanupAlsoFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(CACHE_KEY)).thenReturn("not-json");
        doThrow(new IllegalStateException("delete unavailable")).when(redis).delete(CACHE_KEY);

        assertThat(cache(redis).lookup(HOST, CODE)).isEqualTo(LinkCachePort.LookupResult.miss());
    }

    @Test
    void lookup_shouldSkipRedisForBlankCodesAndPreserveCodeCaseInHostScopedKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        assertThat(cache(redis).lookup(null, " ")).isEqualTo(LinkCachePort.LookupResult.miss());
        assertThat(cache(redis).lookup(null, null)).isEqualTo(LinkCachePort.LookupResult.miss());
        when(values.get("link:host:go.example.test:code:AbC123")).thenReturn(null);
        assertThat(cache(redis).lookup(" GO.EXAMPLE.TEST:443 ", "AbC123"))
                .isEqualTo(LinkCachePort.LookupResult.miss());

        verify(values).get("link:host:go.example.test:code:AbC123");
    }

    @Test
    void tryPut_shouldUseMetaHostnameAndConfiguredTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        LinkMeta meta = meta("AbC123", "Go.Example.Test:443");

        assertThat(cache(redis).tryPut(null, meta)).isTrue();

        verify(values).set(
                eq("link:host:go.example.test:code:AbC123"),
                any(String.class),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void tryPut_shouldTreatInvalidMetaAsNoopAndReportSerializationFailure() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        LinkCacheService cache = new LinkCacheService(redis, mapper, properties(60, 60), metrics);

        assertThat(cache.tryPut(null, null)).isTrue();
        assertThat(cache.tryPut(null, meta(" ", HOST))).isTrue();
        when(mapper.writeValueAsString(any())).thenThrow(new IllegalStateException("cannot serialize"));
        assertThat(cache.tryPut(null, meta(CODE, HOST))).isFalse();

        verify(redis, never()).opsForValue();
        verify(metrics).increment(
                "linkforge.redirect.cache.operations",
                "operation", "write", "result", "failure"
        );
    }

    @Test
    void markNotFound_shouldHonorDisablementAndNormalizeHost() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        LinkCacheService disabled = cache(redis, new ObjectMapper(), properties(60, 0));

        disabled.markNotFound(HOST, CODE);
        disabled.markNotFound(HOST, " ");
        verifyNoInteractions(values);

        cache(redis).markNotFound(" GO.EXAMPLE.TEST:443 ", CODE);
        verify(values).set(CACHE_KEY, "__lf_not_found__", Duration.ofSeconds(60));
    }

    @Test
    void markNotFound_shouldNotThrowWhenRedisWriteFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("write unavailable"))
                .when(values).set(CACHE_KEY, "__lf_not_found__", Duration.ofSeconds(60));

        cache(redis).markNotFound(HOST, CODE);

        verify(values).set(CACHE_KEY, "__lf_not_found__", Duration.ofSeconds(60));
    }

    @Test
    void tryEvict_shouldBeIdempotentForBlankCodesAndSurfaceRedisFailureAsFalse() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkCacheService cache = cache(redis);

        assertThat(cache.tryEvict(null, " ")).isTrue();
        verify(redis, never()).delete(any(String.class));
        assertThat(cache.tryEvict(HOST, CODE)).isTrue();
        verify(redis).delete(CACHE_KEY);

        doThrow(new IllegalStateException("delete unavailable"))
                .when(redis).delete("link:code:legacy");
        assertThat(cache.tryEvict(null, "legacy")).isFalse();
    }

    private static LinkCacheService cache(StringRedisTemplate redis) {
        return cache(redis, new ObjectMapper(), properties(60, 60));
    }

    private static LinkCacheService cache(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            RedirectProperties properties
    ) {
        return new LinkCacheService(redis, mapper, properties, OperationalMetrics.noop());
    }

    private static RedirectProperties properties(long cacheTtl, long notFoundTtl) {
        RedirectProperties properties = new RedirectProperties();
        properties.setCacheTtlSeconds(cacheTtl);
        properties.setNotFoundCacheTtlSeconds(notFoundTtl);
        return properties;
    }

    private static LinkMeta meta(String code, String hostname) {
        return new LinkMeta(
                11L,
                22L,
                code,
                "https://example.com/target",
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                hostname,
                null,
                null,
                LinkMeta.ACTIVE_LIFECYCLE_STATE
        );
    }
}

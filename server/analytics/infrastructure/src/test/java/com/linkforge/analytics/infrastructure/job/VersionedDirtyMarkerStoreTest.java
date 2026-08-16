package com.linkforge.analytics.infrastructure.job;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionedDirtyMarkerStoreTest {

    @Test
    void claim_shouldSnapshotOnlyPositiveGenerations() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("1:10", "3"));
        when(hashes.multiGet("stats:dirty:v2:link:first-seen:20260219", List.of("1:10")))
                .thenReturn(List.of(1_710_000_000_000L));

        VersionedDirtyMarkerStore store = new VersionedDirtyMarkerStore(redis);

        assertThat(store.claim(
                "stats:dirty:v2:link:20260219",
                "stats:dirty:v2:link:first-seen:20260219",
                10
        )).containsExactly(new VersionedDirtyMarkerStore.Claim("1:10", 3L, 1_710_000_000_000L));
    }

    @Test
    void complete_shouldReportDeletedAndAdvancedGenerationsSeparately() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 1L));
        VersionedDirtyMarkerStore store = new VersionedDirtyMarkerStore(redis);

        VersionedDirtyMarkerStore.Completion completion = store.complete(
                "stats:dirty:v2:link:20260219",
                "stats:dirty:v2:link:first-seen:20260219",
                List.of(
                        new VersionedDirtyMarkerStore.Claim("1:10", 3L, 100L),
                        new VersionedDirtyMarkerStore.Claim("1:20", 7L, 200L)
                )
        );

        assertThat(completion).isEqualTo(new VersionedDirtyMarkerStore.Completion(1L, 1L));
    }
}

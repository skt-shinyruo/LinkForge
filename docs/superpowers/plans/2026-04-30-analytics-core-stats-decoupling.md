# Analytics Core Stats Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make basic analytics PV/UV work by default and ensure visit-detail sampling never under-counts core stats.

**Architecture:** Keep one full-fidelity Redis visit stream as the async analytics input. The redirect path still appends one compact event; the core projector always consumes that stream for PV/UV, while the visit-detail ingest worker applies `events.enabled` and `events.sample-rate` only before inserting `link_visit_events` rows.

**Tech Stack:** Java 17, Spring Boot configuration properties, Spring Data Redis Streams, MyBatis, JUnit 5, Mockito, AssertJ, Maven.

---

## File Map

- Modify: `server/foundation/core/src/main/java/com/linkforge/foundation/config/AnalyticsProperties.java`
  - Add `visitStream` configuration.
  - Add `resolveVisitStreamMaxLen()` so appender can prefer `visit-stream.max-len` and fall back to `events.stream-max-len`.
- Create: `server/foundation/core/src/test/java/com/linkforge/foundation/config/AnalyticsPropertiesTest.java`
  - Lock default and fallback max-length behavior.
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java`
  - Always append valid visits.
  - Remove `events.enabled` and `events.sampleRate` from appender decisions.
- Modify: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppenderTest.java`
  - Replace disabled and sample-rate skip expectations with full-fidelity stream append expectations.
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java`
  - Remove `events.enabled` gate from core aggregate projection.
- Modify: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJobTest.java`
  - Lock default projection behavior with `events.enabled=false`.
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java`
  - Apply `events.sampleRate` only to detail rows.
  - Acknowledge valid sampled-out records for the detail consumer group.
- Create: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobTest.java`
  - Cover disabled ingest, sample-rate boundary values, and sampled-out ack behavior.
- Modify: `server/app/src/main/resources/application.yml`
  - Add `app.analytics.visit-stream.max-len`.
  - Clarify that `events.enabled` and `events.sample-rate` are detail-only settings.
- Modify: `deploy/docker-compose.yml`
  - Pass analytics stream/detail environment variables through the server service.
- Modify: `deploy/.env.example`
  - Document core visit stream sizing and optional visit-detail storage.
- Modify: `README.md`
  - Document that basic stats work by default and detail events are optional.

---

### Task 1: Add Core Visit Stream Configuration

**Files:**
- Modify: `server/foundation/core/src/main/java/com/linkforge/foundation/config/AnalyticsProperties.java`
- Create: `server/foundation/core/src/test/java/com/linkforge/foundation/config/AnalyticsPropertiesTest.java`

- [ ] **Step 1: Write the failing configuration test**

Create `server/foundation/core/src/test/java/com/linkforge/foundation/config/AnalyticsPropertiesTest.java`:

```java
package com.linkforge.foundation.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsPropertiesTest {

    @Test
    void resolveVisitStreamMaxLen_shouldUseEventsStreamMaxLenByDefaultForCompatibility() {
        AnalyticsProperties properties = new AnalyticsProperties();

        assertThat(properties.resolveVisitStreamMaxLen()).isEqualTo(200_000L);
    }

    @Test
    void resolveVisitStreamMaxLen_shouldUseLegacyEventsStreamMaxLenWhenDedicatedValueIsUnset() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setStreamMaxLen(500L);

        assertThat(properties.resolveVisitStreamMaxLen()).isEqualTo(500L);
    }

    @Test
    void resolveVisitStreamMaxLen_shouldPreferDedicatedVisitStreamMaxLen() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setStreamMaxLen(500L);
        properties.getVisitStream().setMaxLen(900L);

        assertThat(properties.resolveVisitStreamMaxLen()).isEqualTo(900L);
    }

    @Test
    void resolveVisitStreamMaxLen_shouldAllowDedicatedZeroToDisableTrim() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setStreamMaxLen(500L);
        properties.getVisitStream().setMaxLen(0L);

        assertThat(properties.resolveVisitStreamMaxLen()).isZero();
    }
}
```

- [ ] **Step 2: Run the failing configuration test**

Run:

```bash
mvn -q -pl foundation/core -am -Dtest=AnalyticsPropertiesTest test
```

Expected: FAIL because `AnalyticsProperties` does not yet expose `getVisitStream()` or `resolveVisitStreamMaxLen()`.

- [ ] **Step 3: Add the visit stream properties**

Modify `server/foundation/core/src/main/java/com/linkforge/foundation/config/AnalyticsProperties.java`.

Add this field near the existing `dimensions` and `events` fields:

```java
    private VisitStream visitStream = new VisitStream();
```

Add these methods near the existing `getEvents()` and `setEvents(Events events)` methods:

```java
    public VisitStream getVisitStream() {
        return visitStream;
    }

    public void setVisitStream(VisitStream visitStream) {
        this.visitStream = visitStream;
    }

    public long resolveVisitStreamMaxLen() {
        Long dedicated = visitStream == null ? null : visitStream.getMaxLen();
        if (dedicated != null) {
            return dedicated;
        }
        return events == null ? 200_000L : events.getStreamMaxLen();
    }
```

Add this nested class before the existing `Events` nested class:

```java
    public static class VisitStream {
        /**
         * Redis Stream approximate max length for the full-fidelity redirect visit stream.
         *
         * <p>Null keeps compatibility by falling back to events.stream-max-len. Values <= 0 disable trim.</p>
         */
        private Long maxLen;

        public Long getMaxLen() {
            return maxLen;
        }

        public void setMaxLen(Long maxLen) {
            this.maxLen = maxLen;
        }
    }
```

- [ ] **Step 4: Run the configuration test**

Run:

```bash
mvn -q -pl foundation/core -am -Dtest=AnalyticsPropertiesTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/foundation/core/src/main/java/com/linkforge/foundation/config/AnalyticsProperties.java \
        server/foundation/core/src/test/java/com/linkforge/foundation/config/AnalyticsPropertiesTest.java
git commit -m "fix: add analytics visit stream configuration"
```

---

### Task 2: Make Visit Appender Full-Fidelity

**Files:**
- Modify: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppenderTest.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java`

- [ ] **Step 1: Replace appender skip tests with full-fidelity tests**

Modify `RedisAnalyticsVisitEventAppenderTest` so the first three tests are:

```java
    @Test
    void append_shouldWriteStreamWithDefaultProperties() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1-0"));

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(redis, properties);

        appender.append(event());

        verify(streamOps).add(any());
        verify(streamOps).trim(eq(AnalyticsKeys.visitEventStreamKey()), eq(200_000L), eq(true));
    }

    @Test
    void append_shouldWriteStreamWhenEventsAreDisabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(false);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1-0"));

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(redis, properties);

        appender.append(event());

        verify(streamOps).add(any());
        verify(streamOps).trim(eq(AnalyticsKeys.visitEventStreamKey()), eq(200_000L), eq(true));
    }

    @Test
    void append_shouldWriteStreamWhenDetailSampleRateIsZero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(0);
        properties.getEvents().setStreamMaxLen(0);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1-0"));

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(redis, properties);

        appender.append(event());

        verify(streamOps).add(any());
        verify(streamOps, never()).trim(eq(AnalyticsKeys.visitEventStreamKey()), anyLong(), eq(true));
    }
```

Add this test after the existing trim test:

```java
    @Test
    void append_shouldPreferDedicatedVisitStreamMaxLenOverLegacyEventsStreamMaxLen() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setStreamMaxLen(500);
        properties.getVisitStream().setMaxLen(900L);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1-0"));

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(redis, properties);

        appender.append(event());

        verify(streamOps).add(any());
        verify(streamOps).trim(eq(AnalyticsKeys.visitEventStreamKey()), eq(900L), eq(true));
    }
```

Ensure the test imports include:

```java
import static org.mockito.ArgumentMatchers.anyLong;
```

Remove the unused import:

```java
import static org.mockito.Mockito.verifyNoInteractions;
```

- [ ] **Step 2: Run the failing appender tests**

Run:

```bash
mvn -q -pl analytics/infrastructure -am -Dtest=RedisAnalyticsVisitEventAppenderTest test
```

Expected: FAIL because the appender still skips Redis when events are disabled or `sampleRate` is zero.

- [ ] **Step 3: Remove event gating and sampling from the appender**

Modify `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java`.

Remove this import:

```java
import java.util.concurrent.ThreadLocalRandom;
```

Delete this block from `append(AnalyticsVisitEventService.RedirectVisitEvent event)`:

```java
        if (!shouldAppend(cfg)) {
            return;
        }
```

Replace the trim max-length calculation:

```java
        long maxLen = cfg == null ? 0L : cfg.getStreamMaxLen();
```

with:

```java
        long maxLen = analyticsProperties == null ? 0L : analyticsProperties.resolveVisitStreamMaxLen();
```

Delete the entire `shouldAppend(AnalyticsProperties.Events cfg)` method:

```java
    private static boolean shouldAppend(AnalyticsProperties.Events cfg) {
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        double sampleRate = cfg.getSampleRate();
        if (Double.isNaN(sampleRate) || sampleRate <= 0) {
            return false;
        }
        if (sampleRate >= 1) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }
```

- [ ] **Step 4: Run the appender tests**

Run:

```bash
mvn -q -pl analytics/infrastructure -am -Dtest=RedisAnalyticsVisitEventAppenderTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppenderTest.java
git commit -m "fix: keep analytics visit stream full fidelity"
```

---

### Task 3: Project Core Aggregates Without Detail Events

**Files:**
- Modify: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJobTest.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java`

- [ ] **Step 1: Update projector tests to prove default aggregation**

In `AnalyticsRedirectEventProjectorJobTest`, remove this line from `project_should_apply_async_aggregates_and_ack_after_successful_projection`:

```java
        properties.getEvents().setEnabled(true);
```

Replace the disabled-events test:

```java
    @Test
    void project_shouldSkipRedisWhenEventsAreDisabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
                redis,
                properties,
                mock(AnalyticsRedisAggregateWriter.class)
        );

        job.project();

        verify(redis, never()).hasKey(anyString());
        verify(redis, never()).opsForStream();
    }
```

with:

```java
    @Test
    void project_shouldReadStreamWhenEventsAreDisabledBecauseCoreStatsAreAlwaysOn() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        AnalyticsRedisAggregateWriter aggregateWriter = mock(AnalyticsRedisAggregateWriter.class);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(redis.hasKey(AnalyticsKeys.visitEventStreamKey())).thenReturn(true);
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

        MapRecord<String, Object, Object> record = visitRecord("1-0", Map.of(
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1"
        ));
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(record), List.of());
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);

        AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
                redis,
                properties,
                aggregateWriter
        );

        job.project();

        verify(redis).hasKey(AnalyticsKeys.visitEventStreamKey());
        verify(aggregateWriter).write(Map.of(
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1"
        ));
        verify(streamOps).acknowledge(eq(AnalyticsKeys.visitEventStreamKey()), eq("lf-visit-projector"), any(RecordId[].class));
    }
```

Remove unused static imports left by the old disabled-events test only if the compiler reports them.

- [ ] **Step 2: Run the failing projector tests**

Run:

```bash
mvn -q -pl analytics/infrastructure -am -Dtest=AnalyticsRedirectEventProjectorJobTest test
```

Expected: FAIL because `AnalyticsRedirectEventProjectorJob.project()` returns before reading Redis when `events.enabled=false`.

- [ ] **Step 3: Remove the detail-event gate from core projection**

Modify `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java`.

Delete this block from `project()`:

```java
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
```

Keep the stream key and `ensureGroup(String streamKey)` logic unchanged.

- [ ] **Step 4: Run the projector tests**

Run:

```bash
mvn -q -pl analytics/infrastructure -am -Dtest=AnalyticsRedirectEventProjectorJobTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJobTest.java
git commit -m "fix: project analytics aggregates without detail events"
```

---

### Task 4: Move Sampling to Visit Detail Ingest

**Files:**
- Create: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobTest.java`
- Modify: `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java`
- Modify: `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobPoisonIsolationTest.java`

- [ ] **Step 1: Write the ingest sampling tests**

Create `server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobTest.java`:

```java
package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsEventIngestJobTest {

    @Test
    void ingest_shouldSkipRedisWhenDetailEventsAreDisabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingest();

        verifyNoInteractions(redis);
        verifyNoInteractions(mapper);
    }

    @Test
    void ingestRecords_shouldInsertAllValidRowsWhenSampleRateIsOne() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(1);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);
        when(mapper.batchInsertIgnore(anyList())).thenReturn(2);

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingestRecords("stats:visit:events", List.of(
                record("1-0", "1", "10", "req-1"),
                record("2-0", "1", "11", "req-2")
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkVisitEventInsertRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchInsertIgnore(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).extracting(LinkVisitEventInsertRow::getRequestId)
                .containsExactly("req-1", "req-2");

        assertAcked(streamOps, "stats:visit:events", "1-0", "2-0");
    }

    @Test
    void ingestRecords_shouldAckValidRowsWithoutInsertWhenSampleRateIsZero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(0);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingestRecords("stats:visit:events", List.of(
                record("1-0", "1", "10", "req-1"),
                record("2-0", "1", "11", "req-2")
        ));

        verify(mapper, never()).batchInsertIgnore(anyList());
        assertAcked(streamOps, "stats:visit:events", "1-0", "2-0");
    }

    @Test
    void ingestRecords_shouldAckInvalidRowsAndSampledOutValidRowsTogether() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(0);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingestRecords("stats:visit:events", List.of(
                record("1-0", "1", "10", "req-1"),
                record("2-0", "-1", "0", "req-invalid")
        ));

        verify(mapper, never()).batchInsertIgnore(anyList());
        assertAcked(streamOps, "stats:visit:events", "1-0", "2-0");
    }

    private static AnalyticsEventIngestJob job(
            StringRedisTemplate redis,
            LinkVisitEventMapper mapper,
            AnalyticsProperties properties
    ) {
        return new AnalyticsEventIngestJob(
                redis,
                mapper,
                properties,
                new IdProperties(),
                new SnowflakeIdGenerator(1L, 1L)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static MapRecord<String, Object, Object> record(String id, String tenantId, String linkId, String requestId) {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of(id));
        when(record.getValue()).thenReturn((Map) Map.of(
                "tenantId", tenantId,
                "linkId", linkId,
                "requestId", requestId,
                "ts", "1710000000000"
        ));
        return record;
    }

    private static void assertAcked(
            StreamOperations<String, Object, Object> streamOps,
            String streamKey,
            String... expectedIds
    ) {
        ArgumentCaptor<RecordId[]> idsCaptor = ArgumentCaptor.forClass(RecordId[].class);
        verify(streamOps).acknowledge(eq(streamKey), eq("lf-visit-ingest"), idsCaptor.capture());

        List<String> actual = Arrays.stream(idsCaptor.getValue())
                .map(RecordId::toString)
                .toList();
        assertThat(actual).containsExactlyInAnyOrder(expectedIds);
    }
}
```

- [ ] **Step 2: Run the failing ingest tests**

Run:

```bash
mvn -q -pl analytics/infrastructure -am -Dtest=AnalyticsEventIngestJobTest,AnalyticsEventIngestJobPoisonIsolationTest test
```

Expected: FAIL because `sampleRate=0` still inserts rows when `ingestRecords(String streamKey, List<MapRecord<String, Object, Object>> records)` is called.

- [ ] **Step 3: Apply sampling inside `AnalyticsEventIngestJob`**

Modify `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java`.

Add this import:

```java
import java.util.concurrent.ThreadLocalRandom;
```

In `ingestRecords(String streamKey, List<MapRecord<String, Object, Object>> records)`, replace:

```java
        VisitEventBatchAssembler.Batch batch = batchAssembler.assemble(records);
        List<VisitEventBatchAssembler.IngestItem> items = batch.items();
        List<RecordId> ackAlways = batch.ackAlways();
```

with:

```java
        VisitEventBatchAssembler.Batch batch = applyDetailSampling(batchAssembler.assemble(records));
        List<VisitEventBatchAssembler.IngestItem> items = batch.items();
        List<RecordId> ackAlways = batch.ackAlways();
```

Add these methods after `ingestRecords(String streamKey, List<MapRecord<String, Object, Object>> records)` and before `isolatePoisonAndAck(String streamKey, List<VisitEventBatchAssembler.IngestItem> items)`:

```java
    private VisitEventBatchAssembler.Batch applyDetailSampling(VisitEventBatchAssembler.Batch batch) {
        if (batch == null) {
            return new VisitEventBatchAssembler.Batch(List.of(), List.of());
        }

        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        List<RecordId> ackAlways = new ArrayList<>(batch.ackAlways());
        List<VisitEventBatchAssembler.IngestItem> sampledItems = new ArrayList<>(batch.items().size());

        for (VisitEventBatchAssembler.IngestItem item : batch.items()) {
            if (item == null || item.recordId() == null) {
                continue;
            }
            if (shouldPersistDetail(cfg)) {
                sampledItems.add(item);
            } else {
                ackAlways.add(item.recordId());
            }
        }

        return new VisitEventBatchAssembler.Batch(List.copyOf(sampledItems), List.copyOf(ackAlways));
    }

    private static boolean shouldPersistDetail(AnalyticsProperties.Events cfg) {
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        double sampleRate = cfg.getSampleRate();
        if (Double.isNaN(sampleRate) || sampleRate <= 0) {
            return false;
        }
        if (sampleRate >= 1) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }
```

- [ ] **Step 4: Make the poison isolation test explicit about full detail sampling**

In `AnalyticsEventIngestJobPoisonIsolationTest`, after:

```java
        AnalyticsProperties analyticsProperties = new AnalyticsProperties();
```

add:

```java
        analyticsProperties.getEvents().setEnabled(true);
        analyticsProperties.getEvents().setSampleRate(1);
```

This keeps the poison-isolation test focused on data integrity rather than sampling.

- [ ] **Step 5: Run the ingest tests**

Run:

```bash
mvn -q -pl analytics/infrastructure -am -Dtest=AnalyticsEventIngestJobTest,AnalyticsEventIngestJobPoisonIsolationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobTest.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobPoisonIsolationTest.java
git commit -m "fix: sample only analytics visit details"
```

---

### Task 5: Update Configuration and Documentation

**Files:**
- Modify: `server/app/src/main/resources/application.yml`
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`
- Modify: `README.md`

- [ ] **Step 1: Update `application.yml` comments and visit-stream property**

Modify the `app.analytics` block in `server/app/src/main/resources/application.yml`.

Insert this block after `tracking-param-allowlist` and before `dimensions`:

```yaml
    # 核心访问流（Redirect -> Redis Stream -> 异步 PV/UV 聚合），默认启用；不受 events.enabled/sample-rate 影响
    visit-stream:
      # 空值时兼容回退到 events.stream-max-len；<=0 表示不 trim
      max-len: ${ANALYTICS_VISIT_STREAM_MAX_LEN:}
```

Replace the existing `events` comments with:

```yaml
    events:
      # 可选访问明细落库（Redis Stream -> MySQL link_visit_events），默认关闭；不影响基础 PV/UV
      enabled: ${ANALYTICS_EVENTS_ENABLED:false}
      # 仅影响访问明细落库采样；不影响基础 PV/UV 或应用月点击配额
      sample-rate: ${ANALYTICS_EVENTS_SAMPLE_RATE:0.1}
      # 兼容旧配置：当 visit-stream.max-len 为空时作为核心访问流 trim fallback
      stream-max-len: ${ANALYTICS_EVENTS_STREAM_MAX_LEN:200000}
      retention-days: ${ANALYTICS_EVENTS_RETENTION_DAYS:14}
```

- [ ] **Step 2: Pass analytics environment variables in Docker Compose**

In `deploy/docker-compose.yml`, add these environment entries under the existing `ANALYTICS_SALT` entry for the `server` service:

```yaml
      ANALYTICS_VISIT_STREAM_MAX_LEN: ${ANALYTICS_VISIT_STREAM_MAX_LEN:-}
      ANALYTICS_EVENTS_ENABLED: ${ANALYTICS_EVENTS_ENABLED:-false}
      ANALYTICS_EVENTS_SAMPLE_RATE: ${ANALYTICS_EVENTS_SAMPLE_RATE:-0.1}
      ANALYTICS_EVENTS_STREAM_MAX_LEN: ${ANALYTICS_EVENTS_STREAM_MAX_LEN:-200000}
```

- [ ] **Step 3: Document analytics knobs in `.env.example`**

In `deploy/.env.example`, after:

```dotenv
ANALYTICS_SALT=please_set_a_random_salt
```

add:

```dotenv
# 基础 PV/UV 默认启用，依赖完整访问流；该值用于控制 Redis Stream 近似最大长度
ANALYTICS_VISIT_STREAM_MAX_LEN=200000

# 可选访问明细落库（link_visit_events），默认关闭；采样率只影响明细，不影响基础 PV/UV
ANALYTICS_EVENTS_ENABLED=false
ANALYTICS_EVENTS_SAMPLE_RATE=0.1
```

- [ ] **Step 4: Document default basic stats in README**

In `README.md`, after the existing `ANALYTICS_SALT` bullet, add:

```markdown
- （可选）`ANALYTICS_VISIT_STREAM_MAX_LEN`：基础 PV/UV 使用的 Redis 访问流近似最大长度，默认 `200000`
- （可选）`ANALYTICS_EVENTS_ENABLED` / `ANALYTICS_EVENTS_SAMPLE_RATE`：访问明细落库开关与采样率；只影响 `link_visit_events` 明细，不影响基础 PV/UV
```

- [ ] **Step 5: Run focused tests after documentation/config updates**

Run:

```bash
mvn -q -pl foundation/core,analytics/infrastructure -am -Dtest=AnalyticsPropertiesTest,RedisAnalyticsVisitEventAppenderTest,AnalyticsRedirectEventProjectorJobTest,AnalyticsEventIngestJobTest,AnalyticsEventIngestJobPoisonIsolationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/app/src/main/resources/application.yml \
        deploy/docker-compose.yml \
        deploy/.env.example \
        README.md
git commit -m "docs: clarify analytics core stats configuration"
```

---

### Task 6: Final Verification

**Files:**
- No source edits unless verification exposes a bug.

- [ ] **Step 1: Run analytics and redirect test slice**

Run:

```bash
mvn -q -pl foundation/core,analytics/application,analytics/infrastructure,redirect/application -am -Dtest=AnalyticsPropertiesTest,AnalyticsVisitEventServiceTest,RedisAnalyticsVisitEventAppenderTest,AnalyticsRedirectEventProjectorJobTest,AnalyticsEventIngestJobTest,AnalyticsEventIngestJobPoisonIsolationTest,RedirectServiceTimezoneTest,RedirectServiceAuthoritativeFallbackTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 2: Run app startup/config tests**

Run:

```bash
mvn -q -pl app -am -Dtest=ApiStartupValidatorTest,AppModuleCompositionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 3: Inspect final diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: only the files named in this plan are modified or newly created.

- [ ] **Step 4: Commit any verification-only fixes**

If Step 1 or Step 2 exposed a compile or test issue and the fix was made in a planned file, commit it:

```bash
git add server/foundation/core/src/main/java/com/linkforge/foundation/config/AnalyticsProperties.java \
        server/foundation/core/src/test/java/com/linkforge/foundation/config/AnalyticsPropertiesTest.java \
        server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppenderTest.java \
        server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJobTest.java \
        server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobTest.java \
        server/analytics/infrastructure/src/test/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJobPoisonIsolationTest.java \
        server/app/src/main/resources/application.yml \
        deploy/docker-compose.yml \
        deploy/.env.example \
        README.md
git commit -m "test: verify analytics core stats decoupling"
```

Expected: commit created only if verification required additional fixes.

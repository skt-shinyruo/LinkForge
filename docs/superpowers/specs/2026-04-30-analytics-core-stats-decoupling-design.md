# Analytics Core Stats Decoupling Design

**Date:** 2026-04-30

## Problem Statement

LinkForge advertises basic redirect statistics, but the current default runtime does not produce PV or UV for redirects.

The redirect path calls `VisitRecorderPort.recordVisit(...)`, implemented by `AnalyticsVisitEventService.recordVisit(...)`. That service only converts the redirect visit into a `RedirectVisitEvent` and delegates to `AnalyticsVisitEventAppender`.

The only production appender is `RedisAnalyticsVisitEventAppender`. It returns before writing Redis whenever `app.analytics.events.enabled` is false, and it also applies `app.analytics.events.sample-rate` before appending the visit stream record.

The async aggregate projector, `AnalyticsRedirectEventProjectorJob`, also returns when `app.analytics.events.enabled` is false. The real PV/UV writes happen later in `AnalyticsRedisAggregateWriter`, after a visit stream record has been projected.

The default app configuration sets:

- `app.analytics.events.enabled=false`
- `app.analytics.events.sample-rate=0.1`

The result is:

1. Default deployments produce no Redis PV/UV aggregate keys and therefore no rows in `link_stats_daily`.
2. Enabling visit events makes basic PV/UV depend on the detail-event sample rate.
3. With the default sample rate of `0.1`, core PV/UV and application monthly click usage are under-counted.
4. The "visit detail" feature flag controls the correctness channel for basic stats, which is the wrong ownership boundary.

## Goals

1. Basic PV/UV statistics must work in the default deployment.
2. Core PV/UV aggregation must count every valid redirect visit and must not be sampled.
3. `app.analytics.events.enabled` must control only visit-detail persistence and related detail retention behavior.
4. `app.analytics.events.sample-rate` must control only visit-detail persistence.
5. Redirect hot-path behavior must remain lightweight: append one compact visit event and return.
6. Existing stats flush, dimension flush, top-link, overview, and application quota reads should continue to consume the same aggregate tables.
7. Tests must lock the default behavior so this coupling cannot return unnoticed.

## Non-Goals

- Do not reintroduce synchronous PV/UV or dimension aggregation work into `RedirectService`.
- Do not redesign analytics query APIs or response shapes.
- Do not backfill historical visits lost while the old default configuration was deployed.
- Do not estimate full-fidelity visit-detail data from sampled rows.
- Do not require operators to enable detailed event storage just to get basic stats.

## Current Relevant Code

- `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
  - Calls `visitRecorderPort.recordVisit(...)` after a redirect is confirmed and available.
- `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsVisitEventService.java`
  - Converts `RedirectVisitRecord` into `RedirectVisitEvent`.
  - Delegates to `AnalyticsVisitEventAppender`.
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/RedisAnalyticsVisitEventAppender.java`
  - Checks `events.enabled`.
  - Applies `events.sampleRate`.
  - Writes `stats:visit:events` only when both checks pass.
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedirectEventProjectorJob.java`
  - Checks `events.enabled` before consuming `stats:visit:events`.
  - Calls `AnalyticsRedisAggregateWriter.write(...)` for projectable records.
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsRedisAggregateWriter.java`
  - Increments `stats:pv:*`.
  - Adds visitor keys to `stats:uv:*`.
  - Adds active members and dirty stream records for flush jobs.
  - Writes dimensions only when `analytics.dimensions.enabled` is true.
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsFlushJob.java`
  - Reads dirty members.
  - Reads Redis PV/HLL UV values.
  - Upserts `link_stats_daily`.
- `server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/job/AnalyticsEventIngestJob.java`
  - Consumes the same visit stream into `link_visit_events`.
  - Correctly represents the detail-event persistence feature, but currently receives only sampled records because sampling happens in the appender.
- `server/app/src/main/resources/application.yml`
  - Documents `events.enabled` as "visit detail events", but current behavior makes it a core-stats switch.

## Approaches Considered

### Approach A: Restore synchronous aggregate writes in the appender

The appender would increment PV/UV directly for every redirect and append sampled visit-detail events only when enabled.

Pros:

- Straightforward correctness model.
- Does not depend on projector scheduling for Redis aggregate freshness.

Cons:

- Moves full aggregation work back into the redirect hot path.
- Reverses the single-runtime architecture decision that made redirect append only one lightweight event.
- Reintroduces more Redis operations and dimension work on the user-facing redirect request.

### Approach B: Keep one full-fidelity visit stream and move sampling to detail ingest

The appender always writes the compact visit stream record. The aggregate projector always consumes the stream and writes core PV/UV. The detail ingest job remains behind `events.enabled` and applies `sampleRate` before inserting rows into `link_visit_events`.

Pros:

- Preserves the lightweight redirect hot path.
- Makes the visit stream the single source for async analytics workers.
- Keeps basic stats full-fidelity while retaining sampled detail storage.
- Requires targeted changes to existing workers rather than new storage contracts.

Cons:

- The Redis visit stream exists even when detail events are disabled.
- Stream sizing becomes a core-stats reliability setting, not merely a detail-event memory setting.

### Approach C: Split core stats and detail events into separate streams

The appender writes a full-fidelity core-stats stream and, when enabled and sampled, writes a separate detail-event stream.

Pros:

- Strongest operational separation between core stats and optional detail storage.
- Detail consumer backlog cannot affect the core stream.

Cons:

- Adds a second stream contract and duplicate serialization.
- Increases redirect-path writes.
- Requires more migration and monitoring surface for a narrow bug fix.

## Recommended Approach

Use Approach B.

Keep `stats:visit:events` as the full-fidelity redirect visit stream. It is no longer a "visit detail events only" stream; it is the analytics visit stream used by multiple consumer groups.

`RedisAnalyticsVisitEventAppender` should append every valid visit event to the stream. It must not check `events.enabled` and must not apply `events.sampleRate`. It may still trim the stream using the configured maximum length.

`AnalyticsRedirectEventProjectorJob` should always try to consume and project the stream when Redis contains the key. It must not check `events.enabled`. This worker owns core aggregate correctness.

`AnalyticsEventIngestJob` should remain gated by `events.enabled`. When enabled, it should read the full-fidelity stream through its own consumer group and apply `events.sampleRate` before inserting visit-detail rows. Non-sampled records must still be acknowledged by the detail ingest consumer group, because skipping them is an intentional detail-storage decision, not a retryable failure.

## Target Behavior

### Default Deployment

With the default configuration:

```yaml
app.analytics.events.enabled: false
app.analytics.events.sample-rate: 0.1
```

redirect visits still append compact stream records, the projector still writes Redis PV/UV aggregates, and flush jobs still persist `link_stats_daily`.

No rows are inserted into `link_visit_events` because visit-detail storage is disabled.

### Detail Events Enabled

When `app.analytics.events.enabled=true`, `AnalyticsEventIngestJob` stores visit-detail rows according to `events.sampleRate`.

Examples:

- `sample-rate=1.0`: every valid stream record is inserted into `link_visit_events`.
- `sample-rate=0.1`: about 10% of valid stream records are inserted into `link_visit_events`.
- `sample-rate=0`: no detail rows are inserted, but the detail consumer group acknowledges records it intentionally skips.

In every case, core PV/UV aggregation remains full-fidelity.

### Dimensions

Dimension aggregation remains controlled by `app.analytics.dimensions.enabled`.

The projector should still call `AnalyticsRedisAggregateWriter.write(...)` for every projectable record. That writer continues to write dimensions only when dimensions are enabled.

### Application Quotas

Application monthly click usage continues to read from `link_stats_daily`.

Because `link_stats_daily` is no longer sampled or disabled by visit-detail settings, quota checks become aligned with actual counted redirects.

## Data Flow

1. `RedirectService` resolves a redirect and calls `VisitRecorderPort.recordVisit(...)`.
2. `AnalyticsVisitEventService` converts the contract record to a `RedirectVisitEvent`.
3. `RedisAnalyticsVisitEventAppender` writes one compact record to `stats:visit:events` for every valid event.
4. `AnalyticsRedirectEventProjectorJob`, using the `lf-visit-projector` consumer group, reads every stream record and calls `AnalyticsRedisAggregateWriter`.
5. `AnalyticsRedisAggregateWriter` writes Redis PV/UV aggregate keys, active members, and dirty flush markers.
6. `AnalyticsFlushJob` flushes dirty PV/UV aggregates to `link_stats_daily`.
7. If visit-detail events are enabled, `AnalyticsEventIngestJob`, using the `lf-visit-ingest` consumer group, samples stream records and writes selected rows to `link_visit_events`.
8. `AnalyticsEventRetentionJob` continues to apply retention policy to `link_visit_events`.

## Configuration Design

`app.analytics.events.enabled` keeps its meaning as a visit-detail storage switch.

`app.analytics.events.sample-rate` keeps its meaning as a visit-detail sample rate.

The stream trim setting currently lives under `app.analytics.events.stream-max-len`. After this change, that name is misleading because the stream is required for core stats even when detail events are disabled.

The implementation will add a new core stream setting:

```yaml
app.analytics.visit-stream.max-len: ${ANALYTICS_VISIT_STREAM_MAX_LEN:200000}
```

To avoid a breaking configuration change, `events.stream-max-len` remains as a deprecated fallback for one release. The appender should resolve max length in this order:

1. `analytics.visit-stream.max-len`
2. `analytics.events.stream-max-len`
3. existing default `200000`

Documentation should state that the visit stream is used by core analytics workers and must be sized for projector lag, not just detail-event retention.

## Error Handling

The existing fail-open behavior in `AnalyticsVisitEventService` should remain.

If appending the visit stream fails and `events.failOpen=true`, redirect should continue and log at debug level as it does today. This means stats can still be best-effort under Redis failure, but the default configuration no longer disables stats intentionally.

Projector failures should keep their current retry semantics:

- acknowledge only successfully projected or intentionally skipped legacy records
- stop the current loop on aggregate writer failure
- retry unacknowledged records later

Detail ingest sampling should not be treated as an error. Sampled-out records should be acknowledged for the detail consumer group.

## Test Strategy

### Unit Tests

Update `RedisAnalyticsVisitEventAppenderTest`:

1. Default `AnalyticsProperties` writes one stream record.
2. `events.enabled=false` still writes one stream record.
3. `events.sampleRate=0` still writes one stream record.
4. `events.sampleRate=0.1` does not make appender behavior random.
5. Stream trimming uses the resolved visit stream max length.

Update `AnalyticsRedirectEventProjectorJobTest`:

1. Default `AnalyticsProperties` projects a stream record into PV/UV aggregates.
2. `events.enabled=false` still projects core aggregates.
3. A record without `visitorKey` remains acknowledged as a legacy or non-projectable record, preserving current behavior.
4. Dimension writes still depend on `dimensions.enabled`, not `events.enabled`.

Update or add `AnalyticsEventIngestJobTest`:

1. `events.enabled=false` does not read the stream or insert details.
2. `events.enabled=true` and `sampleRate=1` inserts all valid detail rows.
3. `events.enabled=true` and `sampleRate=0` acknowledges valid records without inserting detail rows.
4. Invalid records are still acknowledged according to the existing assembler rules.
5. A database transient failure still leaves insertable records pending for retry.

### Integration or Component Tests

Add one focused default-flow test if the existing test setup can support Redis mocks or Testcontainers cheaply:

1. Start with default analytics properties.
2. Append a redirect visit.
3. Run the projector.
4. Assert the PV key increments and the active member is written.
5. Run the flush path or directly verify that dirty markers were emitted for `AnalyticsFlushJob`.

### Documentation Tests

No generated documentation test is required, but README and `application.yml` comments should be updated so configuration names do not imply that basic stats require visit-detail events.

## Migration Plan

1. Add failing tests proving default appender and projector behavior should produce core stats with `events.enabled=false`.
2. Change `RedisAnalyticsVisitEventAppender` to always append valid visits and remove sampling from the append decision.
3. Add `app.analytics.visit-stream.max-len` and keep `events.stream-max-len` as a compatibility fallback.
4. Change `AnalyticsRedirectEventProjectorJob` to run independently of `events.enabled`.
5. Move sample-rate behavior into `AnalyticsEventIngestJob` so it only affects detail persistence.
6. Update tests that currently expect Redis to be untouched when events are disabled.
7. Update `application.yml`, README, and any deployment examples to distinguish "basic stats" from "visit detail events".
8. Run analytics application and infrastructure tests.
9. Run redirect tests that cover visit recording.
10. Run a broader backend test slice if the change touches shared configuration binding.

## Risks and Mitigations

### Redis Stream Growth

Risk: Operators who disabled detail events previously produced no visit stream. After the fix, the stream is required for basic stats and will exist by default.

Mitigation: Keep approximate stream trimming enabled by default. Document the new meaning of the stream max-length setting and size it for expected traffic and projector lag.

### Stream Trimming Before Projection

Risk: Under high traffic and a stalled projector, approximate stream trimming can remove unprojected records, causing stats loss.

Mitigation: Treat stream max length as a core analytics durability setting. Keep the default conservative for the MVP and make the setting explicit in docs. A later hardening pass can add lag monitoring or dead-letter metrics.

### Random Sampling in Tests

Risk: Moving sampling to detail ingest can make tests flaky if random sampling is asserted indirectly.

Mitigation: Unit-test boundary values `0` and `1`. If mid-rate behavior needs coverage, isolate the sampling predicate behind a small collaborator or injectable random source.

### Backward Compatibility of Configuration Names

Risk: Existing operators may already configure `ANALYTICS_EVENTS_STREAM_MAX_LEN`.

Mitigation: Keep that setting as a fallback even if a clearer `ANALYTICS_VISIT_STREAM_MAX_LEN` is introduced.

### Quota Behavior Changes

Risk: Tenants with application quotas may see quota checks become stricter because usage is no longer under-counted.

Mitigation: This is the correct behavior. Release notes should call it out as a bug fix affecting quota enforcement accuracy.

## Acceptance Criteria

- With default config, a redirect visit produces a visit stream record, projector aggregate writes, dirty flush marker, and eventually a `link_stats_daily` row.
- `ANALYTICS_EVENTS_ENABLED=false` no longer disables basic PV/UV.
- `ANALYTICS_EVENTS_SAMPLE_RATE` no longer changes basic PV/UV counts.
- With `ANALYTICS_EVENTS_ENABLED=true`, `ANALYTICS_EVENTS_SAMPLE_RATE` affects only rows inserted into `link_visit_events`.
- Application monthly click usage reads from full-fidelity `link_stats_daily` data.
- Existing fail-open redirect behavior is preserved when analytics append fails.
- Tests cover default disabled-events behavior for appender and projector.
- Tests cover detail ingest sampling separately from core aggregation.
- README and app configuration comments clearly distinguish basic stats from optional visit-detail storage.

## Open Decisions Resolved

This design keeps a single full-fidelity visit stream instead of adding a second stream. That preserves the current async architecture and keeps the redirect hot path to one Redis stream append.

This design does not use sampling correction for PV/UV. Core stats should be counted from unsampled events, because sampled UV cannot be accurately reconstructed from `link_visit_events` without a separate estimator design.

This design treats `events.enabled` as a detail-storage feature flag only. Core stats are considered part of the base product behavior described by the README and are enabled by default.

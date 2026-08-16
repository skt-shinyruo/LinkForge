package com.linkforge.foundation.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupValidationTest {

    @Test
    void strict_mode_should_reject_default_snowflake_id_config() {
        IdProperties p = new IdProperties();
        List<String> errors = new ArrayList<>();

        StartupValidation.validateIdBasics(p, true, null, errors);

        assertThat(errors).anyMatch(s -> s.contains("生产/strict 模式禁止使用默认"));
    }

    @Test
    void non_strict_mode_should_allow_default_snowflake_id_config() {
        IdProperties p = new IdProperties();
        List<String> errors = new ArrayList<>();

        StartupValidation.validateIdBasics(p, false, null, errors);

        assertThat(errors).isEmpty();
    }

    @Test
    void id_values_should_be_within_expected_range() {
        IdProperties p = new IdProperties();
        p.setWorkerId(32);
        p.setDatacenterId(-1);

        List<String> errors = new ArrayList<>();
        StartupValidation.validateIdBasics(p, false, null, errors);

        assertThat(errors).contains("app.id.worker-id 仅支持 0~31");
        assertThat(errors).contains("app.id.datacenter-id 仅支持 0~31");
    }

    @Test
    void analyticsVisitStream_shouldRejectCapacityBelowRecoveryBudget() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getVisitStream().setMaxLen(197_999L);
        properties.getVisitStream().setPeakEventsPerSecond(1_000L);
        properties.getVisitStream().setRecoveryWindowSeconds(180L);
        properties.getVisitStream().setSafetyMarginPercent(10);
        List<String> errors = new ArrayList<>();

        StartupValidation.validateAnalyticsVisitStream(properties, errors);

        assertThat(errors).anyMatch(message -> message.contains("198000"));
    }

    @Test
    void analyticsEvents_shouldRejectUnboundedOrEmptyIngestBudget() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setIngestBatchSize(0);
        properties.getEvents().setIngestMaxBatches(0);
        properties.getEvents().setIngestTimeBudgetMs(0);
        List<String> errors = new ArrayList<>();

        StartupValidation.validateAnalyticsEvents(properties, errors);

        assertThat(errors).contains(
                "app.analytics.events.ingest-batch-size 必须 > 0",
                "app.analytics.events.ingest-max-batches 必须 > 0",
                "app.analytics.events.ingest-time-budget-ms 必须 > 0"
        );
    }

    @Test
    void analyticsDirtyMarker_shouldBlockLegacyReadRetirementWithoutExternalProof() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getDirtyMarker().setLegacyReadEnabled(false);
        List<String> errors = new ArrayList<>();

        StartupValidation.validateAnalyticsDirtyMarker(
                properties,
                Instant.parse("2026-08-15T00:00:00Z"),
                errors
        );

        assertThat(errors).anyMatch(message -> message.contains("legacy retirement proof"));
    }

    @Test
    void analyticsDirtyMarker_shouldAllowRetirementOnlyAfterBothProofTimesExceedCompatibilityTtl() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getDirtyMarker().setLegacyReadEnabled(false);
        properties.getDirtyMarker().setLegacyWriteEnabled(false);
        properties.getDirtyMarker().setLegacyRetirementConfirmed(true);
        properties.getDirtyMarker().setCompatibilityTtlDays(45);
        properties.getDirtyMarker().setLegacyWriteStoppedAt(Instant.parse("2026-06-01T00:00:00Z"));
        properties.getDirtyMarker().setLegacyDrainedAt(Instant.parse("2026-06-15T00:00:00Z"));
        List<String> errors = new ArrayList<>();

        StartupValidation.validateAnalyticsDirtyMarker(
                properties,
                Instant.parse("2026-08-15T00:00:00Z"),
                errors
        );

        assertThat(errors).isEmpty();
    }
}

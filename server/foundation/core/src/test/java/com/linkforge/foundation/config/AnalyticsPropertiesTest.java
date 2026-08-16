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

    @Test
    void resolveVisitStreamRequiredCapacity_shouldIncludeRecoverySafetyMargin() {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getVisitStream().setPeakEventsPerSecond(1_000L);
        properties.getVisitStream().setRecoveryWindowSeconds(180L);
        properties.getVisitStream().setSafetyMarginPercent(10);

        assertThat(properties.resolveVisitStreamRequiredCapacity()).isEqualTo(198_000L);
    }
}

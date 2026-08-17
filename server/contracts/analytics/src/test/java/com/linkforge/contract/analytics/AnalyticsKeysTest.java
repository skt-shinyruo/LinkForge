package com.linkforge.contract.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsKeysTest {

    @Test
    void keys_should_use_expected_format() {
        LocalDate day = LocalDate.of(2026, 2, 19);
        assertThat(AnalyticsKeys.dirtyLinkMember(12L, 34L)).isEqualTo("12:34");
        assertThat(AnalyticsKeys.pvKey(12L, 34L, day)).isEqualTo("stats:pv:12:34:20260219");
        assertThat(AnalyticsKeys.uvKey(12L, 34L, day)).isEqualTo("stats:uv:12:34:20260219");
        assertThat(AnalyticsKeys.statsDirtyMarkerV2Key(day)).isEqualTo("stats:dirty:v2:link:20260219");
        assertThat(AnalyticsKeys.statsDirtyMarkerV2FirstSeenKey(day))
                .isEqualTo("stats:dirty:v2:link:first-seen:20260219");
        assertThat(AnalyticsKeys.scopeDirtyMarkerV2Key(day)).isEqualTo("stats:dirty:v2:scope:20260219");
        assertThat(AnalyticsKeys.scopeDirtyMarkerV2FirstSeenKey(day))
                .isEqualTo("stats:dirty:v2:scope:first-seen:20260219");
        assertThat(AnalyticsKeys.dirtyMarkerClaimCursorKey(AnalyticsKeys.statsDirtyMarkerV2Key(day)))
                .isEqualTo("stats:dirty:v2:link:20260219:claim:cursor");
        assertThat(AnalyticsKeys.dirtyMarkerClaimOverflowKey(AnalyticsKeys.statsDirtyMarkerV2Key(day)))
                .isEqualTo("stats:dirty:v2:link:20260219:claim:overflow");
        assertThat(AnalyticsKeys.tenantScopeUvKey(12L, day)).isEqualTo("stats:scope:uv:tenant:12:20260219");
        assertThat(AnalyticsKeys.applicationScopeUvKey(12L, 56L, day)).isEqualTo("stats:scope:uv:application:12:56:20260219");
        assertThat(AnalyticsKeys.domainScopeUvKey(12L, 78L, day)).isEqualTo("stats:scope:uv:domain:12:78:20260219");
        assertThat(AnalyticsKeys.tenantScopeMember(12L)).isEqualTo("tenant:12:0");
        assertThat(AnalyticsKeys.applicationScopeMember(12L, 56L)).isEqualTo("application:12:56");
        assertThat(AnalyticsKeys.domainScopeMember(12L, 78L)).isEqualTo("domain:12:78");
        assertThat(AnalyticsKeys.applicationClickQuotaKey(12L, 56L, LocalDate.of(2026, 2, 1)))
                .isEqualTo("quota:click:application:12:56:202602");
    }
}

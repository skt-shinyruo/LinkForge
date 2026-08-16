package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsEventRetentionJobTest {

    @Test
    void cleanup_shouldSkipWhenEventsAreDisabledOrRetentionIsNonPositive() {
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties disabled = new AnalyticsProperties();
        AnalyticsProperties nonPositive = enabledProperties(0);

        new AnalyticsEventRetentionJob(mapper, disabled).cleanup();
        new AnalyticsEventRetentionJob(mapper, nonPositive).cleanup();

        verifyNoInteractions(mapper);
    }

    @Test
    void cleanup_shouldDeleteBatchesUntilTheMapperReturnsAPartialBatch() {
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        when(mapper.deleteOld(14)).thenReturn(5_000, 5_000, 12);

        new AnalyticsEventRetentionJob(mapper, enabledProperties(14)).cleanup();

        verify(mapper, times(3)).deleteOld(14);
    }

    @Test
    void cleanup_shouldStopAtTheFairnessLimitWhenEveryBatchIsFull() {
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        when(mapper.deleteOld(30)).thenReturn(5_000);

        new AnalyticsEventRetentionJob(mapper, enabledProperties(30)).cleanup();

        verify(mapper, times(20)).deleteOld(30);
    }

    @Test
    void cleanup_shouldLeaveRemainingRowsForTheNextRunAfterDatabaseFailure() {
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        when(mapper.deleteOld(7))
                .thenReturn(5_000)
                .thenThrow(new DataRetrievalFailureException("database unavailable"));

        new AnalyticsEventRetentionJob(mapper, enabledProperties(7)).cleanup();

        verify(mapper, times(2)).deleteOld(7);
    }

    private static AnalyticsProperties enabledProperties(int retentionDays) {
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setRetentionDays(retentionDays);
        return properties;
    }
}

package com.linkforge.analytics.application;

import java.time.LocalDate;

public interface ApplicationClickUsagePort {

    long countApplicationClicks(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc
    );
}

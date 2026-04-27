package com.linkforge.contract.analytics;

import java.time.LocalDate;

public interface ApplicationClickUsagePort {

    long countApplicationClicks(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc
    );
}

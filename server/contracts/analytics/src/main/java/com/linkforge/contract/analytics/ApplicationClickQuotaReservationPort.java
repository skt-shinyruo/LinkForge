package com.linkforge.contract.analytics;

import java.time.LocalDate;

public interface ApplicationClickQuotaReservationPort {

    boolean tryReserveMonthlyClick(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc,
            long monthlyClickLimit
    );
}

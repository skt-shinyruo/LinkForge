package com.linkforge.shortlink.application.port;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ApplicationLinkQuotaReservationPort {

    boolean tryReserveMonthlyLink(
            long tenantId,
            long applicationId,
            LocalDate monthStartUtc,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc,
            long monthlyLinkLimit
    );
}

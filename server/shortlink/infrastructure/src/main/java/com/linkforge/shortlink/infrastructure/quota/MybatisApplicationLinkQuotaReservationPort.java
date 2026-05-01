package com.linkforge.shortlink.infrastructure.quota;

import com.linkforge.shortlink.application.port.ApplicationLinkQuotaReservationPort;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ApplicationLinkQuotaUsageMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class MybatisApplicationLinkQuotaReservationPort implements ApplicationLinkQuotaReservationPort {

    private static final int LOCK_TIMEOUT_SECONDS = 5;

    private final ApplicationLinkQuotaUsageMapper mapper;

    public MybatisApplicationLinkQuotaReservationPort(ApplicationLinkQuotaUsageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public boolean tryReserveMonthlyLink(
            long tenantId,
            long applicationId,
            LocalDate monthStartUtc,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc,
            long monthlyLinkLimit
    ) {
        if (monthlyLinkLimit <= 0) {
            return true;
        }
        if (tenantId <= 0 || applicationId <= 0 || monthStartUtc == null || fromInclusiveUtc == null
                || toExclusiveUtc == null || !toExclusiveUtc.isAfter(fromInclusiveUtc)) {
            return false;
        }
        String lockName = monthlyUsageLockName(tenantId, applicationId, monthStartUtc);
        Integer acquired = mapper.acquireMonthlyLinkUsageLock(lockName, LOCK_TIMEOUT_SECONDS);
        if (acquired == null || acquired != 1) {
            return false;
        }
        try {
            mapper.ensureMonthlyLinkUsage(
                    tenantId,
                    applicationId,
                    monthStartUtc,
                    fromInclusiveUtc,
                    toExclusiveUtc
            );
            return mapper.incrementMonthlyLinkUsage(
                    tenantId,
                    applicationId,
                    monthStartUtc,
                    monthlyLinkLimit
            ) > 0;
        } finally {
            mapper.releaseMonthlyLinkUsageLock(lockName);
        }
    }

    private static String monthlyUsageLockName(long tenantId, long applicationId, LocalDate monthStartUtc) {
        return "lf:ql:" + tenantId + ":" + applicationId + ":" + monthStartUtc;
    }
}

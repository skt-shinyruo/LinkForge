package com.linkforge.analytics.infrastructure.query;

import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class MybatisApplicationClickUsagePort implements ApplicationClickUsagePort {

    private final AnalyticsQueryRepository queryRepository;

    public MybatisApplicationClickUsagePort(AnalyticsQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public long countApplicationClicks(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc
    ) {
        if (tenantId <= 0 || applicationId <= 0 || fromInclusiveUtc == null || toExclusiveUtc == null
                || !toExclusiveUtc.isAfter(fromInclusiveUtc)) {
            return 0L;
        }
        return queryRepository.countApplicationPv(tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc);
    }
}

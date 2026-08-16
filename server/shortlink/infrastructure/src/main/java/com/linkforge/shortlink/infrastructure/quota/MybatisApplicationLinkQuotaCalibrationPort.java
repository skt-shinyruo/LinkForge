package com.linkforge.shortlink.infrastructure.quota;

import com.linkforge.shortlink.application.port.ApplicationLinkQuotaCalibrationPort;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ApplicationLinkQuotaUsageMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 使用与创建短链相同月度锁校准 ownership reconciliation 用量的 MySQL 实现。 */
@Component
public class MybatisApplicationLinkQuotaCalibrationPort implements ApplicationLinkQuotaCalibrationPort {

    private static final int LOCK_TIMEOUT_SECONDS = 5;

    private final ApplicationLinkQuotaUsageMapper mapper;

    public MybatisApplicationLinkQuotaCalibrationPort(ApplicationLinkQuotaUsageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void includeReconciledLink(
            long tenantId,
            long applicationId,
            LocalDate monthStartUtc,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc
    ) {
        if (tenantId <= 0 || applicationId <= 0 || monthStartUtc == null || fromInclusiveUtc == null
                || toExclusiveUtc == null || !toExclusiveUtc.isAfter(fromInclusiveUtc)) {
            throw new IllegalArgumentException("月度用量校准参数无效");
        }

        String lockName = MybatisApplicationLinkQuotaReservationPort.monthlyUsageLockName(
                tenantId,
                applicationId,
                monthStartUtc
        );
        Integer acquired = mapper.acquireMonthlyLinkUsageLock(lockName, LOCK_TIMEOUT_SECONDS);
        if (acquired == null || acquired != 1) {
            throw new IllegalStateException("无法获取应用月度发链用量锁");
        }
        try {
            int initialized = mapper.ensureMonthlyLinkUsage(
                    tenantId,
                    applicationId,
                    monthStartUtc,
                    fromInclusiveUtc,
                    toExclusiveUtc
            );
            if (initialized == 0 && mapper.incrementMonthlyLinkUsageUnbounded(
                    tenantId,
                    applicationId,
                    monthStartUtc
            ) != 1) {
                throw new IllegalStateException("应用月度发链用量校准失败");
            }
        } finally {
            mapper.releaseMonthlyLinkUsageLock(lockName);
        }
    }
}

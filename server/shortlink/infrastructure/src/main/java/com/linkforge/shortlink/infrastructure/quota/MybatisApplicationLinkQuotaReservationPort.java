package com.linkforge.shortlink.infrastructure.quota;

import com.linkforge.shortlink.application.port.ApplicationLinkQuotaReservationPort;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ApplicationLinkQuotaUsageMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基于 MySQL 月度用量表预留应用发链额度。
 *
 * <p>同一 {@code tenantId/applicationId/monthStartUtc} 的首次基线回填和递增由
 * MySQL named lock 串行化，最终是否可预留仍由带上限条件的原子 {@code UPDATE}
 * 决定。存在调用方事务时，{@link Transactional} 的默认 {@code REQUIRED} 传播会加入该
 * 事务，因此创建短链后若整体回滚，用量递增也会一并回滚，无需额外执行减计数补偿；单独
 * 调用本端口时则由本方法创建并提交事务。</p>
 *
 * <p>named lock 属于数据库连接而非事务：获取、回填、递增和释放必须使用事务绑定的
 * 同一连接，且必须在 {@code finally} 中显式释放。获取超时或数据库未返回成功标志时按
 * 预留失败处理（fail-closed）；SQL 异常则向上抛出并触发事务回滚。</p>
 */
@Component
public class MybatisApplicationLinkQuotaReservationPort implements ApplicationLinkQuotaReservationPort {

    private static final int LOCK_TIMEOUT_SECONDS = 5;

    private final ApplicationLinkQuotaUsageMapper mapper;

    public MybatisApplicationLinkQuotaReservationPort(ApplicationLinkQuotaUsageMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 尝试为指定 UTC 月份预留一个发链名额。
     *
     * <p>{@code monthlyLinkLimit <= 0} 表示不启用配额限制。首次出现的月份会先按
     * {@code [fromInclusiveUtc, toExclusiveUtc)} 内已有短链数建立基线，再执行不超过上限的
     * 原子递增。锁等待最多 5 秒；超时、锁获取失败或额度已满均返回 {@code false}。</p>
     *
     * @return 已成功递增月度用量时返回 {@code true}；无需限额时也返回 {@code true}
     */
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

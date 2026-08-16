package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.port.ApplicationLinkQuotaCalibrationPort;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 将一条历史无 scope 短链收敛到经过授权的应用和域名。
 *
 * <p>本用例是 ownership 迁移的唯一公共写入口。它依次执行目标 scope 校验、聚合命名 mutation、版本
 * CAS、当月 quota 校准、领域事件 durable append 和旧/新 redirect identity 的 durable 缓存失效；这些
 * 步骤处于同一事务。批量迁移必须逐条调用本用例，不能绕过聚合或直接执行 ownership SQL。</p>
 *
 * <p>相同目标重复调用返回 {@link ShortLinkOwnershipReconciliationResult.Status#ALREADY_RECONCILED}，不会
 * 再推进版本、额度或副作用。CAS 未命中返回可重试冲突；已绑定到其他 scope、记录不存在分别返回稳定
 * 终态。目标应用、域名、租户或授权失败由 {@link ApplicationScopePort} 原样抛出。</p>
 */
@Component
public class ShortLinkOwnershipReconciliationService {

    private final ApplicationScopePort applicationScopePort;
    private final ShortLinkRepository shortLinkRepository;
    private final ApplicationLinkQuotaCalibrationPort quotaCalibrationPort;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final RedirectCacheInvalidationOutboxPort cacheInvalidationOutbox;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;

    public ShortLinkOwnershipReconciliationService(
            ApplicationScopePort applicationScopePort,
            ShortLinkRepository shortLinkRepository,
            ApplicationLinkQuotaCalibrationPort quotaCalibrationPort,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            RedirectCacheInvalidationOutboxPort cacheInvalidationOutbox,
            RedirectCacheSyncPort redirectCacheSync,
            PostCommitHookPort postCommitHookPort,
            Clock clock
    ) {
        this.applicationScopePort = applicationScopePort;
        this.shortLinkRepository = shortLinkRepository;
        this.quotaCalibrationPort = quotaCalibrationPort;
        this.domainEventDispatcher = domainEventDispatcher;
        this.cacheInvalidationOutbox = cacheInvalidationOutbox;
        this.redirectCacheSync = redirectCacheSync;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
    }

    /**
     * 在一个事务内把指定短链收敛到目标 scope。
     */
    @Transactional
    public ShortLinkOwnershipReconciliationResult reconcile(
            long tenantId,
            long linkId,
            long applicationId,
            long domainId
    ) {
        applicationScopePort.requireApplicationAndDomainAuthorized(tenantId, applicationId, domainId);

        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId).orElse(null);
        if (link == null) {
            return result(linkId, ShortLinkOwnershipReconciliationResult.Status.NOT_FOUND, -1L);
        }

        Long previousDomainId = link.domainId();
        Instant changedAt = clock.instant();
        LocalDateTime changedAtUtc = LocalDateTime.ofInstant(changedAt, ZoneOffset.UTC);
        boolean changed;
        try {
            changed = link.reconcileOwnership(applicationId, domainId, changedAtUtc);
        } catch (ShortLinkDomainException ex) {
            if (ex.reason() == ShortLinkDomainException.Reason.INVALID_OWNERSHIP_SCOPE) {
                return result(linkId, ShortLinkOwnershipReconciliationResult.Status.OWNERSHIP_CONFLICT, link.version());
            }
            throw ex;
        }
        if (!changed) {
            return result(linkId, ShortLinkOwnershipReconciliationResult.Status.ALREADY_RECONCILED, link.version());
        }
        if (!shortLinkRepository.update(link)) {
            return result(linkId, ShortLinkOwnershipReconciliationResult.Status.RETRYABLE_CONFLICT, link.version() - 1);
        }

        calibrateCurrentMonthUsage(link, changedAt);
        domainEventDispatcher.publish(link, changedAt);
        invalidateRedirectIdentity(link, previousDomainId);
        if (!Objects.equals(previousDomainId, link.domainId())) {
            invalidateRedirectIdentity(link, link.domainId());
        }
        return result(linkId, ShortLinkOwnershipReconciliationResult.Status.RECONCILED, link.version());
    }

    private void calibrateCurrentMonthUsage(ShortLink link, Instant now) {
        LocalDate monthStart = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1);
        LocalDateTime fromInclusiveUtc = monthStart.atStartOfDay();
        LocalDateTime toExclusiveUtc = monthStart.plusMonths(1).atStartOfDay();
        LocalDateTime createdAtUtc = link.createdAtUtc();
        if (createdAtUtc == null || createdAtUtc.isBefore(fromInclusiveUtc) || !createdAtUtc.isBefore(toExclusiveUtc)) {
            return;
        }
        quotaCalibrationPort.includeReconciledLink(
                link.tenantId(),
                link.applicationId(),
                monthStart,
                fromInclusiveUtc,
                toExclusiveUtc
        );
    }

    private void invalidateRedirectIdentity(ShortLink link, Long domainId) {
        RedirectCacheInvalidations.enqueueAndRunAfterCommit(
                cacheInvalidationOutbox,
                postCommitHookPort,
                redirectCacheSync,
                link.tenantId(),
                domainId,
                link.code().value()
        );
    }

    private static ShortLinkOwnershipReconciliationResult result(
            long linkId,
            ShortLinkOwnershipReconciliationResult.Status status,
            long version
    ) {
        return new ShortLinkOwnershipReconciliationResult(linkId, status, version);
    }
}

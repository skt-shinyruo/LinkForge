package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.ApplicationClickQuotaReservationPort;
import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.redirect.LinkMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class RedirectQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(RedirectQuotaGuard.class);

    private final Clock clock;
    private final ApplicationScopePort applicationScopePort;
    private final ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort;
    private final boolean failOpenOnQuotaErrors;

    @Autowired
    public RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort,
            @Value("${app.analytics.quota.fail-open:false}") boolean failOpenOnQuotaErrors
    ) {
        this.clock = clock;
        this.applicationScopePort = applicationScopePort == null ? noQuotaApplicationScopePort() : applicationScopePort;
        this.applicationClickQuotaReservationPort = applicationClickQuotaReservationPort == null
                ? allowAllClickQuotaReservationPort()
                : applicationClickQuotaReservationPort;
        this.failOpenOnQuotaErrors = failOpenOnQuotaErrors;
    }

    public RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort
    ) {
        this(clock, applicationScopePort, applicationClickQuotaReservationPort, false);
    }

    static RedirectQuotaGuard from(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickUsagePort applicationClickUsagePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort
    ) {
        ApplicationClickQuotaReservationPort reservationPort = applicationClickQuotaReservationPort == null
                ? fallbackClickQuotaReservationPort(applicationClickUsagePort)
                : applicationClickQuotaReservationPort;
        return new RedirectQuotaGuard(clock, applicationScopePort, reservationPort);
    }

    static RedirectQuotaGuard disabled(Clock clock) {
        return new RedirectQuotaGuard(clock, noQuotaApplicationScopePort(), allowAllClickQuotaReservationPort());
    }

    RedirectResolution.UnavailableReason unavailableReason(LinkMeta meta) {
        if (meta == null) {
            return null;
        }
        Long applicationId = meta.applicationId();
        if (applicationId == null || applicationId <= 0) {
            return null;
        }
        Optional<ApplicationQuotaView> quota;
        try {
            quota = applicationScopePort.findApplicationQuota(meta.tenantId(), applicationId);
        } catch (Exception e) {
            log.debug(
                    "find application quota failed (failOpen={}): tenantId={}, applicationId={}, err={}",
                    failOpenOnQuotaErrors,
                    meta.tenantId(),
                    applicationId,
                    e.getMessage()
            );
            return quotaFailureReason();
        }
        if (quota == null || quota.isEmpty()) {
            return null;
        }
        long monthlyClickLimit = quota.get().monthlyClickLimit();
        if (monthlyClickLimit <= 0) {
            return null;
        }
        LocalDate monthStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        boolean reserved;
        try {
            reserved = applicationClickQuotaReservationPort.tryReserveMonthlyClick(
                    meta.tenantId(),
                    applicationId,
                    monthStart,
                    monthEnd,
                    monthlyClickLimit
            );
        } catch (Exception e) {
            log.debug(
                    "reserve monthly click quota failed (failOpen={}): tenantId={}, applicationId={}, monthStart={}, err={}",
                    failOpenOnQuotaErrors,
                    meta.tenantId(),
                    applicationId,
                    monthStart,
                    e.getMessage()
            );
            return quotaFailureReason();
        }
        return reserved ? null : RedirectResolution.UnavailableReason.QUOTA_EXCEEDED;
    }

    private RedirectResolution.UnavailableReason quotaFailureReason() {
        return failOpenOnQuotaErrors ? null : RedirectResolution.UnavailableReason.QUOTA_EXCEEDED;
    }

    private static ApplicationClickQuotaReservationPort fallbackClickQuotaReservationPort(
            ApplicationClickUsagePort clickUsagePort
    ) {
        ApplicationClickUsagePort usagePort = clickUsagePort == null ? noClickUsagePort() : clickUsagePort;
        return (tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc, monthlyClickLimit) ->
                usagePort.countApplicationClicks(tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc) < monthlyClickLimit;
    }

    private static ApplicationClickUsagePort noClickUsagePort() {
        return (tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc) -> 0L;
    }

    private static ApplicationClickQuotaReservationPort allowAllClickQuotaReservationPort() {
        return (tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc, monthlyClickLimit) -> true;
    }

    private static ApplicationScopePort noQuotaApplicationScopePort() {
        return new ApplicationScopePort() {
            @Override
            public void requireApplicationExists(long tenantId, long applicationId) {
            }

            @Override
            public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
            }

            @Override
            public Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId) {
                return Optional.empty();
            }
        };
    }
}

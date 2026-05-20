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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RedirectQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(RedirectQuotaGuard.class);
    private static final long DEFAULT_QUOTA_LOOKUP_CACHE_TTL_SECONDS = 30L;

    private final Clock clock;
    private final ApplicationScopePort applicationScopePort;
    private final ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort;
    private final boolean failOpenOnQuotaErrors;
    private final long quotaLookupCacheTtlMillis;
    private final ConcurrentMap<QuotaCacheKey, CachedApplicationQuota> quotaLookupCache = new ConcurrentHashMap<>();

    @Autowired
    public RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort,
            @Value("${app.analytics.quota.fail-open:false}") boolean failOpenOnQuotaErrors,
            @Value("${app.analytics.quota.lookup-cache-ttl-seconds:30}") long quotaLookupCacheTtlSeconds
    ) {
        this.clock = clock;
        this.applicationScopePort = applicationScopePort == null ? noQuotaApplicationScopePort() : applicationScopePort;
        this.applicationClickQuotaReservationPort = applicationClickQuotaReservationPort == null
                ? allowAllClickQuotaReservationPort()
                : applicationClickQuotaReservationPort;
        this.failOpenOnQuotaErrors = failOpenOnQuotaErrors;
        this.quotaLookupCacheTtlMillis = toMillis(quotaLookupCacheTtlSeconds);
    }

    public RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort,
            boolean failOpenOnQuotaErrors
    ) {
        this(
                clock,
                applicationScopePort,
                applicationClickQuotaReservationPort,
                failOpenOnQuotaErrors,
                DEFAULT_QUOTA_LOOKUP_CACHE_TTL_SECONDS
        );
    }

    public RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort
    ) {
        this(clock, applicationScopePort, applicationClickQuotaReservationPort, false, DEFAULT_QUOTA_LOOKUP_CACHE_TTL_SECONDS);
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
            quota = findApplicationQuota(meta.tenantId(), applicationId);
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

    private Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId) throws Exception {
        if (quotaLookupCacheTtlMillis <= 0) {
            return normalizeQuota(applicationScopePort.findApplicationQuota(tenantId, applicationId));
        }

        QuotaCacheKey key = new QuotaCacheKey(tenantId, applicationId);
        long nowMillis = clock.instant().toEpochMilli();
        CachedApplicationQuota cached = quotaLookupCache.get(key);
        if (cached != null && cached.isFresh(nowMillis)) {
            return cached.quota();
        }

        AtomicReference<Optional<ApplicationQuotaView>> quotaRef = new AtomicReference<>();
        AtomicReference<Exception> failureRef = new AtomicReference<>();
        quotaLookupCache.compute(key, (ignored, existing) -> {
            long computeNowMillis = clock.instant().toEpochMilli();
            if (existing != null && existing.isFresh(computeNowMillis)) {
                quotaRef.set(existing.quota());
                return existing;
            }
            try {
                Optional<ApplicationQuotaView> loaded =
                        normalizeQuota(applicationScopePort.findApplicationQuota(tenantId, applicationId));
                quotaRef.set(loaded);
                return new CachedApplicationQuota(loaded, expiresAtEpochMillis(computeNowMillis));
            } catch (Exception e) {
                failureRef.set(e);
                return null;
            }
        });
        if (failureRef.get() != null) {
            throw failureRef.get();
        }
        return quotaRef.get();
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

    private long expiresAtEpochMillis(long nowMillis) {
        long expiresAt = nowMillis + quotaLookupCacheTtlMillis;
        return expiresAt < nowMillis ? Long.MAX_VALUE : expiresAt;
    }

    private static long toMillis(long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return 0L;
        }
        if (ttlSeconds > Long.MAX_VALUE / 1_000L) {
            return Long.MAX_VALUE;
        }
        return ttlSeconds * 1_000L;
    }

    private static Optional<ApplicationQuotaView> normalizeQuota(Optional<ApplicationQuotaView> quota) {
        return quota == null ? Optional.empty() : quota;
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

    private record QuotaCacheKey(long tenantId, long applicationId) {
    }

    private record CachedApplicationQuota(
            Optional<ApplicationQuotaView> quota,
            long expiresAtEpochMillis
    ) {

        private boolean isFresh(long nowMillis) {
            return expiresAtEpochMillis > nowMillis;
        }
    }
}

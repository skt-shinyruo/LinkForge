package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.springframework.beans.factory.annotation.Autowired;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.redirect.domain.RedirectAvailabilityPolicy;
import com.linkforge.redirect.domain.RedirectDecision;
import com.linkforge.redirect.domain.RedirectLookupKey;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class RedirectService {

    private final LinkCachePort linkCache;
    private final ShortLinkReadPort shortLinkReadPort;
    private final VisitRecorderPort visitRecorderPort;
    private final Clock clock;
    private final ApplicationScopePort applicationScopePort;
    private final ApplicationClickUsagePort applicationClickUsagePort;
    private final RedirectAvailabilityPolicy availabilityPolicy;

    @Autowired
    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickUsagePort applicationClickUsagePort
    ) {
        this(
                linkCache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                applicationScopePort,
                applicationClickUsagePort,
                new RedirectAvailabilityPolicy()
        );
    }

    RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickUsagePort applicationClickUsagePort,
            RedirectAvailabilityPolicy availabilityPolicy
    ) {
        this.linkCache = linkCache;
        this.shortLinkReadPort = shortLinkReadPort;
        this.visitRecorderPort = visitRecorderPort;
        this.clock = clock;
        this.applicationScopePort = applicationScopePort == null ? noQuotaApplicationScopePort() : applicationScopePort;
        this.applicationClickUsagePort = applicationClickUsagePort == null ? noClickUsagePort() : applicationClickUsagePort;
        this.availabilityPolicy = availabilityPolicy;
    }

    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock
    ) {
        this(
                linkCache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                noQuotaApplicationScopePort(),
                noClickUsagePort()
        );
    }

    /**
     * 仅解析短码，不写统计；用于预览页等“未确认不计数”的场景。
     */
    public LinkMeta resolve(String code) {
        return resolveMeta(null, code);
    }

    public LinkMeta resolve(String host, String code) {
        return resolveMeta(host, code);
    }

    public RedirectResolution resolve(ResolveRedirectRequest request) {
        if (request == null) {
            return RedirectResolution.notFound(null, false);
        }
        Optional<RedirectLookupKey> lookupKey = RedirectLookupKey.tryCreate(request.host(), request.code());
        if (lookupKey.isEmpty()) {
            return RedirectResolution.notFound(request.code(), request.htmlRequest());
        }
        String normalizedCode = lookupKey.get().code();

        LinkMeta meta = findMeta(lookupKey.get().host(), normalizedCode);
        if (meta == null) {
            return RedirectResolution.notFound(normalizedCode, request.htmlRequest());
        }

        RedirectResolution.UnavailableReason unavailableReason = unavailableReason(meta);
        if (unavailableReason != null) {
            return RedirectResolution.unavailable(normalizedCode, request.htmlRequest(), meta, unavailableReason);
        }

        if (request.htmlRequest() && meta.previewEnabled() && !request.confirmed()) {
            return RedirectResolution.preview(normalizedCode, true, meta);
        }

        visitRecorderPort.recordVisit(toRedirectVisitRecord(meta, request.visitInput()));
        return RedirectResolution.redirect(normalizedCode, request.htmlRequest(), meta);
    }

    /**
     * 在“确认跳转”时写统计；若链接不可用则不写入。
     */
    public void recordVisitIfAvailable(LinkMeta meta, RedirectVisitInput visitInput) {
        if (isAvailable(meta)) {
            visitRecorderPort.recordVisit(toRedirectVisitRecord(meta, visitInput));
        }
    }

    public LinkMeta resolveAndRecord(String code, RedirectVisitInput visitInput) {
        LinkMeta meta = resolveMeta(null, code);
        recordVisitIfAvailable(meta, visitInput);
        return meta;
    }

    public LinkMeta resolveAndRecord(String host, String code, RedirectVisitInput visitInput) {
        LinkMeta meta = resolveMeta(host, code);
        recordVisitIfAvailable(meta, visitInput);
        return meta;
    }

    private LinkMeta resolveMeta(String host, String code) {
        RedirectLookupKey lookupKey = RedirectLookupKey.tryCreate(host, code)
                .orElseThrow(() -> new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND));
        LinkMeta meta = findMeta(lookupKey.host(), lookupKey.code());
        if (meta == null) {
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }
        return meta;
    }

    private LinkMeta findMeta(String host, String normalized) {
        LinkCachePort.LookupResult cached = linkCache.lookup(host, normalized);
        if (cached.notFound()) {
            return null;
        }
        if (cached.hit()) {
            return cached.meta();
        }

        LinkMeta meta = shortLinkReadPort.findRedirectMetaByHostAndCode(host, normalized)
                .map(RedirectService::toLinkMeta)
                .orElse(null);
        if (meta != null) {
            linkCache.tryPut(host, meta);
            return meta;
        }

        // Monolith correctness uses the authoritative source. Projectors remain warm/recovery infrastructure.
        linkCache.markNotFound(host, normalized);
        return null;
    }

    private static LinkMeta toLinkMeta(ShortLinkReadPort.RedirectLinkView meta) {
        return new LinkMeta(
                meta.linkId(),
                meta.tenantId(),
                meta.code(),
                meta.originalUrl(),
                meta.enabled(),
                toUtcLocalDateTime(meta.expiresAtUtc()),
                meta.redirectStatusCode(),
                meta.previewEnabled(),
                meta.unavailableLandingUrl(),
                meta.queryForwardMode(),
                meta.queryForwardAllowlist(),
                meta.hostname(),
                meta.applicationId(),
                meta.domainId(),
                meta.lifecycleState()
        );
    }

    private boolean isAvailable(LinkMeta meta) {
        return unavailableReason(meta) == null;
    }

    private RedirectResolution.UnavailableReason unavailableReason(LinkMeta meta) {
        if (meta == null) {
            return null;
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        RedirectDecision decision = availabilityPolicy.evaluate(
                meta.enabled(),
                meta.activeLifecycle(),
                meta.expiresAt(),
                nowUtc,
                quotaExceeded(meta)
        );
        if (decision.kind() != RedirectDecision.Kind.UNAVAILABLE) {
            return null;
        }
        return toUnavailableReason(decision.reason());
    }

    private RedirectVisitRecord toRedirectVisitRecord(LinkMeta meta, RedirectVisitInput visitInput) {
        return new RedirectVisitRecord(
                meta.tenantId(),
                meta.id(),
                clock.instant().toEpochMilli(),
                meta.applicationId(),
                meta.domainId(),
                meta.code(),
                meta.originalUrl(),
                new VisitContext(
                        visitInput == null ? null : visitInput.ip(),
                        visitInput == null ? null : visitInput.userAgent(),
                        visitInput == null ? null : visitInput.referer(),
                        visitInput == null ? null : visitInput.acceptLanguage(),
                        visitInput == null ? null : visitInput.trackingParams()
                )
        );
    }

    private static LocalDateTime toUtcLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private boolean quotaExceeded(LinkMeta meta) {
        Long applicationId = meta.applicationId();
        if (applicationId == null || applicationId <= 0) {
            return false;
        }
        Optional<ApplicationQuotaView> quota = applicationScopePort.findApplicationQuota(meta.tenantId(), applicationId);
        if (quota.isEmpty()) {
            return false;
        }
        long monthlyClickLimit = quota.get().monthlyClickLimit();
        if (monthlyClickLimit <= 0) {
            return false;
        }
        LocalDate monthStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).withDayOfMonth(1);
        long currentMonthClicks = applicationClickUsagePort.countApplicationClicks(
                meta.tenantId(),
                applicationId,
                monthStart,
                monthStart.plusMonths(1)
        );
        return currentMonthClicks >= monthlyClickLimit;
    }

    private static RedirectResolution.UnavailableReason toUnavailableReason(RedirectDecision.UnavailableReason reason) {
        return switch (reason) {
            case DISABLED -> RedirectResolution.UnavailableReason.DISABLED;
            case EXPIRED -> RedirectResolution.UnavailableReason.EXPIRED;
            case QUOTA_EXCEEDED -> RedirectResolution.UnavailableReason.QUOTA_EXCEEDED;
        };
    }

    private static ApplicationClickUsagePort noClickUsagePort() {
        return (tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc) -> 0L;
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

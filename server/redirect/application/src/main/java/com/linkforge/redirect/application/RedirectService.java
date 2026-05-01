package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.analytics.ApplicationClickQuotaReservationPort;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class RedirectService {

    private static final Logger log = LoggerFactory.getLogger(RedirectService.class);

    private final LinkCachePort linkCache;
    private final ShortLinkReadPort shortLinkReadPort;
    private final VisitRecorderPort visitRecorderPort;
    private final Clock clock;
    private final ApplicationScopePort applicationScopePort;
    private final ApplicationClickUsagePort applicationClickUsagePort;
    private final ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort;

    @Autowired
    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickUsagePort applicationClickUsagePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort
    ) {
        this.linkCache = linkCache;
        this.shortLinkReadPort = shortLinkReadPort;
        this.visitRecorderPort = visitRecorderPort;
        this.clock = clock;
        this.applicationScopePort = applicationScopePort == null ? noQuotaApplicationScopePort() : applicationScopePort;
        this.applicationClickUsagePort = applicationClickUsagePort == null ? noClickUsagePort() : applicationClickUsagePort;
        this.applicationClickQuotaReservationPort = applicationClickQuotaReservationPort == null
                ? fallbackClickQuotaReservationPort(this.applicationClickUsagePort)
                : applicationClickQuotaReservationPort;
    }

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
                null
        );
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
                noClickUsagePort(),
                null
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
        String normalizedCode = normalizeCode(request.code());
        if (normalizedCode == null) {
            return RedirectResolution.notFound(request.code(), request.htmlRequest());
        }

        LinkMeta meta = findMeta(request.host(), normalizedCode);
        if (meta == null) {
            return RedirectResolution.notFound(normalizedCode, request.htmlRequest());
        }

        RedirectResolution.UnavailableReason unavailableReason = staticUnavailableReason(meta);
        if (unavailableReason != null) {
            return RedirectResolution.unavailable(normalizedCode, request.htmlRequest(), meta, unavailableReason);
        }

        if (request.htmlRequest() && meta.previewEnabled() && !request.confirmed()) {
            return RedirectResolution.preview(normalizedCode, true, meta);
        }

        unavailableReason = quotaUnavailableReason(meta);
        if (unavailableReason != null) {
            return RedirectResolution.unavailable(normalizedCode, request.htmlRequest(), meta, unavailableReason);
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
        String normalized = normalizeAndValidateCode(code);
        LinkMeta meta = findMeta(host, normalized);
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

    private static String normalizeAndValidateCode(String code) {
        String normalized = normalizeCode(code);
        if (normalized == null) {
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }
        return normalized;
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String v = code.trim();
        if (v.isBlank()) {
            return null;
        }
        // 约束：短码最大长度为 32
        if (v.length() > 32) {
            return null;
        }
        // 安全默认：仅允许字母数字，避免异常字符导致 key/日志/路由复杂度上升
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z');
            if (!ok) {
                return null;
            }
        }
        return v;
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
        return redirectUnavailableReason(meta) == null;
    }

    private RedirectResolution.UnavailableReason redirectUnavailableReason(LinkMeta meta) {
        RedirectResolution.UnavailableReason reason = staticUnavailableReason(meta);
        return reason == null ? quotaUnavailableReason(meta) : reason;
    }

    private RedirectResolution.UnavailableReason staticUnavailableReason(LinkMeta meta) {
        if (meta == null) {
            return null;
        }
        if (!meta.enabled()) {
            return RedirectResolution.UnavailableReason.DISABLED;
        }
        if (!meta.activeLifecycle()) {
            return RedirectResolution.UnavailableReason.DISABLED;
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (meta.expiresAt() != null && !meta.expiresAt().isAfter(nowUtc)) {
            return RedirectResolution.UnavailableReason.EXPIRED;
        }
        return null;
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

    private RedirectResolution.UnavailableReason quotaUnavailableReason(LinkMeta meta) {
        Long applicationId = meta.applicationId();
        if (applicationId == null || applicationId <= 0) {
            return null;
        }
        Optional<ApplicationQuotaView> quota = applicationScopePort.findApplicationQuota(meta.tenantId(), applicationId);
        if (quota.isEmpty()) {
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
                    "reserve monthly click quota failed: tenantId={}, applicationId={}, monthStart={}, err={}",
                    meta.tenantId(),
                    applicationId,
                    monthStart,
                    e.getMessage()
            );
            reserved = false;
        }
        return reserved ? null : RedirectResolution.UnavailableReason.QUOTA_EXCEEDED;
    }

    private static ApplicationClickUsagePort noClickUsagePort() {
        return (tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc) -> 0L;
    }

    private static ApplicationClickQuotaReservationPort fallbackClickQuotaReservationPort(ApplicationClickUsagePort clickUsagePort) {
        ApplicationClickUsagePort usagePort = clickUsagePort == null ? noClickUsagePort() : clickUsagePort;
        return (tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc, monthlyClickLimit) ->
                usagePort.countApplicationClicks(tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc) < monthlyClickLimit;
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

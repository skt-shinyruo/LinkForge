package com.linkforge.edge.redirect.service;

import com.linkforge.edge.analytics.service.AnalyticsService;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.web.VisitInfo;
import com.linkforge.redirect.service.LinkCacheService;
import com.linkforge.redirect.service.LinkMeta;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RedirectService {

    private final ShortLinkLookupRepository shortLinkLookupRepository;
    private final LinkCacheService linkCacheService;
    private final AnalyticsService analyticsService;

    public RedirectService(
            ShortLinkLookupRepository shortLinkLookupRepository,
            LinkCacheService linkCacheService,
            AnalyticsService analyticsService
    ) {
        this.shortLinkLookupRepository = shortLinkLookupRepository;
        this.linkCacheService = linkCacheService;
        this.analyticsService = analyticsService;
    }

    /**
     * 仅解析短码，不写统计；用于预览页等“未确认不计数”的场景。
     */
    public LinkMeta resolve(String code) {
        return resolveMeta(code);
    }

    /**
     * 在“确认跳转”时写统计；若链接不可用则不写入。
     */
    public void recordVisitIfAvailable(LinkMeta meta, VisitInfo visitInfo) {
        if (isAvailable(meta)) {
            analyticsService.recordVisit(meta.tenantId(), meta.id(), visitInfo);
        }
    }

    public LinkMeta resolveAndRecord(String code, VisitInfo visitInfo) {
        LinkMeta meta = resolveMeta(code);
        recordVisitIfAvailable(meta, visitInfo);
        return meta;
    }

    private LinkMeta resolveMeta(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.LINK_NOT_FOUND);
        }

        LinkMeta cached = linkCacheService.get(code);
        if (cached != null) {
            return cached;
        }

        ShortLinkLookupRepository.ShortLinkRow e = shortLinkLookupRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_NOT_FOUND));

        LinkMeta meta = new LinkMeta(
                e.id(),
                e.tenantId(),
                e.code(),
                e.originalUrl(),
                e.enabled(),
                e.expiresAt(),
                e.redirectStatusCode(),
                e.previewEnabled(),
                e.unavailableLandingUrl(),
                e.queryForwardMode(),
                e.queryForwardAllowlist()
        );

        linkCacheService.put(meta);
        return meta;
    }

    private static boolean isAvailable(LinkMeta meta) {
        if (meta == null) {
            return false;
        }
        if (!meta.enabled()) {
            return false;
        }
        return meta.expiresAt() == null || meta.expiresAt().isAfter(LocalDateTime.now());
    }
}

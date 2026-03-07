package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.redirect.infrastructure.persistence.ShortLinkLookupRepository;
import com.linkforge.foundation.web.VisitInfo;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RedirectService {

    private final ShortLinkLookupRepository shortLinkLookupRepository;
    private final LinkCachePort linkCache;
    private final VisitRecorderPort visitRecorder;

    public RedirectService(
            ShortLinkLookupRepository shortLinkLookupRepository,
            LinkCachePort linkCache,
            VisitRecorderPort visitRecorder
    ) {
        this.shortLinkLookupRepository = shortLinkLookupRepository;
        this.linkCache = linkCache;
        this.visitRecorder = visitRecorder;
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
            visitRecorder.recordVisit(meta.tenantId(), meta.id(), toVisitContext(visitInfo));
        }
    }

    public LinkMeta resolveAndRecord(String code, VisitInfo visitInfo) {
        LinkMeta meta = resolveMeta(code);
        recordVisitIfAvailable(meta, visitInfo);
        return meta;
    }

    private LinkMeta resolveMeta(String code) {
        String normalized = normalizeAndValidateCode(code);

        LinkCachePort.LookupResult cached = linkCache.lookup(normalized);
        if (cached.notFound()) {
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }
        if (cached.hit()) {
            return cached.meta();
        }

        ShortLinkLookupRepository.ShortLinkRow e = shortLinkLookupRepository.findByCode(normalized)
                .orElse(null);
        if (e == null) {
            // 负缓存：避免随机短码扫描导致缓存穿透，把 MySQL 回源打穿
            linkCache.markNotFound(normalized);
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }

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

        linkCache.tryPut(meta);
        return meta;
    }

    private static String normalizeAndValidateCode(String code) {
        if (code == null) {
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }
        String v = code.trim();
        if (v.isBlank()) {
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }
        // DB 约束：short_links.code 为 VARCHAR(32)
        if (v.length() > 32) {
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }
        // 安全默认：仅允许字母数字，避免异常字符导致 key/日志/路由复杂度上升
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z');
            if (!ok) {
                throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
            }
        }
        return v;
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

    private static VisitContext toVisitContext(VisitInfo v) {
        if (v == null) {
            return null;
        }
        return new VisitContext(
                v.ip(),
                v.userAgent(),
                v.referer(),
                v.acceptLanguage(),
                v.trackingParams()
        );
    }
}

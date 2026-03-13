package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaQueryPort;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.foundation.web.VisitInfo;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class RedirectService {

    private final LinkMetaQueryPort linkMetaQuery;
    private final LinkCachePort linkCache;
    private final VisitRecorderPort visitRecorder;
    private final Clock clock;

    public RedirectService(
            LinkMetaQueryPort linkMetaQuery,
            LinkCachePort linkCache,
            VisitRecorderPort visitRecorder,
            Clock clock
    ) {
        this.linkMetaQuery = linkMetaQuery;
        this.linkCache = linkCache;
        this.visitRecorder = visitRecorder;
        this.clock = clock;
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

        LinkMeta meta = linkMetaQuery.findActiveByCode(normalized).orElse(null);
        if (meta == null) {
            // 负缓存：避免随机短码扫描导致缓存穿透，把 MySQL 回源打穿
            linkCache.markNotFound(normalized);
            throw new RedirectBusinessException(RedirectErrorCode.LINK_NOT_FOUND);
        }

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
        // 约束：短码最大长度为 32
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

    private boolean isAvailable(LinkMeta meta) {
        if (meta == null) {
            return false;
        }
        if (!meta.enabled()) {
            return false;
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return meta.expiresAt() == null || meta.expiresAt().isAfter(nowUtc);
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

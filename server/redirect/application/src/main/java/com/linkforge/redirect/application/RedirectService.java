package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class RedirectService {

    private final LinkCachePort linkCache;
    private final LinkMetaSourcePort linkMetaSource;
    private final VisitRecorderPort visitRecorder;
    private final Clock clock;

    public RedirectService(
            LinkCachePort linkCache,
            LinkMetaSourcePort linkMetaSource,
            VisitRecorderPort visitRecorder,
            Clock clock
    ) {
        this.linkCache = linkCache;
        this.linkMetaSource = linkMetaSource;
        this.visitRecorder = visitRecorder;
        this.clock = clock;
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

        RedirectResolution.UnavailableReason unavailableReason = unavailableReason(meta);
        if (unavailableReason != null) {
            return RedirectResolution.unavailable(normalizedCode, request.htmlRequest(), meta, unavailableReason);
        }

        if (request.htmlRequest() && meta.previewEnabled() && !request.confirmed()) {
            return RedirectResolution.preview(normalizedCode, true, meta);
        }

        recordVisitIfAvailable(meta, request.visitInput());
        return RedirectResolution.redirect(normalizedCode, request.htmlRequest(), meta);
    }

    /**
     * 在“确认跳转”时写统计；若链接不可用则不写入。
     */
    public void recordVisitIfAvailable(LinkMeta meta, RedirectVisitInput visitInput) {
        if (isAvailable(meta)) {
            visitRecorder.recordVisit(meta.tenantId(), meta.id(), toVisitContext(visitInput));
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

        LinkMeta meta = linkMetaSource.findByHostAndCode(host, normalized).orElse(null);
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

    private boolean isAvailable(LinkMeta meta) {
        return unavailableReason(meta) == null;
    }

    private RedirectResolution.UnavailableReason unavailableReason(LinkMeta meta) {
        if (meta == null) {
            return null;
        }
        if (!meta.enabled()) {
            return RedirectResolution.UnavailableReason.DISABLED;
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (meta.expiresAt() != null && !meta.expiresAt().isAfter(nowUtc)) {
            return RedirectResolution.UnavailableReason.EXPIRED;
        }
        return null;
    }

    private static VisitContext toVisitContext(RedirectVisitInput v) {
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

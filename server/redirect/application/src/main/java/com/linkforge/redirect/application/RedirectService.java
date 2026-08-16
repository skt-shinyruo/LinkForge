package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 跳转解析应用服务。
 *
 * <p>该服务不拥有短链事实：它先读取 {@link LinkCachePort} 的三态结果，只有缓存未命中或
 * 缓存不可用时才同步调用 {@link ShortLinkReadPort}。因此 Redis 的读写失败最多增加回源压力，
 * 不得被解释为短链不存在。</p>
 *
 * <p>{@link #resolve(ResolveRedirectRequest)} 是唯一的跳转决策入口。它按固定顺序校验静态可用性、
 * HTML 预览、应用点击额度，并且仅在确认要返回重定向时记录访问。预览、未找到、禁用、过期和
 * 额度拒绝均不产生访问记录。</p>
 */
@Service
public class RedirectService {

    private final LinkCachePort linkCache;
    private final ShortLinkReadPort shortLinkReadPort;
    private final VisitRecorderPort visitRecorderPort;
    private final Clock clock;
    private final RedirectQuotaGuard quotaGuard;
    private final OperationalMetrics metrics;

    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock,
            RedirectQuotaGuard quotaGuard,
            OperationalMetrics metrics
    ) {
        this.linkCache = linkCache;
        this.shortLinkReadPort = shortLinkReadPort;
        this.visitRecorderPort = visitRecorderPort;
        this.clock = clock;
        this.quotaGuard = quotaGuard;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * 决定一次 HTTP 跳转请求的结果。
     *
     * <p>短码非法、负缓存命中和权威读未命中均返回 {@code NOT_FOUND}。静态不可用优先于预览；
     * 预览页未确认时不会占用点击额度或写 Analytics。确认跳转时，额度预留成功后才调用
     * {@link VisitRecorderPort}，所以记录调用表示已通过 Redirect 本身的业务检查，但不表示
     * Analytics 已经落库。</p>
     *
     * @param request 由 HTTP mapper 构造的请求；{@code null} 等价于未找到
     * @return 可供接口层渲染的决策，不抛出未找到业务异常
     */
    public RedirectResolution resolve(ResolveRedirectRequest request) {
        long startedAt = System.nanoTime();
        try {
            RedirectResolution resolution = resolveDecision(request);
            String result = redirectResultTag(resolution);
            metrics.increment("linkforge.redirect.requests", "result", result);
            metrics.record(
                    "linkforge.redirect.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    result
            );
            return resolution;
        } catch (RuntimeException ex) {
            metrics.increment("linkforge.redirect.requests", "result", "failure");
            metrics.record(
                    "linkforge.redirect.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    "failure"
            );
            throw ex;
        }
    }

    private RedirectResolution resolveDecision(ResolveRedirectRequest request) {
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

    private LinkMeta findMeta(String host, String normalized) {
        // LinkCachePort 将 Redis 故障规范为 MISS；只有 NEGATIVE 才能终止权威回源。
        LinkCachePort.LookupResult cached = linkCache.lookup(host, normalized);
        if (cached.notFound()) {
            return null;
        }
        if (cached.hit()) {
            return cached.meta();
        }

        long startedAt = System.nanoTime();
        LinkMeta meta;
        try {
            meta = shortLinkReadPort.findRedirectMetaByHostAndCode(host, normalized).orElse(null);
            metrics.record(
                    "linkforge.redirect.authoritative_lookup",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    meta == null ? "miss" : "hit"
            );
        } catch (RuntimeException ex) {
            metrics.record(
                    "linkforge.redirect.authoritative_lookup",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    "failure"
            );
            throw ex;
        }
        if (meta != null) {
            linkCache.tryPut(host, meta);
            return meta;
        }

        // 缓存不是事实来源：只有权威读确认不存在才允许写负缓存。
        linkCache.markNotFound(host, normalized);
        return null;
    }

    private static String redirectResultTag(RedirectResolution resolution) {
        if (resolution == null || resolution.kind() == null) {
            return "unknown";
        }
        if (resolution.kind() != RedirectResolution.Kind.UNAVAILABLE || resolution.unavailableReason() == null) {
            return resolution.kind().name().toLowerCase(java.util.Locale.ROOT);
        }
        return "unavailable_" + resolution.unavailableReason().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String v = code.trim();
        if (v.isBlank()) {
            return null;
        }
        // 短码约束同时限制路由、Redis key 和风险控制 key 的输入面。
        if (v.length() > 32) {
            return null;
        }
        // 安全默认：仅允许字母数字，避免异常字符导致 key/日志/路由复杂度上升。
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

    private RedirectResolution.UnavailableReason quotaUnavailableReason(LinkMeta meta) {
        return quotaGuard.unavailableReason(meta);
    }

}

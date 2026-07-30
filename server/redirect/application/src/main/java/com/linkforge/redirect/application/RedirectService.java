package com.linkforge.redirect.application;

import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.analytics.ApplicationClickQuotaReservationPort;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.springframework.beans.factory.annotation.Autowired;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
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

    /**
     * 创建生产主流程所需的依赖。
     *
     * <p>当 quotaGuard 为 {@code null} 时采用显式关闭额度的兼容守卫；这仅供旧装配或测试使用，生产
     * 运行时应提供完整的 {@link RedirectQuotaGuard}。</p>
     *
     * @param linkCache 三态缓存端口，必须把基础设施读故障表达为 miss
     * @param shortLinkReadPort 短链权威读取端口
     * @param visitRecorderPort 真实跳转访问记录端口
     * @param clock UTC 时间来源
     * @param quotaGuard 应用额度守卫；仅兼容场景可为 {@code null}
     */
    @Autowired
    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock,
            RedirectQuotaGuard quotaGuard
    ) {
        this.linkCache = linkCache;
        this.shortLinkReadPort = shortLinkReadPort;
        this.visitRecorderPort = visitRecorderPort;
        this.clock = clock;
        this.quotaGuard = quotaGuard == null ? RedirectQuotaGuard.disabled(clock) : quotaGuard;
    }

    /**
     * 兼容旧调用面的构造器：从 Platform 与 Analytics 端口组合额度守卫。
     *
     * <p>若未提供 reservation port，会退回到已落库点击量查询，不能提供并发精确预留；新装配应传入
     * {@link ApplicationClickQuotaReservationPort}。</p>
     *
     * @param linkCache 三态缓存端口
     * @param shortLinkReadPort 短链权威读取端口
     * @param visitRecorderPort 真实跳转访问记录端口
     * @param clock UTC 时间来源
     * @param applicationScopePort Platform quota 查询端口
     * @param applicationClickUsagePort 已落库点击量查询端口
     * @param applicationClickQuotaReservationPort 原子额度预留端口，可为 {@code null}
     */
    public RedirectService(
            LinkCachePort linkCache,
            ShortLinkReadPort shortLinkReadPort,
            VisitRecorderPort visitRecorderPort,
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickUsagePort applicationClickUsagePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort
    ) {
        this(
                linkCache,
                shortLinkReadPort,
                visitRecorderPort,
                clock,
                RedirectQuotaGuard.from(
                        clock,
                        applicationScopePort,
                        applicationClickUsagePort,
                        applicationClickQuotaReservationPort
                )
        );
    }

    /**
     * 兼容仅有点击量查询端口的调用面。
     *
     * @param linkCache 三态缓存端口
     * @param shortLinkReadPort 短链权威读取端口
     * @param visitRecorderPort 真实跳转访问记录端口
     * @param clock UTC 时间来源
     * @param applicationScopePort Platform quota 查询端口
     * @param applicationClickUsagePort 已落库点击量查询端口
     */
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

    /**
     * 关闭应用额度的最小构造器，主要用于不具备 Platform/Analytics 依赖的测试或旧调用方。
     *
     * @param linkCache 三态缓存端口
     * @param shortLinkReadPort 短链权威读取端口
     * @param visitRecorderPort 真实跳转访问记录端口
     * @param clock UTC 时间来源
     */
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
                null,
                noClickUsagePort(),
                null
        );
    }

    /**
     * 以 legacy/unscoped 方式读取短链元数据，不写统计。
     *
     * <p>找不到或短码不合法时抛出 {@link RedirectBusinessException}，适用于需要明确读取结果的
     * 内部调用；HTTP 主流程应使用 {@link #resolve(ResolveRedirectRequest)} 获取可渲染的状态。</p>
     *
     * @param code 大小写敏感的短码
     * @return 权威读取或正缓存中的短链元数据
     * @throws RedirectBusinessException 短码非法或不存在
     */
    public LinkMeta resolve(String code) {
        return resolveMeta(null, code);
    }

    /**
     * 在给定 host 范围读取短链元数据，不写统计。
     *
     * <p>host 为空时沿用 legacy/unscoped 查询兼容路径；短码保持大小写，不能在此处转小写。</p>
     *
     * @param host 规范化 host，可为 {@code null}
     * @param code 大小写敏感的短码
     * @return 权威读取或正缓存中的短链元数据
     * @throws RedirectBusinessException 短码非法或在该 host 范围不可见
     */
    public LinkMeta resolve(String host, String code) {
        return resolveMeta(host, code);
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
     * 为兼容调用者在已解析元数据后补写访问记录。
     *
     * <p>仅当静态可用性与额度均通过时写入；该方法不会构造 HTTP 响应，也不会重新加载缓存。
     * 新的 HTTP 路径应优先使用 {@link #resolve(ResolveRedirectRequest)}，避免预览或重复调用
     * 造成计数语义不清。</p>
     *
     * @param meta 已加载的短链元数据
     * @param visitInput 已清洗的访问上下文，可为 {@code null}
     */
    public void recordVisitIfAvailable(LinkMeta meta, RedirectVisitInput visitInput) {
        if (isAvailable(meta)) {
            visitRecorderPort.recordVisit(toRedirectVisitRecord(meta, visitInput));
        }
    }

    /**
     * 读取 legacy/unscoped 短链后按 {@link #recordVisitIfAvailable(LinkMeta, RedirectVisitInput)} 的
     * 规则尝试记录访问。
     *
     * @param code 大小写敏感的短码
     * @param visitInput 已清洗的访问上下文，可为 {@code null}
     * @return 已解析的短链元数据
     */
    public LinkMeta resolveAndRecord(String code, RedirectVisitInput visitInput) {
        LinkMeta meta = resolveMeta(null, code);
        recordVisitIfAvailable(meta, visitInput);
        return meta;
    }

    /**
     * 在指定 host 范围读取短链后尝试记录访问。
     *
     * <p>这是旧调用面的便利方法；它不具备预览确认语义，不能替代 HTTP 主流程。</p>
     *
     * @param host 规范化 host，可为 {@code null}
     * @param code 大小写敏感的短码
     * @param visitInput 已清洗的访问上下文，可为 {@code null}
     * @return 已解析的短链元数据
     */
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
        // LinkCachePort 将 Redis 故障规范为 MISS；只有 NEGATIVE 才能终止权威回源。
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

        // 缓存不是事实来源：只有权威读确认不存在才允许写负缓存。
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
        return quotaGuard.unavailableReason(meta);
    }

    private static ApplicationClickUsagePort noClickUsagePort() {
        return (tenantId, applicationId, fromInclusiveUtc, toExclusiveUtc) -> 0L;
    }
}

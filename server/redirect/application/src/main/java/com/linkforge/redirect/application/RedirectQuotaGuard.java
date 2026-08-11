package com.linkforge.redirect.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.linkforge.contract.analytics.ApplicationClickQuotaReservationPort;
import com.linkforge.contract.analytics.ApplicationClickUsagePort;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 应用级月点击额度守卫。
 *
 * <p>只有携带有效 {@code applicationId} 的链接进入该守卫。额度定义按 UTC 月
 * {@code [monthStart, nextMonthStart)} 计算；小于等于零的 {@code monthlyClickLimit} 表示不限额。
 * 每次真实跳转通过 {@link ApplicationClickQuotaReservationPort} 原子预留一个名额，不能用普通查询
 * 加一替代。</p>
 *
 * <p>Platform 的 quota 视图以短 TTL 缓存在进程内，空结果同样可缓存；加载异常绝不缓存。这里的
 * {@code failOpenOnQuotaErrors} 只处理仍从 Platform 查询或 reservation port 抛出的异常，不能改变
 * Analytics Redis adapter 自身已定义的固定 fail-open 行为。</p>
 */
@Component
public class RedirectQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(RedirectQuotaGuard.class);
    private static final long DEFAULT_QUOTA_LOOKUP_CACHE_TTL_SECONDS = 30L;
    private static final long DEFAULT_QUOTA_LOOKUP_CACHE_MAX_ENTRIES = 10_000L;

    private final Clock clock;
    private final ApplicationScopePort applicationScopePort;
    private final ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort;
    private final boolean failOpenOnQuotaErrors;
    private final long quotaLookupCacheTtlMillis;
    private final Cache<QuotaCacheKey, Optional<ApplicationQuotaView>> quotaLookupCache;
    private final OperationalMetrics metrics;

    /**
     * 创建生产用额度守卫。
     *
     * @param failOpenOnQuotaErrors Platform 查询或端口调用抛异常时是否继续跳转
     * @param quotaLookupCacheTtlSeconds Platform quota 视图的本地缓存秒数，非正值关闭缓存
     * @param quotaLookupCacheMaxEntries 本地缓存最大应用数，至少为 1
     * @param clock UTC 时间来源
     * @param applicationScopePort Platform quota 查询端口
     * @param applicationClickQuotaReservationPort 原子月额度预留端口
     */
    public RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort,
            @Value("${app.analytics.quota.fail-open:false}") boolean failOpenOnQuotaErrors,
            @Value("${app.analytics.quota.lookup-cache-ttl-seconds:30}") long quotaLookupCacheTtlSeconds,
            @Value("${app.analytics.quota.lookup-cache-max-entries:10000}") long quotaLookupCacheMaxEntries
    ) {
        this(
                clock,
                applicationScopePort,
                applicationClickQuotaReservationPort,
                failOpenOnQuotaErrors,
                quotaLookupCacheTtlSeconds,
                quotaLookupCacheMaxEntries,
                OperationalMetrics.noop()
        );
    }

    @Autowired
    public RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort,
            @Value("${app.analytics.quota.fail-open:false}") boolean failOpenOnQuotaErrors,
            @Value("${app.analytics.quota.lookup-cache-ttl-seconds:30}") long quotaLookupCacheTtlSeconds,
            @Value("${app.analytics.quota.lookup-cache-max-entries:10000}") long quotaLookupCacheMaxEntries,
            OperationalMetrics metrics
    ) {
        this.clock = clock;
        this.applicationScopePort = applicationScopePort == null ? noQuotaApplicationScopePort() : applicationScopePort;
        this.applicationClickQuotaReservationPort = applicationClickQuotaReservationPort == null
                ? allowAllClickQuotaReservationPort()
                : applicationClickQuotaReservationPort;
        this.failOpenOnQuotaErrors = failOpenOnQuotaErrors;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
        this.quotaLookupCacheTtlMillis = toMillis(quotaLookupCacheTtlSeconds);
        long maximumSize = Math.max(1L, quotaLookupCacheMaxEntries);
        Duration ttl = Duration.ofMillis(Math.max(1L, this.quotaLookupCacheTtlMillis));
        this.quotaLookupCache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.instant().toEpochMilli()))
                .build();
    }

    RedirectQuotaGuard(
            Clock clock,
            ApplicationScopePort applicationScopePort,
            ApplicationClickQuotaReservationPort applicationClickQuotaReservationPort,
            boolean failOpenOnQuotaErrors,
            long quotaLookupCacheTtlSeconds
    ) {
        this(
                clock,
                applicationScopePort,
                applicationClickQuotaReservationPort,
                failOpenOnQuotaErrors,
                quotaLookupCacheTtlSeconds,
                DEFAULT_QUOTA_LOOKUP_CACHE_MAX_ENTRIES
        );
    }

    /**
     * 以默认 30 秒 quota 查询缓存创建守卫。
     *
     * @param clock UTC 时间来源
     * @param applicationScopePort Platform quota 查询端口
     * @param applicationClickQuotaReservationPort 原子月额度预留端口
     * @param failOpenOnQuotaErrors Platform/端口异常是否放行
     */
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
                DEFAULT_QUOTA_LOOKUP_CACHE_TTL_SECONDS,
                DEFAULT_QUOTA_LOOKUP_CACHE_MAX_ENTRIES
        );
    }

    /**
     * 以 fail-closed 和默认查询缓存创建守卫。
     *
     * @param clock UTC 时间来源
     * @param applicationScopePort Platform quota 查询端口
     * @param applicationClickQuotaReservationPort 原子月额度预留端口
     */
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

    /**
     * 为给定链接尝试预留本月点击额度。
     *
     * <p>返回 {@code null} 表示可继续跳转；返回 {@code QUOTA_EXCEEDED} 表示不得记录访问或写
     * Location。该方法只在调用方已经完成静态可用性和预览确认之后调用。</p>
     *
     * @param meta 已通过静态可用性检查的短链元数据
     * @return {@code null} 表示预留成功或无需限额，否则为不可用原因
     */
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
            return quotaFailureReason("lookup");
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
            return quotaFailureReason("reservation");
        }
        metrics.increment("linkforge.redirect.quota.reservations", "result", reserved ? "accepted" : "rejected");
        return reserved ? null : RedirectResolution.UnavailableReason.QUOTA_EXCEEDED;
    }

    private Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId) throws Exception {
        if (quotaLookupCacheTtlMillis <= 0) {
            metrics.increment("linkforge.redirect.quota.cache.lookups", "result", "disabled");
            return normalizeQuota(applicationScopePort.findApplicationQuota(tenantId, applicationId));
        }

        QuotaCacheKey key = new QuotaCacheKey(tenantId, applicationId);
        Optional<ApplicationQuotaView> cached = quotaLookupCache.getIfPresent(key);
        if (cached != null) {
            metrics.increment("linkforge.redirect.quota.cache.lookups", "result", "hit");
            return cached;
        }
        metrics.increment("linkforge.redirect.quota.cache.lookups", "result", "miss");

        AtomicReference<Optional<ApplicationQuotaView>> quotaRef = new AtomicReference<>();
        AtomicReference<Exception> failureRef = new AtomicReference<>();
        // compute 让同一个 application 的并发首读共用一次远端查询；异常不留下失败缓存。
        quotaLookupCache.asMap().compute(key, (ignored, existing) -> {
            if (existing != null) {
                quotaRef.set(existing);
                return existing;
            }
            try {
                Optional<ApplicationQuotaView> loaded =
                        normalizeQuota(applicationScopePort.findApplicationQuota(tenantId, applicationId));
                quotaRef.set(loaded);
                return loaded;
            } catch (Exception e) {
                failureRef.set(e);
                return null;
            }
        });
        if (failureRef.get() != null) {
            throw failureRef.get();
        }
        metrics.set("linkforge.redirect.quota.cache.size", quotaLookupCache.estimatedSize());
        return quotaRef.get();
    }

    long estimatedQuotaCacheSize() {
        quotaLookupCache.cleanUp();
        return quotaLookupCache.estimatedSize();
    }

    private RedirectResolution.UnavailableReason quotaFailureReason(String stage) {
        metrics.increment(
                "linkforge.redirect.quota.failures",
                "stage",
                stage,
                "decision",
                failOpenOnQuotaErrors ? "fail_open" : "fail_closed"
        );
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

}

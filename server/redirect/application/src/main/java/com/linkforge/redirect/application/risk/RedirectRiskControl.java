package com.linkforge.redirect.application.risk;

import com.linkforge.redirect.domain.net.CidrBlock;
import com.linkforge.redirect.domain.net.CidrBlocks;
import com.linkforge.redirect.domain.risk.UserAgentBotDetector;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Redirect Edge 的基础风控：IP 黑白名单、轻量 bot 识别和 Redis 固定窗口限流。
 *
 * <p>执行顺序固定为 denylist、非空 allowlist、bot 直接阻断、IP 限流、IP+code 限流。denylist 优先，
 * 因而同一地址同时命中两张表时仍被拒绝。此处只做请求前的防滥用/降级，不能替代 WAF、CDN 或网关。</p>
 *
 * <p>限流端口异常是否放行由 {@code app.edge.risk-control.rate-limit.fail-open} 决定；IP 解析可信边界
 * 不在本类，而由 {@code RedirectClientIpResolver} 在 filter 中完成。</p>
 */
@Component
public class RedirectRiskControl {

    private static final Logger log = LoggerFactory.getLogger(RedirectRiskControl.class);

    private static final List<String> DEFAULT_BOT_KEYWORDS = List.of(
            "bot", "crawler", "spider", "curl", "wget", "python-requests", "httpclient"
    );

    private final RateLimiterPort rateLimiter;

    private final boolean enabled;
    private final List<CidrBlock> ipAllowlist;
    private final List<CidrBlock> ipDenylist;

    private final boolean rateLimitEnabled;
    private final int windowSeconds;
    private final long ipMaxRequests;
    private final boolean ipCodeEnabled;
    private final long ipCodeMaxRequests;
    private final boolean failOpen;

    private final boolean botEnabled;
    private final List<String> botKeywords;
    private final long botIpMaxRequests;
    private final boolean botBlock;

    /**
     * 从 Edge 配置建立只读的风控策略快照。
     *
     * @param properties Edge 与 risk-control 配置；为空时风控关闭
     * @param rateLimiter 固定窗口计数端口；启用限流时必须可用
     */
    public RedirectRiskControl(EdgeProperties properties, RateLimiterPort rateLimiter) {
        this.rateLimiter = rateLimiter;

        EdgeProperties.RiskControl cfg = properties == null ? null : properties.getRiskControl();

        this.enabled = cfg != null && cfg.isEnabled();

        this.ipAllowlist = CidrBlocks.parseList(cfg == null ? null : cfg.getIpAllowlist(), "app.edge.risk-control.ip-allowlist");
        this.ipDenylist = CidrBlocks.parseList(cfg == null ? null : cfg.getIpDenylist(), "app.edge.risk-control.ip-denylist");

        EdgeProperties.RiskControl.RateLimit rl = cfg == null ? null : cfg.getRateLimit();
        this.rateLimitEnabled = rl != null && rl.isEnabled();
        this.windowSeconds = rl == null ? 60 : rl.getWindowSeconds();
        this.ipMaxRequests = rl == null ? 120 : rl.getIpMaxRequests();
        this.ipCodeEnabled = rl != null && rl.isIpCodeEnabled();
        this.ipCodeMaxRequests = rl == null ? 60 : rl.getIpCodeMaxRequests();
        this.failOpen = rl == null || rl.isFailOpen();

        EdgeProperties.RiskControl.BotPolicy bot = cfg == null ? null : cfg.getBot();
        this.botEnabled = bot != null && bot.isEnabled();
        List<String> rawKeywords = bot == null ? null : bot.getUserAgentKeywords();
        this.botKeywords = (rawKeywords == null || rawKeywords.isEmpty()) ? DEFAULT_BOT_KEYWORDS : rawKeywords;
        this.botIpMaxRequests = bot == null ? 30 : bot.getIpMaxRequests();
        this.botBlock = bot != null && bot.isBlock();
    }

    /**
     * 对已清洗的客户端上下文作出允许、403 或 429 决策。
     *
     * <p>code 只参与 IP+code 限流维度，不能通过本方法绕过 RedirectService 的短码校验。未启用风控时
     * 不访问限流器，直接放行。</p>
     *
     * @param clientIp 已按可信代理规则解析的客户端 IP；为空按 unknown 维度处理
     * @param userAgent 已限长的 User-Agent，可为 {@code null}
     * @param code 仅用于 IP+code 限流的原始短码
     * @return 允许、403 或 429 的完整决策
     */
    public Decision check(String clientIp, String userAgent, String code) {
        if (!enabled) {
            return Decision.allow();
        }

        String ip = normalizeIp(clientIp);

        // deny 优先，确保 allowlist 不能意外覆盖显式封禁。
        if (CidrBlocks.containsAny(ipDenylist, ip)) {
            return Decision.forbidden("ip_denylist", "IP 已被禁止访问");
        }
        if (!ipAllowlist.isEmpty() && !CidrBlocks.containsAny(ipAllowlist, ip)) {
            return Decision.forbidden("ip_not_in_allowlist", "IP 不在允许访问范围");
        }

        boolean isBot = botEnabled && UserAgentBotDetector.isBot(userAgent, botKeywords);
        if (isBot && botBlock) {
            return Decision.forbidden("bot_block", "请求被策略拒绝");
        }

        if (!rateLimitEnabled) {
            return Decision.allow();
        }

        long now = Instant.now().getEpochSecond();
        long window = Math.max(windowSeconds, 1);
        long windowId = now / window;
        int ttl = (int) Math.min(Integer.MAX_VALUE, window + 5);

        long ipLimit = isBot ? botIpMaxRequests : ipMaxRequests;
        if (ipLimit > 0) {
            String key = "rl:r:ip:" + keyPart(ip) + ":" + windowId;
            try {
                long c = rateLimiter.increment(key, ttl);
                if (c > ipLimit) {
                    return Decision.tooManyRequests("rate_ip", "请求过于频繁，请稍后再试", retryAfterSeconds(now, window, windowId));
                }
            } catch (Exception e) {
                return onRateLimiterError(e);
            }
        }

        if (ipCodeEnabled && ipCodeMaxRequests > 0 && code != null && !code.isBlank()) {
            String safeCode = normalizeCodeForKey(code);
            if (safeCode != null) {
                String key = "rl:r:ip_code:" + keyPart(ip) + ":" + safeCode + ":" + windowId;
                try {
                    long c = rateLimiter.increment(key, ttl);
                    if (c > ipCodeMaxRequests) {
                        return Decision.tooManyRequests("rate_ip_code", "请求过于频繁，请稍后再试", retryAfterSeconds(now, window, windowId));
                    }
                } catch (Exception e) {
                    return onRateLimiterError(e);
                }
            }
        }

        return Decision.allow();
    }

    private Decision onRateLimiterError(Exception e) {
        log.debug("rate limiter failed (failOpen={}): {}", failOpen, e.getMessage());
        if (failOpen) {
            return Decision.allow();
        }
        return Decision.tooManyRequests("rate_error", "服务繁忙，请稍后再试", 1L);
    }

    private static long retryAfterSeconds(long now, long windowSeconds, long windowId) {
        long next = (windowId + 1) * windowSeconds;
        long r = next - now;
        return r <= 0 ? 1L : r;
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return ip.trim();
    }

    private static String keyPart(String s) {
        if (s == null) {
            return "null";
        }
        // Redis key 允许 ':'，但 IPv6 包含 ':' 会显著增加 key 的可读复杂度，这里做轻度归一化
        return s.trim().replace(':', '_');
    }

    private static String normalizeCodeForKey(String code) {
        if (code == null) {
            return null;
        }
        String v = code.trim();
        if (v.isBlank()) {
            return null;
        }
        // 与 RedirectService 的短码输入边界保持一致，避免攻击输入扩张 Redis key。
        if (v.length() > 32) {
            return null;
        }
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

    /**
     * 风控检查的完整结果；{@code retryAfterSeconds} 仅在 429 时可能有值。
     */
    public record Decision(
            boolean allowed,
            int httpStatus,
            RedirectErrorCode errorCode,
            String message,
            Long retryAfterSeconds,
            String reasonCode
    ) {
        static Decision allow() {
            return new Decision(true, 200, null, null, null, null);
        }

        static Decision forbidden(String reasonCode, String message) {
            return new Decision(false, 403, RedirectErrorCode.FORBIDDEN, message, null, reasonCode);
        }

        static Decision tooManyRequests(String reasonCode, String message, Long retryAfterSeconds) {
            return new Decision(false, 429, RedirectErrorCode.TOO_MANY_REQUESTS, message, retryAfterSeconds, reasonCode);
        }
    }
}

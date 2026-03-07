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
 * Redirect Edge 的基础风控：IP 黑白名单、简单 bot 识别、Redis 限流。
 *
 * <p>注意：此处的限流与 bot 识别属于“防滥用/降级”能力，不替代 WAF / CDN / 网关的第一道防线。</p>
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

    public Decision check(String clientIp, String userAgent, String code) {
        if (!enabled) {
            return Decision.allow();
        }

        String ip = normalizeIp(clientIp);

        // deny 优先
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
            String safeCode = code.trim();
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

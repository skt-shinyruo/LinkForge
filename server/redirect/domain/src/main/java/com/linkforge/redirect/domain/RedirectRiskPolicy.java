package com.linkforge.redirect.domain;

import com.linkforge.redirect.domain.net.CidrBlock;
import com.linkforge.redirect.domain.net.CidrBlocks;
import com.linkforge.redirect.domain.risk.UserAgentBotDetector;

import java.util.List;

public class RedirectRiskPolicy {

    private final boolean enabled;
    private final List<CidrBlock> ipAllowlist;
    private final List<CidrBlock> ipDenylist;
    private final boolean botEnabled;
    private final List<String> botKeywords;
    private final boolean botBlock;

    public RedirectRiskPolicy(
            boolean enabled,
            List<CidrBlock> ipAllowlist,
            List<CidrBlock> ipDenylist,
            boolean botEnabled,
            List<String> botKeywords,
            boolean botBlock
    ) {
        this.enabled = enabled;
        this.ipAllowlist = ipAllowlist == null ? List.of() : List.copyOf(ipAllowlist);
        this.ipDenylist = ipDenylist == null ? List.of() : List.copyOf(ipDenylist);
        this.botEnabled = botEnabled;
        this.botKeywords = botKeywords == null ? List.of() : List.copyOf(botKeywords);
        this.botBlock = botBlock;
    }

    public RiskDecision evaluate(String clientIp, String userAgent) {
        if (!enabled) {
            return RiskDecision.allow(false);
        }
        String ip = normalizeIp(clientIp);
        if (CidrBlocks.containsAny(ipDenylist, ip)) {
            return RiskDecision.forbidden("ip_denylist", "IP 已被禁止访问", false);
        }
        if (!ipAllowlist.isEmpty() && !CidrBlocks.containsAny(ipAllowlist, ip)) {
            return RiskDecision.forbidden("ip_not_in_allowlist", "IP 不在允许访问范围", false);
        }

        boolean bot = botEnabled && UserAgentBotDetector.isBot(userAgent, botKeywords);
        if (bot && botBlock) {
            return RiskDecision.forbidden("bot_block", "请求被策略拒绝", true);
        }
        return RiskDecision.allow(bot);
    }

    public static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return ip.trim();
    }

    public static String normalizeCodeForRateKey(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        if (value.isBlank() || value.length() > 32) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z');
            if (!ok) {
                return null;
            }
        }
        return value;
    }

    public record RiskDecision(
            boolean allowed,
            String reasonCode,
            String message,
            boolean bot
    ) {

        public static RiskDecision allow(boolean bot) {
            return new RiskDecision(true, null, null, bot);
        }

        public static RiskDecision forbidden(String reasonCode, String message, boolean bot) {
            return new RiskDecision(false, reasonCode, message, bot);
        }
    }
}

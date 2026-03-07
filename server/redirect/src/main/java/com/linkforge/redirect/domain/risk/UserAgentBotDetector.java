package com.linkforge.redirect.domain.risk;

import java.util.List;

/**
 * 简单 UA 识别器：基于关键字包含匹配。
 *
 * <p>注意：此策略仅用于“降频/降级”，不应作为强安全边界。</p>
 */
public final class UserAgentBotDetector {

    private UserAgentBotDetector() {
    }

    public static boolean isBot(String userAgent, List<String> keywords) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        String ua = userAgent.toLowerCase();
        for (String k : keywords) {
            if (k == null) {
                continue;
            }
            String t = k.trim().toLowerCase();
            if (t.isBlank()) {
                continue;
            }
            if (ua.contains(t)) {
                return true;
            }
        }
        return false;
    }
}

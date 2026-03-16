package com.linkforge.redirect.domain.risk;

import java.util.List;
import java.util.Locale;

/**
 * 简单 UA 识别器：基于关键字包含匹配。
 *
 * <p>注意：此策略仅用于“降频/降级”，不应作为强安全边界。</p>
 */
public final class UserAgentBotDetector {

    private UserAgentBotDetector() {
    }

    private static final int MAX_UA_LEN = 512;

    public static boolean isBot(String userAgent, List<String> keywords) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        String raw = userAgent;
        if (raw.length() > MAX_UA_LEN) {
            raw = raw.substring(0, MAX_UA_LEN);
        }
        String ua = raw.toLowerCase(Locale.ROOT);
        for (String k : keywords) {
            if (k == null) {
                continue;
            }
            String t = k.trim().toLowerCase(Locale.ROOT);
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

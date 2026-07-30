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

    /**
     * 以大小写无关的关键字包含匹配识别 UA。
     *
     * <p>只检查有限长度的 UA，避免攻击者把超长 header 放大为每次请求的大字符串扫描；结果仅适合作为
     * 风控降级信号，不能作为身份或安全证明。</p>
     */
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

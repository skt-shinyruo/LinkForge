package com.linkforge.redirect.interfaces.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP 字符串处理工具：用于解析/清洗 forwarded headers 中的 IP。
 */
public final class IpStrings {

    private IpStrings() {
    }

    public static String cleanIpToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isBlank()) {
            return null;
        }
        if ("unknown".equalsIgnoreCase(t)) {
            return null;
        }

        // 去掉可能的引号
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).trim();
        }

        // IPv6 可能用 [] 包裹："[2001:db8::1]:1234"
        if (t.startsWith("[") && t.contains("]")) {
            int end = t.indexOf(']');
            String inside = t.substring(1, end).trim();
            return inside.isBlank() ? null : inside;
        }

        // 处理 IPv4:port（IPv6 不做该处理，避免误切分）
        int colon = t.lastIndexOf(':');
        if (colon > 0 && t.indexOf(':') == colon && t.contains(".")) {
            String maybeIp = t.substring(0, colon).trim();
            return maybeIp.isBlank() ? null : maybeIp;
        }

        return t;
    }

    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress.getByName(ip.trim());
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}

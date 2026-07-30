package com.linkforge.redirect.domain.net;

import java.util.Arrays;

/**
 * IP 字符串处理工具：用于解析/清洗 forwarded headers 中的 IP。
 */
public final class IpStrings {

    private IpStrings() {
    }

    /**
     * 清理 remote 地址或 forwarded header 中的单个 token。
     *
     * <p>支持带引号、IPv4 端口和方括号 IPv6 形式，但不把值本身视为可信；调用方仍须通过
     * {@link #isValidIp(String)} 和可信代理链规则判断能否使用。</p>
     */
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

    /**
     * 按纯字面 IPv4/IPv6 规则验证地址，不做 DNS 解析。
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return parseIpLiteralToBytes(ip.trim()) != null;
    }

    static byte[] parseIpLiteralToBytes(String ip) {
        if (ip == null) {
            return null;
        }
        String t = ip.trim();
        if (t.isBlank()) {
            return null;
        }
        if (t.indexOf(':') >= 0) {
            return parseIpv6Literal(t);
        }
        if (t.indexOf('.') >= 0) {
            return parseIpv4Literal(t);
        }
        return null;
    }

    private static byte[] parseIpv4Literal(String ip) {
        // Strict dotted-decimal only: a.b.c.d where each part is 0-255.
        byte[] out = new byte[4];
        int outIndex = 0;
        int partValue = 0;
        int partLen = 0;
        boolean leadingZero = false;

        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if (c == '.') {
                if (partLen == 0 || outIndex >= 4) {
                    return null;
                }
                if (leadingZero && partLen > 1) {
                    return null;
                }
                out[outIndex++] = (byte) partValue;
                partValue = 0;
                partLen = 0;
                leadingZero = false;
                continue;
            }
            if (c < '0' || c > '9') {
                return null;
            }
            if (partLen == 0 && c == '0') {
                leadingZero = true;
            }
            partValue = (partValue * 10) + (c - '0');
            if (partValue > 255) {
                return null;
            }
            partLen++;
            if (partLen > 3) {
                return null;
            }
        }

        if (partLen == 0 || outIndex != 3) {
            return null;
        }
        if (leadingZero && partLen > 1) {
            return null;
        }
        out[outIndex] = (byte) partValue;
        return out;
    }

    private static byte[] parseIpv6Literal(String ip) {
        // Strip optional zone id: "fe80::1%eth0" -> "fe80::1"
        int percent = ip.indexOf('%');
        String t = percent >= 0 ? ip.substring(0, percent) : ip;
        if (t.isBlank()) {
            return null;
        }

        // Must contain at least one ':' for IPv6.
        if (t.indexOf(':') < 0) {
            return null;
        }

        byte[] out = new byte[16];
        int outIndex = 0;
        int compressIndex = -1;

        int i = 0;
        int length = t.length();

        if (t.startsWith("::")) {
            compressIndex = 0;
            i = 2;
            if (i == length) {
                return out; // "::"
            }
        } else if (t.startsWith(":")) {
            return null; // leading single ':'
        }

        while (i < length) {
            if (outIndex == 16) {
                return null;
            }

            int segmentStart = i;
            int segmentValue = 0;
            int segmentLen = 0;

            while (i < length) {
                int digit = hexValue(t.charAt(i));
                if (digit < 0) {
                    break;
                }
                segmentValue = (segmentValue << 4) | digit;
                segmentLen++;
                if (segmentLen > 4) {
                    return null;
                }
                i++;
            }

            if (segmentLen == 0) {
                return null;
            }

            if (i < length && t.charAt(i) == '.') {
                // IPv4-mapped/embedded IPv6 tail.
                byte[] v4 = parseIpv4Literal(t.substring(segmentStart));
                if (v4 == null || outIndex > 12) {
                    return null;
                }
                System.arraycopy(v4, 0, out, outIndex, 4);
                outIndex += 4;
                i = length;
                break;
            }

            out[outIndex++] = (byte) ((segmentValue >>> 8) & 0xFF);
            out[outIndex++] = (byte) (segmentValue & 0xFF);

            if (i == length) {
                break;
            }

            if (t.charAt(i) != ':') {
                return null;
            }
            i++;
            if (i == length) {
                return null; // trailing single ':'
            }
            if (i < length && t.charAt(i) == ':') {
                if (compressIndex != -1) {
                    return null;
                }
                compressIndex = outIndex;
                i++;
                if (i == length) {
                    break; // ends with "::"
                }
            }
        }

        if (outIndex < 16) {
            if (compressIndex == -1) {
                return null;
            }
            int moved = outIndex - compressIndex;
            int zeros = 16 - outIndex;
            System.arraycopy(out, compressIndex, out, compressIndex + zeros, moved);
            Arrays.fill(out, compressIndex, compressIndex + zeros, (byte) 0);
            outIndex = 16;
        } else if (compressIndex != -1) {
            // "::" must expand at least one 16-bit group
            return null;
        }

        return out;
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return 10 + (c - 'a');
        }
        if (c >= 'A' && c <= 'F') {
            return 10 + (c - 'A');
        }
        return -1;
    }
}

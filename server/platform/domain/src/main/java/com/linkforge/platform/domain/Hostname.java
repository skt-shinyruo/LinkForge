package com.linkforge.platform.domain;

import java.net.IDN;
import java.util.Locale;

/**
 * 平台登记域名使用的规范化 DNS 主机名值对象。
 *
 * <p>构造时会去除首尾空白、按 {@link Locale#ROOT} 转为小写，并使用 STD3 规则将国际化域名
 * 转为 ASCII（Punycode）。因此 {@link #value()} 始终返回可稳定比较和持久化的 ASCII 形式，而不是
 * 调用方传入的原始文本。</p>
 *
 * <p>该值对象只接受至少包含两个 label 的域名，并拒绝 URL、端口、路径、用户信息、通配符、
 * {@code localhost}、IPv4 字面量、尾随点以及超出 DNS 长度限制的输入。它不执行 DNS 解析，也不证明
 * 域名所有权或可达性；这些属于平台控制面的后续校验职责。</p>
 *
 * @param value 待规范化的主机名；为 {@code null}、空白或不符合上述规则时抛出
 *              {@link IllegalArgumentException}
 */
public record Hostname(String value) {

    public Hostname {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hostname 不能为空");
        }
        value = normalize(value);
    }

    /**
     * 解析并规范化外部主机名输入。
     *
     * @param raw 外部输入，不得包含协议、端口或路径
     * @return 规范化后的主机名值对象
     * @throws IllegalArgumentException 输入为空或不是允许登记的 DNS 主机名
     */
    public static Hostname parse(String raw) {
        return new Hostname(raw);
    }

    private static String normalize(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank() || hasInvalidHostnameCharacters(value)) {
            throw invalid();
        }
        String ascii;
        try {
            ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
        if (ascii.length() > 253
                || ascii.startsWith(".")
                || ascii.endsWith(".")
                || ascii.contains("..")
                || "localhost".equals(ascii)
                || ascii.endsWith(".localhost")
                || looksLikeIpv4Address(ascii)) {
            throw invalid();
        }
        String[] labels = ascii.split("\\.");
        if (labels.length < 2) {
            throw invalid();
        }
        for (String label : labels) {
            if (!isValidHostnameLabel(label)) {
                throw invalid();
            }
        }
        return ascii;
    }

    private static boolean hasInvalidHostnameCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '/' || ch == '\\' || ch == '@' || ch == '*') {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeIpv4Address(String value) {
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static boolean isValidHostnameLabel(String label) {
        if (label.isBlank() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("hostname 不合法");
    }
}

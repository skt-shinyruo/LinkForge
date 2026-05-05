package com.linkforge.platform.domain;

import java.net.IDN;
import java.util.Locale;

public record Hostname(String value) {

    public Hostname {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hostname 不能为空");
        }
        value = normalize(value);
    }

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

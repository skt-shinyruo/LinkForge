package com.linkforge.analytics.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class VisitNormalizationPolicy {

    public VisitDimension normalize(
            String ip,
            String userAgent,
            String referer,
            String acceptLanguage,
            Map<String, String> trackingParams
    ) {
        return new VisitDimension(
                cleanInline(ip),
                cleanInline(userAgent),
                cleanInline(referer),
                cleanInline(acceptLanguage),
                normalizeTrackingParams(trackingParams)
        );
    }

    private static Map<String, String> normalizeTrackingParams(Map<String, String> trackingParams) {
        if (trackingParams == null || trackingParams.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        trackingParams.forEach((key, value) -> {
            String k = cleanInline(key);
            String v = cleanInline(value);
            if (k != null && v != null) {
                normalized.put(k, v);
            }
        });
        return Map.copyOf(normalized);
    }

    private static String cleanInline(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch < 0x20) {
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        String trimmed = sb.toString().trim().replaceAll(" +", " ");
        return trimmed.isBlank() ? null : trimmed;
    }
}

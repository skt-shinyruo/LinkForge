package com.linkforge.analytics.domain;

import java.util.Map;

public record VisitDimension(
        String ip,
        String userAgent,
        String referer,
        String acceptLanguage,
        Map<String, String> trackingParams
) {

    public VisitDimension {
        trackingParams = trackingParams == null ? Map.of() : Map.copyOf(trackingParams);
    }

    public static VisitDimension empty() {
        return new VisitDimension(null, null, null, null, Map.of());
    }
}

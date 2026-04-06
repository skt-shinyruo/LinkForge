package com.linkforge.redirect.application;

import java.util.Map;

public record RedirectVisitInput(
        String ip,
        String userAgent,
        String referer,
        String acceptLanguage,
        Map<String, String> trackingParams
) {
}

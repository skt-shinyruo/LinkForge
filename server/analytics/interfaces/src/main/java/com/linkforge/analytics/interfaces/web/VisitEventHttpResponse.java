package com.linkforge.analytics.interfaces.web;

import java.time.LocalDateTime;

public record VisitEventHttpResponse(
        LocalDateTime occurredAt,
        String requestId,
        String ipHash,
        String userAgentRaw,
        String userAgentFamily,
        String osFamily,
        String deviceType,
        String refererDomain,
        String language,
        String utmSource,
        String utmMedium,
        String utmCampaign
) {
}

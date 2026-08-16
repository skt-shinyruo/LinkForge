package com.linkforge.shortlink.interfaces.web;

import com.linkforge.shortlink.application.CreateLinkRequest;
import com.linkforge.shortlink.application.UpdateLinkRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkUpdateHttpRequest;

public final class ShortLinkHttpMapper {

    private ShortLinkHttpMapper() {
    }

    public static CreateLinkRequest toCreateRequest(ShortLinkCreateHttpRequest req) {
        return new CreateLinkRequest(
                req.originalUrl(),
                req.note(),
                req.expiresAt(),
                req.enabled(),
                req.customCode(),
                req.tags(),
                req.redirectStatusCode(),
                req.previewEnabled(),
                req.unavailableLandingUrl(),
                req.queryForwardMode(),
                req.queryForwardAllowlist(),
                req.applicationId(),
                req.domainId(),
                req.lifecycleState()
        );
    }

    public static UpdateLinkRequest toUpdateRequest(ShortLinkUpdateHttpRequest req) {
        return new UpdateLinkRequest(
                req.originalUrl(),
                req.note(),
                req.expiresAt(),
                req.clearExpiresAt(),
                req.enabled(),
                req.tags(),
                req.redirectStatusCode(),
                req.clearRedirectStatusCode(),
                req.previewEnabled(),
                req.unavailableLandingUrl(),
                req.queryForwardMode(),
                req.clearQueryForwardMode(),
                req.queryForwardAllowlist(),
                req.lifecycleState()
        );
    }

}

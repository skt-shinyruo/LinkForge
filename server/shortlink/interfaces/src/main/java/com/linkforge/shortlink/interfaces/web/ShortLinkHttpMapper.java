package com.linkforge.shortlink.interfaces.web;

import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkPageHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkUpdateHttpRequest;

public final class ShortLinkHttpMapper {

    private ShortLinkHttpMapper() {
    }

    public static ShortLinkService.CreateLinkRequest toCreateRequest(ShortLinkCreateHttpRequest req) {
        return new ShortLinkService.CreateLinkRequest(
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
                req.queryForwardAllowlist()
        );
    }

    public static ShortLinkService.UpdateLinkRequest toUpdateRequest(ShortLinkUpdateHttpRequest req) {
        return new ShortLinkService.UpdateLinkRequest(
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
                req.queryForwardAllowlist()
        );
    }

    public static <T> ShortLinkPageHttpResponse<T> toPageResponse(PageResult<T> result) {
        return new ShortLinkPageHttpResponse<>(result.items(), result.total(), result.page(), result.size());
    }
}

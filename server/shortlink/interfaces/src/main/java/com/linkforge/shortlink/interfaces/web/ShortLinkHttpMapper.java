package com.linkforge.shortlink.interfaces.web;

import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.CreateLinkRequest;
import com.linkforge.shortlink.application.ImportResult;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.TagDto;
import com.linkforge.shortlink.application.UpdateLinkRequest;
import com.linkforge.shortlink.interfaces.web.dto.ImportHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkPageHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkUpdateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.TagHttpResponse;

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

    public static ShortLinkHttpResponse toLinkResponse(LinkDto dto) {
        return new ShortLinkHttpResponse(
                dto.id(),
                dto.tenantId(),
                dto.applicationId(),
                dto.domainId(),
                dto.lifecycleState(),
                dto.code(),
                dto.shortUrl(),
                dto.originalUrl(),
                dto.note(),
                dto.enabled(),
                dto.expiresAt(),
                dto.archivedAt(),
                dto.redirectStatusCode(),
                dto.previewEnabled(),
                dto.unavailableLandingUrl(),
                dto.queryForwardMode(),
                dto.queryForwardAllowlist(),
                dto.tags(),
                dto.createdAt(),
                dto.pendingApproval(),
                dto.approvalRequestId(),
                dto.requestedOriginalUrl()
        );
    }

    public static ShortLinkPageHttpResponse<ShortLinkHttpResponse> toPageResponse(PageResult<LinkDto> result) {
        return new ShortLinkPageHttpResponse<>(
                result.items().stream().map(ShortLinkHttpMapper::toLinkResponse).toList(),
                result.total(),
                result.page(),
                result.size()
        );
    }

    public static ImportHttpResponse toImportResponse(ImportResult result) {
        return new ImportHttpResponse(result.success(), result.failed(), result.errors());
    }

    public static TagHttpResponse toTagResponse(TagDto dto) {
        return new TagHttpResponse(dto.id(), dto.name());
    }
}

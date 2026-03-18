package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.application.ShortLinkService.CreatedBy;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkPageHttpResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open/links")
public class OpenApiShortLinkController {

    private final ShortLinkService shortLinkService;
    private final ShortLinkWriteGuard writeGuard;

    public OpenApiShortLinkController(ShortLinkService shortLinkService, ShortLinkWriteGuard writeGuard) {
        this.shortLinkService = shortLinkService;
        this.writeGuard = writeGuard;
    }

    @PostMapping
    public ApiResponse<LinkDto> create(@Valid @RequestBody ShortLinkCreateHttpRequest req) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        Long apiKeyId = p.getApiKeyId();
        if (apiKeyId == null || apiKeyId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        LinkDto dto = shortLinkService.create(
                p.getTenantId(),
                CreatedBy.apiKey(apiKeyId),
                ShortLinkHttpMapper.toCreateRequest(req)
        );
        return ApiResponse.ok(dto, RequestId.get());
    }

    @GetMapping
    public ApiResponse<ShortLinkPageHttpResponse<LinkDto>> list(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        PageResult<LinkDto> result = shortLinkService.search(
                p.getTenantId(),
                new ShortLinkSearchQuery(false, enabled, keyword, null),
                PageQuery.of(page, size, 100)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }
}

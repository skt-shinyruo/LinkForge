package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.platform.application.PlatformControlPlaneService;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.application.ShortLinkService.CreatedBy;
import com.linkforge.shortlink.application.ShortLinkService.CreateLinkRequest;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkPageHttpResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open")
public class OpenApiShortLinkController {

    private final ShortLinkService shortLinkService;
    private final ShortLinkWriteGuard writeGuard;
    private final PlatformControlPlaneService platformControlPlaneService;

    public OpenApiShortLinkController(
            ShortLinkService shortLinkService,
            ShortLinkWriteGuard writeGuard,
            PlatformControlPlaneService platformControlPlaneService
    ) {
        this.shortLinkService = shortLinkService;
        this.writeGuard = writeGuard;
        this.platformControlPlaneService = platformControlPlaneService;
    }

    @PostMapping("/links")
    public ApiResponse<LinkDto> create(@Valid @RequestBody ShortLinkCreateHttpRequest req) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        long apiKeyId = requireApiKeyId(p);
        Long applicationId = resolveAuthorizedApplicationId(p, req.applicationId(), null);
        CreateLinkRequest createRequest = withApplicationId(ShortLinkHttpMapper.toCreateRequest(req), applicationId);
        LinkDto dto = shortLinkService.create(
                p.getTenantId(),
                CreatedBy.apiKey(apiKeyId),
                createRequest
        );
        return ApiResponse.ok(dto, RequestId.get());
    }

    @PostMapping("/applications/{applicationId}/links")
    public ApiResponse<LinkDto> createForApplication(
            @PathVariable("applicationId") long applicationId,
            @Valid @RequestBody ShortLinkCreateHttpRequest req
    ) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        long apiKeyId = requireApiKeyId(p);
        long effectiveApplicationId = resolveAuthorizedApplicationId(p, req.applicationId(), applicationId);
        platformControlPlaneService.requireApplicationExists(p.getTenantId(), effectiveApplicationId);
        CreateLinkRequest createRequest = withApplicationId(ShortLinkHttpMapper.toCreateRequest(req), effectiveApplicationId);
        LinkDto dto = shortLinkService.create(
                p.getTenantId(),
                CreatedBy.apiKey(apiKeyId),
                createRequest
        );
        return ApiResponse.ok(dto, RequestId.get());
    }

    @GetMapping("/links")
    public ApiResponse<ShortLinkPageHttpResponse<LinkDto>> list(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        PageResult<LinkDto> result = shortLinkService.search(
                p.getTenantId(),
                new ShortLinkSearchQuery(false, enabled, keyword, null, p.getApplicationId()),
                PageQuery.of(page, size, 100)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }

    @GetMapping("/applications/{applicationId}/links")
    public ApiResponse<ShortLinkPageHttpResponse<LinkDto>> listByApplication(
            @PathVariable("applicationId") long applicationId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        long effectiveApplicationId = resolveAuthorizedApplicationId(p, null, applicationId);
        platformControlPlaneService.requireApplicationExists(p.getTenantId(), effectiveApplicationId);
        PageResult<LinkDto> result = shortLinkService.search(
                p.getTenantId(),
                new ShortLinkSearchQuery(false, enabled, keyword, null, effectiveApplicationId),
                PageQuery.of(page, size, 100)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }

    private static long requireApiKeyId(AuthPrincipal principal) {
        Long apiKeyId = principal.getApiKeyId();
        if (apiKeyId == null || apiKeyId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return apiKeyId;
    }

    private static long resolveAuthorizedApplicationId(AuthPrincipal principal, Long requestApplicationId, Long pathApplicationId) {
        Long principalApplicationId = principal.getApplicationId();
        Long requestedApplicationId = pathApplicationId != null ? pathApplicationId : requestApplicationId;
        if (principalApplicationId != null) {
            if (requestedApplicationId != null && !principalApplicationId.equals(requestedApplicationId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 无权访问该应用");
            }
            return principalApplicationId;
        }
        if (requestedApplicationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationId 不能为空");
        }
        return requestedApplicationId;
    }

    private static CreateLinkRequest withApplicationId(CreateLinkRequest createRequest, long applicationId) {
        return new CreateLinkRequest(
                createRequest.originalUrl(),
                createRequest.note(),
                createRequest.expiresAt(),
                createRequest.enabled(),
                createRequest.customCode(),
                createRequest.tags(),
                createRequest.redirectStatusCode(),
                createRequest.previewEnabled(),
                createRequest.unavailableLandingUrl(),
                createRequest.queryForwardMode(),
                createRequest.queryForwardAllowlist(),
                applicationId,
                createRequest.domainId(),
                createRequest.lifecycleState()
        );
    }
}

package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.runtime.security.PrincipalActorMapper;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.shortlink.application.BrowseLinksRequest;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.ScopedCreateLinkRequest;
import com.linkforge.shortlink.application.ShortLinkApplicationService;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkHttpResponse;
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

    private final ShortLinkApplicationService shortLinkService;
    private final ShortLinkWriteGuard writeGuard;
    private final PrincipalActorMapper principalActorMapper;

    public OpenApiShortLinkController(
            ShortLinkApplicationService shortLinkService,
            ShortLinkWriteGuard writeGuard,
            PrincipalActorMapper principalActorMapper
    ) {
        this.shortLinkService = shortLinkService;
        this.writeGuard = writeGuard;
        this.principalActorMapper = principalActorMapper;
    }

    @PostMapping("/links")
    public ApiResponse<ShortLinkHttpResponse> create(@Valid @RequestBody ShortLinkCreateHttpRequest req) {
        writeGuard.requireWriteEnabled();
        ApiKeyActor actor = principalActorMapper.requireApiKey(AuthContext.requirePrincipal());
        LinkDto dto = shortLinkService.createForApiKey(
                actor,
                new ScopedCreateLinkRequest(ShortLinkHttpMapper.toCreateRequest(req), null)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toLinkResponse(dto), RequestId.get());
    }

    @PostMapping("/applications/{applicationId}/links")
    public ApiResponse<ShortLinkHttpResponse> createForApplication(
            @PathVariable("applicationId") long applicationId,
            @Valid @RequestBody ShortLinkCreateHttpRequest req
    ) {
        writeGuard.requireWriteEnabled();
        ApiKeyActor actor = principalActorMapper.requireApiKey(AuthContext.requirePrincipal());
        LinkDto dto = shortLinkService.createForApiKey(
                actor,
                new ScopedCreateLinkRequest(ShortLinkHttpMapper.toCreateRequest(req), applicationId)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toLinkResponse(dto), RequestId.get());
    }

    @GetMapping("/links")
    public ApiResponse<ShortLinkPageHttpResponse<ShortLinkHttpResponse>> list(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "true") boolean includeTotal
    ) {
        ApiKeyActor actor = principalActorMapper.requireApiKey(AuthContext.requirePrincipal());
        PageResult<LinkDto> result = shortLinkService.browseForApiKey(
                actor,
                new BrowseLinksRequest(
                        false, enabled, keyword, null, null, null, page, size, 100, cursor, includeTotal
                )
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }

    @GetMapping("/applications/{applicationId}/links")
    public ApiResponse<ShortLinkPageHttpResponse<ShortLinkHttpResponse>> listByApplication(
            @PathVariable("applicationId") long applicationId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "true") boolean includeTotal
    ) {
        ApiKeyActor actor = principalActorMapper.requireApiKey(AuthContext.requirePrincipal());
        PageResult<LinkDto> result = shortLinkService.browseForApiKey(
                actor,
                new BrowseLinksRequest(
                        false, enabled, keyword, null, null, applicationId, page, size, 100, cursor, includeTotal
                )
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }
}

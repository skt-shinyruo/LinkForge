package com.linkforge.platform.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.platform.application.CreateApplicationCommand;
import com.linkforge.platform.application.PlatformControlPlaneService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
public class TenantAdminApplicationController {

    private final PlatformControlPlaneService platformControlPlaneService;

    public TenantAdminApplicationController(PlatformControlPlaneService platformControlPlaneService) {
        this.platformControlPlaneService = platformControlPlaneService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<ApplicationHttpResponse>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(
                platformControlPlaneService.listApplications(tenantId).stream()
                        .map(PlatformHttpMapper::toApplicationResponse)
                        .toList(),
                RequestId.get()
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ApplicationHttpResponse> create(@Valid @RequestBody CreateApplicationRequest req) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        return ApiResponse.ok(
                PlatformHttpMapper.toApplicationResponse(platformControlPlaneService.createApplication(
                        principal.getTenantId(),
                        actor,
                        new CreateApplicationCommand(req.applicationKey(), req.displayName())
                )),
                RequestId.get()
        );
    }

    @PostMapping("/{applicationId}/domain-authorizations/{domainId}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> authorizeTenantDomain(
            @PathVariable("applicationId") long applicationId,
            @PathVariable("domainId") long domainId
    ) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        platformControlPlaneService.authorizeTenantDomainForApplicationUse(principal.getTenantId(), actor, applicationId, domainId);
        return ApiResponse.ok(null, RequestId.get());
    }

    public record CreateApplicationRequest(
            @NotBlank(message = "applicationKey 不能为空")
            @Size(max = 128, message = "applicationKey 过长")
            String applicationKey,
            @NotBlank(message = "displayName 不能为空")
            @Size(max = 128, message = "displayName 过长")
            String displayName
    ) {
    }
}

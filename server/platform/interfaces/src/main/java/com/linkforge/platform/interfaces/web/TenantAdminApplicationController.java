package com.linkforge.platform.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.security.AuthContext;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.platform.application.ApplicationProvisioningService;
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
    public ApiResponse<List<ApplicationProvisioningService.ApplicationDto>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(platformControlPlaneService.listApplications(tenantId), RequestId.get());
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ApplicationProvisioningService.ApplicationDto> create(@Valid @RequestBody CreateApplicationRequest req) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(
                platformControlPlaneService.createApplication(
                        tenantId,
                        new ApplicationProvisioningService.CreateApplicationRequest(req.applicationKey(), req.displayName())
                ),
                RequestId.get()
        );
    }

    @PostMapping("/{applicationId}/domain-authorizations/{domainId}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> authorizeTenantDomain(
            @PathVariable("applicationId") long applicationId,
            @PathVariable("domainId") long domainId
    ) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        platformControlPlaneService.authorizeTenantDomainForApplicationUse(tenantId, applicationId, domainId);
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

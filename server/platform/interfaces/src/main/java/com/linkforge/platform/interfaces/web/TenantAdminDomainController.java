package com.linkforge.platform.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
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
@RequestMapping("/api/v1")
public class TenantAdminDomainController {

    private final PlatformControlPlaneService platformControlPlaneService;

    public TenantAdminDomainController(PlatformControlPlaneService platformControlPlaneService) {
        this.platformControlPlaneService = platformControlPlaneService;
    }

    @GetMapping("/domains")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<DomainHttpResponse>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(
                platformControlPlaneService.listDomains(tenantId).stream()
                        .map(PlatformHttpMapper::toDomainResponse)
                        .toList(),
                RequestId.get()
        );
    }

    @GetMapping("/applications/{applicationId}/domains")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<DomainHttpResponse>> listByApplication(
            @PathVariable("applicationId") long applicationId
    ) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(
                platformControlPlaneService.listDomainsForApplication(tenantId, applicationId).stream()
                        .map(PlatformHttpMapper::toDomainResponse)
                        .toList(),
                RequestId.get()
        );
    }

    @PostMapping("/domains/tenant-shared")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<DomainHttpResponse> createTenantSharedDomain(
            @Valid @RequestBody CreateDomainRequest req
    ) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        return ApiResponse.ok(
                PlatformHttpMapper.toDomainResponse(
                        platformControlPlaneService.createTenantSharedDomain(principal.getTenantId(), actor, req.hostname())
                ),
                RequestId.get()
        );
    }

    @PostMapping("/applications/{applicationId}/domains")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<DomainHttpResponse> createApplicationDedicatedDomain(
            @PathVariable("applicationId") long applicationId,
            @Valid @RequestBody CreateDomainRequest req
    ) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        return ApiResponse.ok(
                PlatformHttpMapper.toDomainResponse(
                        platformControlPlaneService.createApplicationDedicatedDomain(principal.getTenantId(), actor, applicationId, req.hostname())
                ),
                RequestId.get()
        );
    }

    public record CreateDomainRequest(
            @NotBlank(message = "hostname 不能为空")
            @Size(max = 255, message = "hostname 过长")
            String hostname
    ) {
    }
}

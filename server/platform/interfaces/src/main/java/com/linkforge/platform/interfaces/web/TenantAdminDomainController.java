package com.linkforge.platform.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.platform.application.DomainResult;
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

/**
 * 租户管理员的域名控制面入口。
 *
 * <p>所有查询和写入都以认证主体中的 tenantId 为边界；路径中的 applicationId
 * 只标识目标资源，不能扩大主体的租户范围。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class TenantAdminDomainController {

    private final PlatformControlPlaneService platformControlPlaneService;

    public TenantAdminDomainController(PlatformControlPlaneService platformControlPlaneService) {
        this.platformControlPlaneService = platformControlPlaneService;
    }

    /** 返回当前租户可管理的全部域名。 */
    @GetMapping("/domains")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<DomainResult>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(platformControlPlaneService.listDomains(tenantId), RequestId.get());
    }

    /**
     * 返回指定应用可使用的域名，包括应用专属域名和已授权的租户共享域名。
     */
    @GetMapping("/applications/{applicationId}/domains")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<DomainResult>> listByApplication(
            @PathVariable("applicationId") long applicationId
    ) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(platformControlPlaneService.listDomainsForApplication(tenantId, applicationId), RequestId.get());
    }

    /** 创建可由同租户多个应用显式授权使用的共享域名。 */
    @PostMapping("/domains/tenant-shared")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<DomainResult> createTenantSharedDomain(
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
                platformControlPlaneService.createTenantSharedDomain(principal.getTenantId(), actor, req.hostname()),
                RequestId.get()
        );
    }

    /** 创建仅供指定应用使用的专属域名。 */
    @PostMapping("/applications/{applicationId}/domains")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<DomainResult> createApplicationDedicatedDomain(
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
                platformControlPlaneService.createApplicationDedicatedDomain(principal.getTenantId(), actor, applicationId, req.hostname()),
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

package com.linkforge.platform.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.platform.application.CreateApplicationCommand;
import com.linkforge.platform.application.ApplicationResult;
import com.linkforge.platform.application.PlatformControlPlaneService;
import com.linkforge.platform.domain.PlatformDefaults;
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
 * 租户管理员的应用控制面入口。
 *
 * <p>租户标识仅从已认证主体取得，不接受请求参数中的租户标识。应用层仍会校验
 * actor 与租户一致性以及资源归属，避免仅依赖路由层授权。</p>
 */
@RestController
@RequestMapping("/api/v1/applications")
public class TenantAdminApplicationController {

    private final PlatformControlPlaneService platformControlPlaneService;

    public TenantAdminApplicationController(PlatformControlPlaneService platformControlPlaneService) {
        this.platformControlPlaneService = platformControlPlaneService;
    }

    /** 返回当前认证主体所属租户的应用。 */
    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<ApplicationResult>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(platformControlPlaneService.listApplications(tenantId), RequestId.get());
    }

    /**
     * 为当前租户创建应用；长度约束与数据库列宽及应用层校验保持一致。
     */
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ApplicationResult> create(@Valid @RequestBody CreateApplicationRequest req) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        return ApiResponse.ok(
                platformControlPlaneService.createApplication(
                        principal.getTenantId(),
                        actor,
                        new CreateApplicationCommand(req.applicationKey(), req.displayName())
                ),
                RequestId.get()
        );
    }

    /**
     * 授权应用使用一个租户共享域名。
     *
     * <p>该操作不是创建域名，也不改变专属域名归属；应用层会验证应用、域名均属于
     * 当前租户，且域名作用域允许被应用共享。</p>
     */
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
            @Size(max = PlatformDefaults.APPLICATION_KEY_MAX_LENGTH, message = "applicationKey 过长")
            String applicationKey,
            @NotBlank(message = "displayName 不能为空")
            @Size(max = PlatformDefaults.APPLICATION_DISPLAY_NAME_MAX_LENGTH, message = "displayName 过长")
            String displayName
    ) {
    }
}

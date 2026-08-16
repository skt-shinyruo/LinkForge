package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.CreateUserCommand;
import com.linkforge.accounts.application.UserAdminService;
import com.linkforge.accounts.application.UserResult;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.web.RequestId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 租户管理员维护租户内用户的 HTTP 边界。
 *
 * <p>所有端点要求已认证 JWT 主体和 {@code TENANT_ADMIN} 角色。操作者 tenantId 只来自
 * {@link AuthContext}，目标用户 id 不能改变租户作用域；应用服务负责再次校验目标归属、角色集合和
 * 状态迁移。停用操作还传递操作者 userId，以便应用层执行禁止停用自身等业务不变量。</p>
 *
 * <p>请求和响应均不返回密码哈希；重置密码只把明文交给应用层完成单向哈希和 token version 失效处理。</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(
            UserAdminService userAdminService
    ) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<UserResult>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(userAdminService.list(tenantId), RequestId.get());
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserResult> create(@Valid @RequestBody CreateUserRequest req) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        UserResult out = userAdminService.create(
                tenantId,
                new CreateUserCommand(req.email(), req.password(), req.roles())
        );
        return ApiResponse.ok(out, RequestId.get());
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserResult> disable(@PathVariable("id") long id) {
        var principal = AuthContext.requirePrincipal();
        return ApiResponse.ok(
                userAdminService.disable(principal.getTenantId(), principal.getUserId(), id),
                RequestId.get()
        );
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserResult> enable(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(userAdminService.enable(tenantId, id), RequestId.get());
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserResult> resetPassword(@PathVariable("id") long id, @Valid @RequestBody ResetPasswordRequest req) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(
                userAdminService.resetPassword(tenantId, id, req.password()),
                RequestId.get()
        );
    }

    public record CreateUserRequest(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 256, message = "邮箱过长")
            String email,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64")
            String password,
            Set<String> roles
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64")
            String password
    ) {
    }
}

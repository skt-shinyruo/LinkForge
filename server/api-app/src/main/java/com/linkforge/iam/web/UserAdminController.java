package com.linkforge.iam.web;

import com.linkforge.platform.api.ApiResponse;
import com.linkforge.platform.security.AuthContext;
import com.linkforge.platform.web.RequestId;
import com.linkforge.iam.service.UserAdminService;
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
    public ApiResponse<List<UserAdminService.UserDto>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(userAdminService.list(tenantId), RequestId.get());
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserAdminService.UserDto> create(@Valid @RequestBody CreateUserRequest req) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        UserAdminService.UserDto out = userAdminService.create(
                tenantId,
                new UserAdminService.CreateUserRequest(req.email(), req.password(), req.roles())
        );
        return ApiResponse.ok(out, RequestId.get());
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserAdminService.UserDto> disable(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(userAdminService.disable(tenantId, id), RequestId.get());
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserAdminService.UserDto> enable(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(userAdminService.enable(tenantId, id), RequestId.get());
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<UserAdminService.UserDto> resetPassword(@PathVariable("id") long id, @Valid @RequestBody ResetPasswordRequest req) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(userAdminService.resetPassword(tenantId, id, req.password()), RequestId.get());
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

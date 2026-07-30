package com.linkforge.accounts.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 返回当前管理端认证主体的只读视图。
 *
 * <p>用户、租户和角色均来自安全过滤链写入的 {@link AuthPrincipal}，不接受请求参数覆盖；过滤链已校验
 * JWT、token version 以及用户和租户状态。这里再次通过 {@link AuthContext} 要求已认证主体，避免把
 * 匿名或非 LinkForge 主体误当作业务身份。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(new MeResponse(p.getUserId(), p.getTenantId(), p.getEmail(), p.getRoles()), RequestId.get());
    }

    public record MeResponse(long userId, long tenantId, String email, java.util.Set<String> roles) {
    }
}

package com.linkforge.accounts.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

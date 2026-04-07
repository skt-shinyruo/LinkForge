package com.linkforge.foundation.runtime.security;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.runtime.security.AuthContext;
import org.springframework.stereotype.Component;

@Component
public class TenantGuard {

    public void requireCurrentTenant(long tenantId) {
        long currentTenantId = AuthContext.requirePrincipal().getTenantId();
        if (tenantId != currentTenantId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "跨租户访问被拒绝");
        }
    }
}

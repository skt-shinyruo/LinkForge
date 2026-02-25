package com.linkforge.api.security;

import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 多租户护栏：确保服务层使用的 tenantId 与当前认证主体一致，避免误把 client 入参 tenantId 透传导致越权。
 */
@Component
public class TenantGuard {

    public void requireCurrentTenant(long tenantId) {
        long currentTenantId = AuthContext.requirePrincipal().getTenantId();
        if (tenantId != currentTenantId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "跨租户访问被拒绝");
        }
    }
}


package com.linkforge.foundation.runtime.security;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.runtime.security.AuthContext;
import org.springframework.stereotype.Component;

/**
 * HTTP 适配层的显式租户一致性检查。
 *
 * <p>它比较路径/请求中的 tenantId 与经过认证的当前主体 tenantId，不执行资源归属或角色授权；调用方仍必须
 * 根据具体资源补充所有权和角色判断。拒绝时使用 {@code FORBIDDEN}，避免把跨租户请求误报告为认证缺失。</p>
 */
@Component
public class TenantGuard {

    /** @throws BusinessException 当前主体缺失或目标租户不属于当前主体时抛出 */
    public void requireCurrentTenant(long tenantId) {
        long currentTenantId = AuthContext.requirePrincipal().getTenantId();
        if (tenantId != currentTenantId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "跨租户访问被拒绝");
        }
    }
}

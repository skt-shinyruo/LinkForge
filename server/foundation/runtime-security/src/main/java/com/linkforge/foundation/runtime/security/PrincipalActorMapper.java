package com.linkforge.foundation.runtime.security;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.ApiKeyAuthenticationDetails;
import com.linkforge.foundation.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 将已认证的 Spring Security 主体转换为应用层 actor。
 *
 * <p>用户 actor 只从 {@link AuthPrincipal} 的租户、用户和角色快照构造。API Key actor 额外要求当前
 * {@link Authentication} 已认证、其 principal 与传入对象为同一实例，并从 authentication details 读取
 * API Key ID 与可选 application scope；这样业务层不会把 JWT 用户主体误当作 OpenAPI 主体。</p>
 */
@Component
public class PrincipalActorMapper {

    /**
     * 构造用户 actor。
     *
     * @throws BusinessException principal 为空时抛出未认证错误
     */
    public UserActor requireUser(AuthPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
    }

    /**
     * 构造 API Key actor 并验证其只来自当前已认证上下文。
     *
     * <p>{@code applicationId} 为 {@code null} 表示该 Key 未绑定应用，具体资源范围由调用方的授权策略继续
     * 判定；本方法不把 {@code null} 自动扩展为任意应用。</p>
     *
     * @throws BusinessException 认证上下文、主体身份或 API Key details 缺失/非法时抛出未认证错误
     */
    public ApiKeyActor requireApiKey(AuthPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() != principal) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Object details = auth.getDetails();
        if (!(details instanceof ApiKeyAuthenticationDetails apiKeyDetails) || apiKeyDetails.apiKeyId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new ApiKeyActor(principal.getTenantId(), apiKeyDetails.apiKeyId(), apiKeyDetails.applicationId());
    }
}

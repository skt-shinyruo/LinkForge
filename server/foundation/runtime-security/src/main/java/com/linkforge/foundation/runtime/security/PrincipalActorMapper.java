package com.linkforge.foundation.runtime.security;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.AuthPrincipal;
import org.springframework.stereotype.Component;

@Component
public class PrincipalActorMapper {

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

    public ApiKeyActor requireApiKey(AuthPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long apiKeyId = principal.getApiKeyId();
        if (apiKeyId == null || apiKeyId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new ApiKeyActor(principal.getTenantId(), apiKeyId, principal.getApplicationId());
    }
}

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth == null ? null : auth.getDetails();
        if (!(details instanceof ApiKeyAuthenticationDetails apiKeyDetails) || apiKeyDetails.apiKeyId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new ApiKeyActor(principal.getTenantId(), apiKeyDetails.apiKeyId(), apiKeyDetails.applicationId());
    }
}

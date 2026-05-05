package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.UserResult;

final class AccountsHttpMapper {

    private AccountsHttpMapper() {
    }

    static UserHttpResponse toUserResponse(UserResult result) {
        return new UserHttpResponse(
                result.id(),
                result.tenantId(),
                result.email(),
                result.status(),
                result.roles()
        );
    }
}

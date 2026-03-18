package com.linkforge.accounts.application.port;

import java.util.Set;

public interface AccountsTokenIssuer {

    String issueToken(long userId, long tenantId, String email, Set<String> roles);
}

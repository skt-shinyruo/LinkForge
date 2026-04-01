package com.linkforge.accounts.domain;

import com.linkforge.foundation.security.StandardRoles;

@Deprecated(forRemoval = false)
public final class Roles {

    public static final String PLATFORM_ADMIN = StandardRoles.PLATFORM_ADMIN;
    public static final String TENANT_ADMIN = StandardRoles.TENANT_ADMIN;
    public static final String USER = StandardRoles.USER;
    public static final String OPENAPI = StandardRoles.OPENAPI;

    private Roles() {
    }
}

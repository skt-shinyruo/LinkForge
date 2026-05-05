package com.linkforge.platform.domain;

public final class PlatformDefaults {

    public static final String APPLICATION_STATUS_ACTIVE = "ACTIVE";
    public static final long MONTHLY_LINK_LIMIT = 10_000L;
    public static final long MONTHLY_CLICK_LIMIT = 1_000_000L;
    public static final int REDIRECT_STATUS_CODE = 302;
    public static final boolean PREVIEW_ENABLED = false;
    public static final DomainScope DEFAULT_DOMAIN_SCOPE = DomainScope.TENANT_SHARED;

    private PlatformDefaults() {
    }
}

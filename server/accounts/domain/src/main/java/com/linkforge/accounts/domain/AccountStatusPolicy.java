package com.linkforge.accounts.domain;

public class AccountStatusPolicy {

    public boolean canAuthenticate(Tenant tenant, AccountUser user) {
        return tenant != null && user != null && tenant.active() && user.active();
    }

    public void requireActive(Tenant tenant, AccountUser user) {
        if (!canAuthenticate(tenant, user)) {
            throw new IllegalStateException("account is not active");
        }
    }
}

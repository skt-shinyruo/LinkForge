package com.linkforge.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountStatusPolicyTest {

    private final AccountStatusPolicy policy = new AccountStatusPolicy();

    @Test
    void canAuthenticate_shouldAllowActiveTenantAndActiveUser() {
        Tenant tenant = Tenant.rehydrate(10L, TenantName.of("Tenant A"), AccountsConstants.STATUS_ACTIVE);
        AccountUser user = AccountUser.rehydrate(
                20L,
                10L,
                EmailAddress.of("user@example.com"),
                AccountsConstants.STATUS_ACTIVE,
                TokenVersion.initial()
        );

        assertThat(policy.canAuthenticate(tenant, user)).isTrue();
    }

    @Test
    void canAuthenticate_shouldRejectDisabledTenant() {
        Tenant tenant = Tenant.rehydrate(10L, TenantName.of("Tenant A"), AccountsConstants.STATUS_DISABLED);
        AccountUser user = AccountUser.rehydrate(
                20L,
                10L,
                EmailAddress.of("user@example.com"),
                AccountsConstants.STATUS_ACTIVE,
                TokenVersion.initial()
        );

        assertThat(policy.canAuthenticate(tenant, user)).isFalse();
    }

    @Test
    void canAuthenticate_shouldRejectNullInputs() {
        Tenant tenant = Tenant.rehydrate(10L, TenantName.of("Tenant A"), AccountsConstants.STATUS_ACTIVE);
        AccountUser user = AccountUser.rehydrate(
                20L,
                10L,
                EmailAddress.of("user@example.com"),
                AccountsConstants.STATUS_ACTIVE,
                TokenVersion.initial()
        );

        assertThat(policy.canAuthenticate(null, user)).isFalse();
        assertThat(policy.canAuthenticate(tenant, null)).isFalse();
    }

    @Test
    void requireActive_shouldThrowWhenDisabled() {
        Tenant tenant = Tenant.rehydrate(10L, TenantName.of("Tenant A"), AccountsConstants.STATUS_ACTIVE);
        AccountUser user = AccountUser.rehydrate(
                20L,
                10L,
                EmailAddress.of("user@example.com"),
                AccountsConstants.STATUS_DISABLED,
                TokenVersion.initial()
        );

        assertThatThrownBy(() -> policy.requireActive(tenant, user))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tenantName_shouldTrimValue() {
        assertThat(TenantName.of("  Tenant A  ").value()).isEqualTo("Tenant A");
    }

    @Test
    void rehydrate_shouldPreserveTenantStatusExactly() {
        Tenant tenant = Tenant.rehydrate(10L, TenantName.of("Tenant A"), " " + AccountsConstants.STATUS_ACTIVE + " ");

        assertThat(tenant.status()).isEqualTo(" " + AccountsConstants.STATUS_ACTIVE + " ");
    }
}

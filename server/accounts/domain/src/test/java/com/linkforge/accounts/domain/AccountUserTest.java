package com.linkforge.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountUserTest {

    @Test
    void logout_shouldIncrementTokenVersion() {
        AccountUser user = activeUser(TokenVersion.of(2));

        AccountUser loggedOut = user.logout();

        assertThat(loggedOut.tokenVersion().value()).isEqualTo(3);
        assertThat(loggedOut.id()).isEqualTo(user.id());
        assertThat(loggedOut.tenantId()).isEqualTo(user.tenantId());
        assertThat(loggedOut.email()).isEqualTo(user.email());
        assertThat(loggedOut.status()).isEqualTo(user.status());
    }

    @Test
    void enableAndDisable_shouldReturnExpectedActiveState() {
        AccountUser user = activeUser(TokenVersion.initial());

        AccountUser disabled = user.disable();
        AccountUser enabled = disabled.enable();

        assertThat(disabled.disabled()).isTrue();
        assertThat(disabled.active()).isFalse();
        assertThat(enabled.active()).isTrue();
    }

    @Test
    void rehydrate_shouldRejectInvalidIds() {
        assertThatThrownBy(() -> AccountUser.rehydrate(
                0L,
                10L,
                EmailAddress.of("user@example.com"),
                AccountsConstants.STATUS_ACTIVE,
                TokenVersion.initial()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrate_shouldPreserveStatusExactly() {
        AccountUser user = AccountUser.rehydrate(
                20L,
                10L,
                EmailAddress.of("user@example.com"),
                " " + AccountsConstants.STATUS_ACTIVE + " ",
                TokenVersion.initial()
        );

        assertThat(user.status()).isEqualTo(" " + AccountsConstants.STATUS_ACTIVE + " ");
    }

    private static AccountUser activeUser(TokenVersion tokenVersion) {
        return AccountUser.rehydrate(
                20L,
                10L,
                EmailAddress.of("user@example.com"),
                AccountsConstants.STATUS_ACTIVE,
                tokenVersion
        );
    }
}

package com.linkforge.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyScopePolicyTest {

    private final ApiKeyScopePolicy policy = new ApiKeyScopePolicy();

    @Test
    void requireApplicationBound_shouldAcceptValidApiKey() {
        policy.requireApplicationBound(activeApiKey());
    }

    @Test
    void requireApplicationBound_shouldRejectNullApiKey() {
        assertThatThrownBy(() -> policy.requireApplicationBound(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireApplicationBound_shouldRejectUnboundApiKey() {
        ApiKey apiKey = ApiKey.rehydrate(
                30L,
                10L,
                null,
                ApiKeyName.of("Legacy Key"),
                AccountsConstants.STATUS_ACTIVE
        );

        assertThatThrownBy(() -> policy.requireApplicationBound(apiKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void apiKey_shouldActivateAndRevoke() {
        ApiKey revoked = activeApiKey().revoke();
        ApiKey activated = revoked.activate();

        assertThat(revoked.active()).isFalse();
        assertThat(activated.active()).isTrue();
    }

    @Test
    void apiKeyName_shouldTrimValue() {
        assertThat(ApiKeyName.of("  Production Key  ").value()).isEqualTo("Production Key");
    }

    @Test
    void rehydrate_shouldPreserveStatusExactly() {
        ApiKey apiKey = ApiKey.rehydrate(
                30L,
                10L,
                40L,
                ApiKeyName.of("Production Key"),
                " " + AccountsConstants.STATUS_ACTIVE + " "
        );

        assertThat(apiKey.status()).isEqualTo(" " + AccountsConstants.STATUS_ACTIVE + " ");
    }

    private static ApiKey activeApiKey() {
        return ApiKey.rehydrate(
                30L,
                10L,
                40L,
                ApiKeyName.of("Production Key"),
                AccountsConstants.STATUS_ACTIVE
        );
    }
}

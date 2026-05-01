package com.linkforge.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenVersionTest {

    @Test
    void of_shouldTreatNullAsInitial() {
        assertThat(TokenVersion.of(null).value()).isZero();
    }

    @Test
    void of_shouldRejectNegativeValue() {
        assertThatThrownBy(() -> TokenVersion.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenVersion");
    }

    @Test
    void incremented_shouldReturnNextVersion() {
        assertThat(TokenVersion.of(3).incremented().value()).isEqualTo(4);
    }
}

package com.linkforge.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void of_shouldNormalizeEmail() {
        assertThat(EmailAddress.of("  MEMBER@Example.COM ").value())
                .isEqualTo("member@example.com");
    }

    @Test
    void constructor_shouldNormalizeEmail() {
        assertThat(new EmailAddress("  MEMBER@Example.COM ").value())
                .isEqualTo("member@example.com");
    }

    @Test
    void of_shouldRejectBlankEmail() {
        assertThatThrownBy(() -> EmailAddress.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void of_shouldRejectEmailWithoutAtSign() {
        assertThatThrownBy(() -> EmailAddress.of("member.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }
}

package com.linkforge.platform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainHostnameTest {

    @Test
    void of_shouldNormalizeHostname() {
        assertThat(DomainHostname.of(" EXAMPLE.COM ").value()).isEqualTo("example.com");
    }

    @Test
    void of_shouldRejectBlankHostname() {
        assertThatThrownBy(() -> DomainHostname.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostname");
    }
}

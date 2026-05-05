package com.linkforge.platform.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostnameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "example.com:443",
            "example.com/path",
            "exa mple.com",
            "bad_host.example.com",
            "*.example.com",
            "localhost",
            "127.0.0.1",
            "::1",
            "example.com."
    })
    void parse_shouldRejectInvalidHostnames(String hostname) {
        assertThatThrownBy(() -> Hostname.parse(hostname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostname");
    }

    @ParameterizedTest
    @ValueSource(strings = {" Go.Example.COM ", "go.example.com"})
    void parse_shouldNormalizeHostname(String hostname) {
        assertThat(Hostname.parse(hostname).value()).isEqualTo("go.example.com");
    }
}

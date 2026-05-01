package com.linkforge.redirect.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectLookupKeyTest {

    @Test
    void tryCreate_shouldNormalizeHostAndPreserveShortCodeCase() {
        RedirectLookupKey key = RedirectLookupKey.tryCreate(" Go.Example.COM ", " AbC123 ").orElseThrow();

        assertThat(key.host()).isEqualTo("go.example.com");
        assertThat(key.code()).isEqualTo("AbC123");
    }

    @Test
    void tryCreate_shouldRejectUnsafeShortCode() {
        assertThat(RedirectLookupKey.tryCreate("go.example.com", "abc/123")).isEmpty();
    }
}

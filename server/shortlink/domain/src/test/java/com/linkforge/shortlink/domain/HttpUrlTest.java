package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpUrlTest {

    @Test
    void shouldAcceptHttpAndHttpsUrls() {
        HttpUrl a = HttpUrl.of("https://example.com/path?q=1");
        HttpUrl b = HttpUrl.of("http://example.com");

        assertThat(a.value()).isEqualTo("https://example.com/path?q=1");
        assertThat(b.value()).isEqualTo("http://example.com");
    }

    @Test
    void shouldRejectBlank() {
        assertThatThrownBy(() -> HttpUrl.of("   "))
                .isInstanceOf(ShortLinkDomainException.class)
                .satisfies(ex -> assertThat(((ShortLinkDomainException) ex).reason())
                        .isEqualTo(ShortLinkDomainException.Reason.INVALID_URL));
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThatThrownBy(() -> HttpUrl.of("ftp://example.com/a"))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("http/https");
    }

    @Test
    void shouldRejectMissingHost() {
        assertThatThrownBy(() -> HttpUrl.of("https:///a"))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("host");
    }
}


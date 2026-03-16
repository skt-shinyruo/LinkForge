package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryParamPatternTest {

    @Test
    void shouldAllowUtmWildcardPattern() {
        QueryParamPattern p = QueryParamPattern.of("utm_*");
        assertThat(p.value()).isEqualTo("utm_*");
    }

    @Test
    void shouldRejectStarOnly() {
        assertThatThrownBy(() -> QueryParamPattern.of("*"))
                .isInstanceOf(ShortLinkDomainException.class);
    }

    @Test
    void shouldRejectStarInMiddle() {
        assertThatThrownBy(() -> QueryParamPattern.of("utm_*_x"))
                .isInstanceOf(ShortLinkDomainException.class);
    }

    @Test
    void shouldRejectInvalidCharacter() {
        assertThatThrownBy(() -> QueryParamPattern.of("utm-source"))
                .isInstanceOf(ShortLinkDomainException.class);
    }
}


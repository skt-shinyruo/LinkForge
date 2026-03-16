package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeTest {

    @Test
    void shouldAcceptValidCode_andPreserveCase() {
        ShortCode code = ShortCode.of(" Abcdef ");
        assertThat(code.value()).isEqualTo("Abcdef");
    }

    @Test
    void shouldRejectTooShort() {
        assertThatThrownBy(() -> ShortCode.of("abc"))
                .isInstanceOf(ShortLinkDomainException.class)
                .satisfies(ex -> assertThat(((ShortLinkDomainException) ex).reason())
                        .isEqualTo(ShortLinkDomainException.Reason.INVALID_CODE));
    }

    @Test
    void shouldRejectInvalidCharacters() {
        assertThatThrownBy(() -> ShortCode.of("abc-def"))
                .isInstanceOf(ShortLinkDomainException.class)
                .satisfies(ex -> assertThat(((ShortLinkDomainException) ex).reason())
                        .isEqualTo(ShortLinkDomainException.Reason.INVALID_CODE));
    }
}


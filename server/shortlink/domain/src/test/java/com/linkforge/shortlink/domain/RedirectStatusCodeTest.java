package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedirectStatusCodeTest {

    @Test
    void of_shouldAllow301And302() {
        assertThat(RedirectStatusCode.of(301).value()).isEqualTo(301);
        assertThat(RedirectStatusCode.of(302).value()).isEqualTo(302);
    }

    @Test
    void of_shouldRejectUnsupportedStatus() {
        assertThatThrownBy(() -> RedirectStatusCode.of(307))
                .isInstanceOf(ShortLinkDomainException.class)
                .extracting(ex -> ((ShortLinkDomainException) ex).reason())
                .isEqualTo(ShortLinkDomainException.Reason.INVALID_REDIRECT_STATUS_CODE);
    }
}

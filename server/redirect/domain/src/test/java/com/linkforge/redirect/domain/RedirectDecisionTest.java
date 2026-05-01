package com.linkforge.redirect.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectDecisionTest {

    @Test
    void factories_shouldExpressRedirectOutcomesInDomainLanguage() {
        assertThat(RedirectDecision.redirect().kind()).isEqualTo(RedirectDecision.Kind.REDIRECT);
        assertThat(RedirectDecision.preview().kind()).isEqualTo(RedirectDecision.Kind.PREVIEW);
        assertThat(RedirectDecision.notFound().kind()).isEqualTo(RedirectDecision.Kind.NOT_FOUND);
        assertThat(RedirectDecision.unavailable(RedirectDecision.UnavailableReason.EXPIRED).reason())
                .isEqualTo(RedirectDecision.UnavailableReason.EXPIRED);
        assertThat(RedirectDecision.blocked("bot_block").blockReason()).isEqualTo("bot_block");
    }
}

package com.linkforge.redirect.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectAvailabilityPolicyTest {

    private final RedirectAvailabilityPolicy policy = new RedirectAvailabilityPolicy();

    @Test
    void evaluate_shouldRejectDisabledOrExpiredLinks() {
        LocalDateTime now = LocalDateTime.parse("2026-04-30T12:00:00");

        assertThat(policy.evaluate(false, true, null, now, false).reason())
                .isEqualTo(RedirectDecision.UnavailableReason.DISABLED);
        assertThat(policy.evaluate(true, true, now.minusSeconds(1), now, false).reason())
                .isEqualTo(RedirectDecision.UnavailableReason.EXPIRED);
    }

    @Test
    void evaluate_shouldAllowActiveUnexpiredLink() {
        assertThat(policy.evaluate(
                true,
                true,
                LocalDateTime.parse("2026-05-01T00:00:00"),
                LocalDateTime.parse("2026-04-30T12:00:00"),
                false
        ).kind()).isEqualTo(RedirectDecision.Kind.REDIRECT);
    }
}

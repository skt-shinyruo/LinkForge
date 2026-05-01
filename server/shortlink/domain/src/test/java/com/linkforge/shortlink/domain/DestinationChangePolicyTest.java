package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationChangePolicyTest {

    private final DestinationChangePolicy policy = new DestinationChangePolicy();

    @Test
    void decide_shouldRequireApprovalForActiveApplicationAwareDestinationChange() {
        DestinationChangePolicy.Decision decision = policy.decide(
                true,
                ShortLinkLifecycleState.ACTIVE,
                "https://example.com/old",
                "https://example.com/new"
        );

        assertThat(decision).isEqualTo(DestinationChangePolicy.Decision.REQUIRES_APPROVAL);
    }

    @Test
    void decide_shouldAllowNoopAndLegacyChangesDirectly() {
        assertThat(policy.decide(true, ShortLinkLifecycleState.ACTIVE, "https://a", "https://a"))
                .isEqualTo(DestinationChangePolicy.Decision.DIRECT);
        assertThat(policy.decide(false, ShortLinkLifecycleState.ACTIVE, "https://a", "https://b"))
                .isEqualTo(DestinationChangePolicy.Decision.DIRECT);
    }
}

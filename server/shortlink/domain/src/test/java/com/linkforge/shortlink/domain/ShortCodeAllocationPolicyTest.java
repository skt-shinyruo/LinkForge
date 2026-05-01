package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeAllocationPolicyTest {

    @Test
    void collisionDecision_shouldFailCustomCodesAndRetryGeneratedCodes() {
        ShortCodeAllocationPolicy policy = new ShortCodeAllocationPolicy();

        assertThat(policy.onCollision(true)).isEqualTo(ShortCodeAllocationPolicy.CollisionDecision.FAIL);
        assertThat(policy.onCollision(false)).isEqualTo(ShortCodeAllocationPolicy.CollisionDecision.RETRY_OR_SURFACE_INFRASTRUCTURE_ERROR);
    }
}

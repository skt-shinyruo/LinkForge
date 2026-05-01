package com.linkforge.platform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationQuotaTest {

    @Test
    void create_shouldAllowUnlimitedMonthlyLinksWithZeroLimit() {
        ApplicationQuota quota = ApplicationQuota.create(10L, MonthlyLinkLimit.unlimited(), MonthlyLinkLimit.of(100L));

        assertThat(quota.monthlyLinkLimit()).isZero();
        assertThat(quota.monthlyClickLimit()).isEqualTo(100L);
    }

    @Test
    void create_shouldRejectNegativeMonthlyLimit() {
        assertThatThrownBy(() -> MonthlyLinkLimit.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monthlyLinkLimit");
    }
}

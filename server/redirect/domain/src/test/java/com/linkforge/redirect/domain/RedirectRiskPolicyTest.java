package com.linkforge.redirect.domain;

import com.linkforge.redirect.domain.net.CidrBlocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectRiskPolicyTest {

    @Test
    void evaluate_shouldApplyDenylistBeforeAllowlist() {
        RedirectRiskPolicy policy = new RedirectRiskPolicy(
                true,
                CidrBlocks.parseList(List.of("10.0.0.0/8"), "allowlist"),
                CidrBlocks.parseList(List.of("10.0.0.5/32"), "denylist"),
                true,
                List.of("bot"),
                false
        );

        RedirectRiskPolicy.RiskDecision decision = policy.evaluate("10.0.0.5", "Mozilla/5.0");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ip_denylist");
    }

    @Test
    void normalizeCodeForRateKey_shouldRejectUnsafeCodes() {
        assertThat(RedirectRiskPolicy.normalizeCodeForRateKey(" abc123 ")).isEqualTo("abc123");
        assertThat(RedirectRiskPolicy.normalizeCodeForRateKey("abc/123")).isNull();
    }
}

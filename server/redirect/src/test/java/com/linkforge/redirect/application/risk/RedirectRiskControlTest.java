package com.linkforge.redirect.application.risk;

import com.linkforge.foundation.config.EdgeProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectRiskControlTest {

    @Test
    void ip_code_rate_limit_should_ignore_invalid_code_to_avoid_redis_key_abuse() {
        EdgeProperties props = new EdgeProperties();
        EdgeProperties.RiskControl rc = props.getRiskControl();
        rc.setEnabled(true);

        EdgeProperties.RiskControl.RateLimit rl = rc.getRateLimit();
        rl.setEnabled(true);
        rl.setIpMaxRequests(0);
        rl.setIpCodeEnabled(true);
        rl.setIpCodeMaxRequests(10);

        RecordingRateLimiter rateLimiter = new RecordingRateLimiter();
        RedirectRiskControl riskControl = new RedirectRiskControl(props, rateLimiter);

        riskControl.check("1.2.3.4", "ua", "abc/def");

        assertThat(rateLimiter.keys).isEmpty();
    }

    private static class RecordingRateLimiter implements RateLimiterPort {
        private final List<String> keys = new ArrayList<>();

        @Override
        public long increment(String key, int ttlSeconds) {
            keys.add(key);
            return 1L;
        }
    }
}


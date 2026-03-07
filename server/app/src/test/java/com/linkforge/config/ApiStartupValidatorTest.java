package com.linkforge.config;

import com.linkforge.app.startup.AppStartupValidator;
import com.linkforge.foundation.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiStartupValidatorTest {

    @Test
    void strictConfig_should_fail_on_dev_secrets() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.strict-config", "true");

        AppProperties props = new AppProperties();
        props.setBaseUrl("http://localhost");
        props.getSecurity().getJwt().setSecret("dev-change-me-please-set-env-and-long-enough-32-bytes");
        props.getAnalytics().setSalt("dev-salt-change-me");
        props.getAnalytics().setRedisKeyTtlDays(1);

        AppStartupValidator v = new AppStartupValidator(env, props);
        assertThatThrownBy(() -> v.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");
    }

    @Test
    void cors_allowCredentials_should_require_whitelist() {
        MockEnvironment env = new MockEnvironment();

        AppProperties props = new AppProperties();
        props.setBaseUrl("http://localhost");
        props.getSecurity().getJwt().setSecret("test-secret-please-change-but-long-enough-32-bytes");
        props.getAnalytics().setSalt("test-analytics-salt");
        props.getAnalytics().setRedisKeyTtlDays(1);

        props.getCors().setAllowCredentials(true);
        props.getCors().setAllowedOrigins(java.util.List.of());

        AppStartupValidator v = new AppStartupValidator(env, props);
        assertThatThrownBy(() -> v.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }
}

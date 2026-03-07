package com.linkforge.config;

import com.linkforge.app.startup.AppStartupValidator;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiStartupValidatorTest {

    @Test
    void strictConfig_should_fail_on_dev_secrets() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.strict-config", "true");

        CoreProperties core = new CoreProperties();
        core.setBaseUrl("http://localhost");

        IdProperties id = new IdProperties();
        id.setWorkerId(2);
        id.setDatacenterId(2);

        SecurityProperties security = new SecurityProperties();
        security.getJwt().setSecret("dev-change-me-please-set-env-and-long-enough-32-bytes");

        CorsProperties cors = new CorsProperties();

        RedirectProperties redirect = new RedirectProperties();
        redirect.setDefaultStatusCode(302);
        redirect.setCacheTtlSeconds(60);
        redirect.setNotFoundCacheTtlSeconds(60);

        AnalyticsProperties analytics = new AnalyticsProperties();
        analytics.setSalt("dev-salt-change-me");
        analytics.setRedisKeyTtlDays(1);

        EdgeProperties edge = new EdgeProperties();

        AppStartupValidator v = new AppStartupValidator(env, core, id, security, cors, redirect, analytics, edge);
        assertThatThrownBy(() -> v.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");
    }

    @Test
    void cors_allowCredentials_should_require_whitelist() {
        MockEnvironment env = new MockEnvironment();

        CoreProperties core = new CoreProperties();
        core.setBaseUrl("http://localhost");

        IdProperties id = new IdProperties();
        id.setWorkerId(2);
        id.setDatacenterId(2);

        SecurityProperties security = new SecurityProperties();
        security.getJwt().setSecret("test-secret-please-change-but-long-enough-32-bytes");

        RedirectProperties redirect = new RedirectProperties();
        redirect.setDefaultStatusCode(302);
        redirect.setCacheTtlSeconds(60);
        redirect.setNotFoundCacheTtlSeconds(60);

        AnalyticsProperties analytics = new AnalyticsProperties();
        analytics.setSalt("test-analytics-salt");
        analytics.setRedisKeyTtlDays(1);

        EdgeProperties edge = new EdgeProperties();

        CorsProperties cors = new CorsProperties();
        cors.setAllowCredentials(true);
        cors.setAllowedOrigins(java.util.List.of());

        AppStartupValidator v = new AppStartupValidator(env, core, id, security, cors, redirect, analytics, edge);
        assertThatThrownBy(() -> v.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }
}

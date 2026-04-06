package com.linkforge.config;

import com.linkforge.accounts.interfaces.startup.AccountsStartupCheck;
import com.linkforge.analytics.infrastructure.startup.AnalyticsStartupCheck;
import com.linkforge.app.startup.AppStartupValidator;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.runtime.startup.StartupCheck;
import com.linkforge.redirect.interfaces.startup.RedirectStartupCheck;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiStartupValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StartupValidationTestConfig.class)
            .withPropertyValues(
                    "test.base-url=http://localhost",
                    "test.jwt-secret=test-secret-please-change-but-long-enough-32-bytes"
            );

    @Test
    void strictConfig_should_fail_through_aggregated_startup_checks() {
        contextRunner
                .withPropertyValues(
                        "app.strict-config=true",
                        "test.jwt-secret=dev-change-me-please-set-env-and-long-enough-32-bytes"
                )
                .run(context -> {
                    assertThat(context.getBeansOfType(StartupCheck.class))
                            .hasSize(3)
                            .containsKeys("accountsStartupCheck", "redirectStartupCheck", "analyticsStartupCheck");

                    AppStartupValidator validator = context.getBean(AppStartupValidator.class);
                    assertThatThrownBy(() -> validator.run(null))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("JWT secret");
                });
    }

    @Test
    void app_checks_should_still_run_alongside_aggregated_startup_checks() {
        contextRunner
                .withPropertyValues("test.base-url=")
                .run(context -> {
                    assertThat(context.getBeansOfType(StartupCheck.class)).hasSize(3);

                    AppStartupValidator validator = context.getBean(AppStartupValidator.class);
                    assertThatThrownBy(() -> validator.run(null))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("app.base-url");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class StartupValidationTestConfig {

        @Bean
        AppStartupValidator appStartupValidator(
                Environment env,
                CoreProperties coreProperties,
                IdProperties idProperties,
                List<StartupCheck> startupChecks
        ) {
            return new AppStartupValidator(env, coreProperties, idProperties, startupChecks);
        }

        @Bean
        CoreProperties coreProperties(Environment env) {
            CoreProperties core = new CoreProperties();
            core.setBaseUrl(env.getProperty("test.base-url"));
            return core;
        }

        @Bean
        IdProperties idProperties() {
            IdProperties id = new IdProperties();
            id.setWorkerId(2);
            id.setDatacenterId(2);
            return id;
        }

        @Bean
        SecurityProperties securityProperties(Environment env) {
            SecurityProperties security = new SecurityProperties();
            security.getJwt().setSecret(env.getProperty("test.jwt-secret"));
            security.getJwt().setCookieEnabled(env.getProperty("test.jwt.cookie-enabled", Boolean.class, false));
            security.getJwt().setCookieName(env.getProperty("test.jwt.cookie-name"));
            security.getJwt().setCookieSameSite(env.getProperty("test.jwt.cookie-same-site"));
            security.getJwt().setCookieSecure(env.getProperty("test.jwt.cookie-secure", Boolean.class, false));
            return security;
        }

        @Bean
        CorsProperties corsProperties(Environment env) {
            CorsProperties cors = new CorsProperties();
            cors.setAllowCredentials(env.getProperty("test.cors.allow-credentials", Boolean.class, false));

            String origins = env.getProperty("test.cors.allowed-origins");
            if (origins != null) {
                cors.setAllowedOrigins(Arrays.stream(origins.split(",", -1)).toList());
            }
            return cors;
        }

        @Bean
        RedirectProperties redirectProperties() {
            RedirectProperties redirect = new RedirectProperties();
            redirect.setDefaultStatusCode(302);
            redirect.setCacheTtlSeconds(60);
            redirect.setNotFoundCacheTtlSeconds(60);
            return redirect;
        }

        @Bean
        AnalyticsProperties analyticsProperties() {
            AnalyticsProperties analytics = new AnalyticsProperties();
            analytics.setSalt("test-analytics-salt");
            analytics.setRedisKeyTtlDays(1);
            return analytics;
        }

        @Bean
        EdgeProperties edgeProperties() {
            return new EdgeProperties();
        }

        @Bean
        AccountsStartupCheck accountsStartupCheck(SecurityProperties securityProperties, CorsProperties corsProperties) {
            return new AccountsStartupCheck(securityProperties, corsProperties);
        }

        @Bean
        RedirectStartupCheck redirectStartupCheck(RedirectProperties redirectProperties, EdgeProperties edgeProperties) {
            return new RedirectStartupCheck(redirectProperties, edgeProperties);
        }

        @Bean
        AnalyticsStartupCheck analyticsStartupCheck(AnalyticsProperties analyticsProperties) {
            return new AnalyticsStartupCheck(analyticsProperties);
        }
    }
}

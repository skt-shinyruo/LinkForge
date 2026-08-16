package com.linkforge.accounts.interfaces.startup;

import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsStartupCheckTest {

    private final ApplicationContextRunner bindingContext = new ApplicationContextRunner()
            .withUserConfiguration(ApiKeyPropertiesBindingConfig.class);

    @Test
    void strictMode_shouldRequireIndependentCurrentApiKeyPepper_andDisableJwtFallback() {
        SecurityProperties properties = validJwtProperties();
        properties.getApiKey().setLegacyJwtFallbackEnabled(true);
        AccountsStartupCheck check = new AccountsStartupCheck(properties, new CorsProperties());
        List<String> errors = new ArrayList<>();

        check.validate(true, errors);

        assertThat(errors)
                .anyMatch(error -> error.contains("current-pepper"))
                .anyMatch(error -> error.contains("JWT fallback"));
    }

    @Test
    void strictMode_shouldRejectApiKeyPepperEqualToJwtSecret() {
        SecurityProperties properties = validJwtProperties();
        properties.getApiKey().setCurrentKeyId("pepper-v1");
        properties.getApiKey().setCurrentPepper(properties.getJwt().getSecret());
        properties.getApiKey().setLegacyJwtFallbackEnabled(false);
        AccountsStartupCheck check = new AccountsStartupCheck(properties, new CorsProperties());
        List<String> errors = new ArrayList<>();

        check.validate(true, errors);

        assertThat(errors).anyMatch(error -> error.contains("必须独立于 JWT secret"));
    }

    @Test
    void strictMode_shouldRejectLegacyAndCompatibilityPeppersEqualToJwtSecret() {
        SecurityProperties properties = validJwtProperties();
        String jwtSecret = properties.getJwt().getSecret();
        properties.getApiKey().setCurrentKeyId("pepper-v2");
        properties.getApiKey().setCurrentPepper("independent-current-api-key-pepper-at-least-32-bytes");
        properties.getApiKey().setLegacyPepper(jwtSecret);
        properties.getApiKey().setHmacPepper(jwtSecret);
        properties.getApiKey().setLegacyJwtFallbackEnabled(false);
        AccountsStartupCheck check = new AccountsStartupCheck(properties, new CorsProperties());
        List<String> errors = new ArrayList<>();

        check.validate(true, errors);

        assertThat(errors)
                .anyMatch(error -> error.contains("legacy pepper 必须独立于 JWT secret"))
                .anyMatch(error -> error.contains("hmac-pepper 必须独立于 JWT secret"));
    }

    @Test
    void strictMode_shouldAcceptExplicitCurrentAndPreviousKeyring() {
        SecurityProperties properties = validJwtProperties();
        properties.getApiKey().setCurrentKeyId("pepper-v2");
        properties.getApiKey().setCurrentPepper("current-api-key-pepper-at-least-32-bytes");
        properties.getApiKey().setPreviousKeyId("pepper-v1");
        properties.getApiKey().setPreviousPepper("previous-api-key-pepper-at-least-32-bytes");
        properties.getApiKey().setLegacyJwtFallbackEnabled(false);
        AccountsStartupCheck check = new AccountsStartupCheck(properties, new CorsProperties());
        List<String> errors = new ArrayList<>();

        check.validate(true, errors);

        assertThat(errors).isEmpty();
    }

    @ParameterizedTest(name = "strict={0}")
    @ValueSource(booleans = {false, true})
    void apiKeyKeyIdsLongerThanDatabaseColumn_shouldBeRejected(boolean strict) {
        SecurityProperties properties = validApiKeyProperties();
        properties.getApiKey().setCurrentKeyId("c".repeat(65));
        properties.getApiKey().setPreviousKeyId("p".repeat(65));
        AccountsStartupCheck check = new AccountsStartupCheck(properties, new CorsProperties());
        List<String> errors = new ArrayList<>();

        check.validate(strict, errors);

        assertThat(errors)
                .anyMatch(error -> error.contains("current-key-id") && error.contains("64"))
                .anyMatch(error -> error.contains("previous-key-id") && error.contains("64"));
    }

    @ParameterizedTest(name = "strict={0}")
    @ValueSource(booleans = {false, true})
    void apiKeyKeyIdsAtDatabaseColumnLimit_shouldBeAccepted(boolean strict) {
        SecurityProperties properties = validApiKeyProperties();
        properties.getApiKey().setCurrentKeyId("c".repeat(64));
        properties.getApiKey().setPreviousKeyId("p".repeat(64));
        AccountsStartupCheck check = new AccountsStartupCheck(properties, new CorsProperties());
        List<String> errors = new ArrayList<>();

        check.validate(strict, errors);

        assertThat(errors).isEmpty();
    }

    @Test
    void configurationBinding_shouldPopulateIndependentKeyringUsedByStartupCheck() {
        bindingContext.withPropertyValues(
                "app.security.jwt.secret=independent-jwt-signing-secret-at-least-32-bytes",
                "app.security.api-key.current-key-id=pepper-v2",
                "app.security.api-key.current-pepper=independent-current-api-key-pepper-at-least-32-bytes",
                "app.security.api-key.previous-key-id=pepper-v1",
                "app.security.api-key.previous-pepper=independent-previous-api-key-pepper-at-least-32-bytes",
                "app.security.api-key.legacy-jwt-fallback-enabled=false"
        ).run(context -> {
            SecurityProperties properties = context.getBean(SecurityProperties.class);
            assertThat(properties.getApiKey().getCurrentKeyId()).isEqualTo("pepper-v2");
            assertThat(properties.getApiKey().getPreviousKeyId()).isEqualTo("pepper-v1");

            List<String> errors = new ArrayList<>();
            context.getBean(AccountsStartupCheck.class).validate(true, errors);
            assertThat(errors).isEmpty();
        });
    }

    private static SecurityProperties validJwtProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("independent-jwt-secret-at-least-32-bytes");
        return properties;
    }

    private static SecurityProperties validApiKeyProperties() {
        SecurityProperties properties = validJwtProperties();
        properties.getApiKey().setCurrentKeyId("pepper-v2");
        properties.getApiKey().setCurrentPepper("current-api-key-pepper-at-least-32-bytes");
        properties.getApiKey().setPreviousKeyId("pepper-v1");
        properties.getApiKey().setPreviousPepper("previous-api-key-pepper-at-least-32-bytes");
        properties.getApiKey().setLegacyJwtFallbackEnabled(false);
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class ApiKeyPropertiesBindingConfig {

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties();
        }

        @Bean
        AccountsStartupCheck accountsStartupCheck(
                SecurityProperties securityProperties,
                CorsProperties corsProperties
        ) {
            return new AccountsStartupCheck(securityProperties, corsProperties);
        }
    }
}

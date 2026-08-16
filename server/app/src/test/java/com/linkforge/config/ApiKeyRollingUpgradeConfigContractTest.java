package com.linkforge.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyRollingUpgradeConfigContractTest {

    @Test
    void existingSinglePepperComposeDeployment_shouldFeedV26CurrentAndLegacySlots() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        String compose = Files.readString(Path.of("../../deploy/docker-compose.yml"));

        assertThat(application)
                .contains("current-pepper: ${API_KEY_CURRENT_PEPPER:${API_KEY_HMAC_PEPPER:}}")
                .contains("legacy-pepper: ${API_KEY_LEGACY_PEPPER:${API_KEY_HMAC_PEPPER:}}");
        assertThat(compose)
                .contains("API_KEY_HMAC_PEPPER: ${API_KEY_HMAC_PEPPER:-${API_KEY_CURRENT_PEPPER:-}}")
                .contains("API_KEY_CURRENT_PEPPER: ${API_KEY_CURRENT_PEPPER:-${API_KEY_HMAC_PEPPER:-}}")
                .contains("API_KEY_LEGACY_PEPPER: ${API_KEY_LEGACY_PEPPER:-${API_KEY_HMAC_PEPPER:-${API_KEY_CURRENT_PEPPER:-}}}");
    }

    @Test
    void newComposeDeployment_shouldExposeCurrentPepperToOldReadersDuringMixedVersionPhase() throws Exception {
        String envExample = Files.readString(Path.of("../../deploy/.env.example"));

        assertThat(envExample)
                .contains("API_KEY_CURRENT_PEPPER=please_set_an_independent_random_secret_at_least_32_bytes")
                .contains("API_KEY_HMAC_PEPPER=${API_KEY_CURRENT_PEPPER}");
    }
}

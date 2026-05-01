package com.linkforge.platform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationKeyTest {

    @Test
    void of_shouldTrimApplicationKeyWithoutChangingCase() {
        assertThat(ApplicationKey.of(" AppKey01 ").value()).isEqualTo("AppKey01");
    }

    @Test
    void of_shouldRejectBlankApplicationKey() {
        assertThatThrownBy(() -> ApplicationKey.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("applicationKey");
    }
}

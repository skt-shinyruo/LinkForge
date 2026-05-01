package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkTagPolicyTest {

    private final LinkTagPolicy policy = new LinkTagPolicy();

    @Test
    void normalizeAssignment_shouldTrimDeduplicateAndLimitTags() {
        Set<String> normalized = policy.normalizeAssignment(Arrays.asList(" one ", "two", "one", " ", null));

        assertThat(normalized).containsExactlyInAnyOrder("one", "two");
    }

    @Test
    void normalizeName_shouldRejectOverlongTag() {
        assertThatThrownBy(() -> policy.normalizeName("x".repeat(65)))
                .isInstanceOf(ShortLinkDomainException.class);
    }
}

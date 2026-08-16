package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkPublicMutationContractTest {

    private static final Set<String> PUBLISHED_METHOD_NAMES = Set.of(
            "create",
            "rehydrate",
            "id",
            "tenantId",
            "applicationId",
            "domainId",
            "code",
            "lifecycleState",
            "originalUrl",
            "note",
            "enabled",
            "expiresAtUtc",
            "archivedAtUtc",
            "redirectStatusCode",
            "previewEnabled",
            "unavailableLandingUrl",
            "queryForwardMode",
            "queryForwardAllowlist",
            "createdBy",
            "version",
            "createdByType",
            "createdAtUtc",
            "updatedAtUtc",
            "archive",
            "restore",
            "delete",
            "planPatch",
            "applyUpdate",
            "approveDestinationChange"
    );

    @Test
    void aggregate_shouldExposeOnlyNamedMutations() {
        List<String> published = Arrays.stream(ShortLink.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .distinct()
                .sorted()
                .toList();
        List<String> expected = PUBLISHED_METHOD_NAMES.stream().sorted().toList();

        assertThat(published)
                .as("ShortLink public API changes require an explicit named-behavior contract review")
                .containsExactlyElementsOf(expected);
        assertThat(Arrays.stream(ShortLink.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())))
                .noneMatch(method -> method.isAnnotationPresent(Deprecated.class));
    }
}

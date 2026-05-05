package com.linkforge.foundation.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPrincipalTest {

    @Test
    void constructor_shouldPreserveRolesReference() {
        Set<String> roles = new HashSet<>(Set.of(StandardRoles.OPENAPI));

        AuthPrincipal principal = new AuthPrincipal(0L, 1L, null, roles);

        assertThat(principal.getRoles()).isSameAs(roles);
    }
}

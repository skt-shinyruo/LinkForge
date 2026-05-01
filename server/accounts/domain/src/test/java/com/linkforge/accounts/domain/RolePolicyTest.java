package com.linkforge.accounts.domain;

import com.linkforge.foundation.security.StandardRoles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolePolicyTest {

    private final RolePolicy policy = new RolePolicy();

    @Test
    void effectiveRoles_shouldDefaultToUserWhenEmpty() {
        assertThat(policy.effectiveRoles(List.of())).containsExactly(StandardRoles.USER);
    }

    @Test
    void effectiveRoles_shouldDefaultToUserWhenNull() {
        assertThat(policy.effectiveRoles(null)).containsExactly(StandardRoles.USER);
    }

    @Test
    void effectiveRoles_shouldReturnAssignedRolesAndIgnoreNullAssignments() {
        List<RoleAssignment> assignments = new ArrayList<>();
        assignments.add(RoleAssignment.of(20L, RoleCode.of(StandardRoles.TENANT_ADMIN)));
        assignments.add(null);

        Set<String> roles = policy.effectiveRoles(assignments);

        assertThat(roles).containsExactly(StandardRoles.TENANT_ADMIN);
    }

    @Test
    void roleCode_shouldTrimValue() {
        assertThat(RoleCode.of("  " + StandardRoles.USER + "  ").value()).isEqualTo(StandardRoles.USER);
    }

    @Test
    void roleAssignment_shouldRejectInvalidUserId() {
        assertThatThrownBy(() -> RoleAssignment.of(0L, RoleCode.of(StandardRoles.USER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeUserGrantableRoles_shouldTrimAndValidateRoles() {
        Set<RoleCode> roles = policy.normalizeUserGrantableRoles(List.of(
                "  " + StandardRoles.USER + "  ",
                StandardRoles.TENANT_ADMIN
        ));

        assertThat(roles).extracting(RoleCode::value)
                .containsExactlyInAnyOrder(StandardRoles.USER, StandardRoles.TENANT_ADMIN);
    }

    @Test
    void normalizeUserGrantableRoles_shouldRejectUnknownRole() {
        assertThatThrownBy(() -> policy.normalizeUserGrantableRoles(List.of(StandardRoles.OPENAPI)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown role");
    }
}

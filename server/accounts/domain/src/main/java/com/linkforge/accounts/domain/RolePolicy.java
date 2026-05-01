package com.linkforge.accounts.domain;

import com.linkforge.foundation.security.StandardRoles;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RolePolicy {

    private static final Set<String> USER_GRANTABLE_ROLES = Set.of(
            StandardRoles.TENANT_ADMIN,
            StandardRoles.USER
    );

    public Set<String> effectiveRoles(Collection<RoleAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return Set.of(StandardRoles.USER);
        }
        Set<String> roles = assignments.stream()
                .filter(assignment -> assignment != null)
                .map(RoleAssignment::roleCode)
                .map(RoleCode::value)
                .collect(Collectors.toUnmodifiableSet());
        return roles.isEmpty() ? Set.of(StandardRoles.USER) : roles;
    }

    public Set<RoleCode> normalizeUserGrantableRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of(RoleCode.of(StandardRoles.USER));
        }
        Set<RoleCode> normalized = new HashSet<>();
        for (String role : roles) {
            RoleCode roleCode = RoleCode.of(role);
            if (!USER_GRANTABLE_ROLES.contains(roleCode.value())) {
                throw new IllegalArgumentException("unknown role: " + roleCode.value());
            }
            normalized.add(roleCode);
        }
        return normalized.isEmpty() ? Set.of(RoleCode.of(StandardRoles.USER)) : Set.copyOf(normalized);
    }
}

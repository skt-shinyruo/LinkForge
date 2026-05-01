package com.linkforge.governance.domain;

import java.util.Set;

public record ApprovalActor(
        long tenantId,
        long userId,
        String email,
        Set<String> roles
) {

    public ApprovalActor {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (email == null || email.trim().isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        email = email.trim();
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}

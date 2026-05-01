package com.linkforge.accounts.domain;

import java.util.Objects;

public final class RoleAssignment {

    private final long userId;
    private final RoleCode roleCode;

    private RoleAssignment(long userId, RoleCode roleCode) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        this.userId = userId;
        this.roleCode = Objects.requireNonNull(roleCode, "roleCode");
    }

    public static RoleAssignment of(long userId, RoleCode roleCode) {
        return new RoleAssignment(userId, roleCode);
    }

    public long userId() {
        return userId;
    }

    public RoleCode roleCode() {
        return roleCode;
    }
}

package com.linkforge.accounts.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserRoleId implements Serializable {

    private Long userId;

    private String roleCode;

    public UserRoleId() {
    }

    public UserRoleId(Long userId, String roleCode) {
        this.userId = userId;
        this.roleCode = roleCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(roleCode, that.roleCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleCode);
    }
}

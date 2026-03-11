package com.linkforge.accounts.infrastructure.persistence.entity;

public class UserRoleEntity {

    private UserRoleId id;

    public UserRoleEntity() {
    }

    public UserRoleEntity(UserRoleId id) {
        this.id = id;
    }

    public UserRoleId getId() {
        return id;
    }

    public void setId(UserRoleId id) {
        this.id = id;
    }
}

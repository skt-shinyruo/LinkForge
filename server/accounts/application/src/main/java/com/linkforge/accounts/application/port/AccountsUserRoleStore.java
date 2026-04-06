package com.linkforge.accounts.application.port;

import java.util.List;

public interface AccountsUserRoleStore {

    void insert(UserRoleData userRole);

    default void insert(long userId, String roleCode) {
        insert(new UserRoleData(userId, roleCode));
    }

    List<UserRoleData> findAllByUserId(Long userId);

    List<UserRoleData> findAllByUserIdIn(List<Long> userIds);

    record UserRoleData(Long userId, String roleCode) {
    }
}

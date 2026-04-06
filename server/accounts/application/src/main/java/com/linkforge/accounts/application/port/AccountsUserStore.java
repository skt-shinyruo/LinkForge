package com.linkforge.accounts.application.port;

import java.time.LocalDateTime;
import java.util.List;

public interface AccountsUserStore {

    void insert(UserData user);

    UserData findById(Long userId);

    UserData findFirstByEmail(String email);

    List<UserData> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    void update(UserData user);

    record UserData(
            Long id,
            Long tenantId,
            String email,
            String passwordHash,
            String status,
            Integer tokenVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}

package com.linkforge.accounts.infrastructure.persistence.mapper;

import com.linkforge.accounts.infrastructure.persistence.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {

    int insert(UserEntity user);

    UserEntity findById(Long id);

    UserEntity findFirstByEmail(String email);

    List<UserEntity> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    int incrementTokenVersion(Long userId);

    int updatePasswordHashAndIncrementTokenVersion(Long tenantId, Long userId, String passwordHash);

    int updateStatus(Long tenantId, Long userId, String status);

    Long lockTenantForUserAdministration(Long tenantId);
}

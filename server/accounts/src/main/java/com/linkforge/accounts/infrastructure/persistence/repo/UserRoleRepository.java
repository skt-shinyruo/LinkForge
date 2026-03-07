package com.linkforge.accounts.infrastructure.persistence.repo;

import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {

    @Query("select r from UserRoleEntity r where r.id.userId = :userId")
    List<UserRoleEntity> findAllByUserId(@Param("userId") Long userId);

    @Query("select r from UserRoleEntity r where r.id.userId in :userIds")
    List<UserRoleEntity> findAllByUserIdIn(@Param("userIds") List<Long> userIds);
}

package com.linkforge.accounts.infrastructure.persistence.mapper;

import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserRoleMapper {

    int insert(UserRoleEntity userRole);

    List<UserRoleEntity> findAllByUserId(Long userId);

    List<UserRoleEntity> findAllByUserIdIn(List<Long> userIds);
}

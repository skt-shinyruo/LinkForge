package com.linkforge.accounts.application;

import com.linkforge.accounts.infrastructure.persistence.mapper.UserMapper;
import com.linkforge.accounts.infrastructure.persistence.mapper.UserRoleMapper;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.TenantGuard;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminServiceTest {

    @Test
    void create_shouldRejectUnknownRoleCodes() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userMapper,
                userRoleMapper,
                passwordEncoder,
                tenantGuard
        );

        when(userMapper.findFirstByEmail("u@example.com")).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(123L);
        when(passwordEncoder.encode("password123")).thenReturn("hash");

        UserAdminService.CreateUserRequest req = new UserAdminService.CreateUserRequest(
                "u@example.com",
                "password123",
                Set.of("UNKNOWN_ROLE")
        );

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                });

        verify(userMapper, never()).insert(any());
        verify(userRoleMapper, never()).insert(any());
    }
}


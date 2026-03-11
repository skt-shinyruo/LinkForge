package com.linkforge.accounts.application;

import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleId;
import com.linkforge.accounts.infrastructure.persistence.mapper.TenantMapper;
import com.linkforge.accounts.infrastructure.persistence.mapper.UserMapper;
import com.linkforge.accounts.infrastructure.persistence.mapper.UserRoleMapper;
import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final SnowflakeIdGenerator idGenerator;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            SnowflakeIdGenerator idGenerator,
            TenantMapper tenantMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.idGenerator = idGenerator;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult register(String tenantName, String email, String rawPassword) {
        if (userMapper.findFirstByEmail(email) != null) {
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        long tenantId = idGenerator.nextId();
        TenantEntity t = new TenantEntity();
        t.setId(tenantId);
        t.setName(tenantName);
        t.setStatus(AccountsConstants.STATUS_ACTIVE);
        tenantMapper.insert(t);

        long userId = idGenerator.nextId();
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setTenantId(tenantId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setStatus(AccountsConstants.STATUS_ACTIVE);
        try {
            userMapper.insert(u);
        } catch (DataIntegrityViolationException e) {
            // 并发注册或绕过应用层校验时，以 DB 约束为准，返回一致的业务错误码
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        userRoleMapper.insert(new UserRoleEntity(new UserRoleId(userId, Roles.TENANT_ADMIN)));

        Set<String> roles = Set.of(Roles.TENANT_ADMIN);
        String token = jwtService.issueToken(userId, tenantId, email, roles);
        return new AuthResult(token, new AuthPrincipal(userId, tenantId, email, roles));
    }

    public AuthResult login(String email, String rawPassword) {
        UserEntity u = userMapper.findFirstByEmail(email);
        if (u == null) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        TenantEntity t = tenantMapper.findById(u.getTenantId());
        if (t == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "租户不存在");
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(t.getStatus())) {
            throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(u.getStatus())) {
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(rawPassword, u.getPasswordHash())) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        Set<String> roles = userRoleMapper.findAllByUserId(u.getId()).stream()
                .map(r -> r.getId().getRoleCode())
                .collect(Collectors.toUnmodifiableSet());

        if (roles.isEmpty()) {
            roles = Set.of(Roles.USER);
        }

        String token = jwtService.issueToken(u.getId(), u.getTenantId(), u.getEmail(), roles);
        return new AuthResult(token, new AuthPrincipal(u.getId(), u.getTenantId(), u.getEmail(), roles));
    }

    public record AuthResult(String token, AuthPrincipal principal) {
    }
}

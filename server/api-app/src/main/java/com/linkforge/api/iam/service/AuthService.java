package com.linkforge.api.iam.service;

import com.linkforge.api.iam.entity.TenantEntity;
import com.linkforge.api.iam.entity.UserEntity;
import com.linkforge.api.iam.entity.UserRoleEntity;
import com.linkforge.api.iam.entity.UserRoleId;
import com.linkforge.api.iam.repo.TenantRepository;
import com.linkforge.api.iam.repo.UserRepository;
import com.linkforge.api.iam.repo.UserRoleRepository;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.id.SnowflakeIdGenerator;
import com.linkforge.api.security.AuthPrincipal;
import com.linkforge.api.security.JwtService;
import com.linkforge.api.security.Roles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final SnowflakeIdGenerator idGenerator;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            SnowflakeIdGenerator idGenerator,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.idGenerator = idGenerator;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult register(String tenantName, String email, String rawPassword) {
        userRepository.findFirstByEmail(email).ifPresent(u -> {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        });

        long tenantId = idGenerator.nextId();
        TenantEntity t = new TenantEntity();
        t.setId(tenantId);
        t.setName(tenantName);
        t.setStatus(IamConstants.STATUS_ACTIVE);
        tenantRepository.save(t);

        long userId = idGenerator.nextId();
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setTenantId(tenantId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setStatus(IamConstants.STATUS_ACTIVE);
        try {
            userRepository.save(u);
        } catch (DataIntegrityViolationException e) {
            // 并发注册或绕过应用层校验时，以 DB 约束为准，返回一致的业务错误码
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        userRoleRepository.save(new UserRoleEntity(new UserRoleId(userId, Roles.TENANT_ADMIN)));

        Set<String> roles = Set.of(Roles.TENANT_ADMIN);
        String token = jwtService.issueToken(userId, tenantId, email, roles);
        return new AuthResult(token, new AuthPrincipal(userId, tenantId, email, roles));
    }

    public AuthResult login(String email, String rawPassword) {
        UserEntity u = userRepository.findFirstByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        TenantEntity t = tenantRepository.findById(u.getTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "租户不存在"));

        if (!IamConstants.STATUS_ACTIVE.equals(t.getStatus())) {
            throw new BusinessException(ErrorCode.TENANT_DISABLED);
        }
        if (!IamConstants.STATUS_ACTIVE.equals(u.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(rawPassword, u.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Set<String> roles = userRoleRepository.findAllByUserId(u.getId()).stream()
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

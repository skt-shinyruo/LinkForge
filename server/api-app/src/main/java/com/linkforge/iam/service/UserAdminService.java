package com.linkforge.iam.service;

import com.linkforge.iam.entity.UserEntity;
import com.linkforge.iam.entity.UserRoleEntity;
import com.linkforge.iam.entity.UserRoleId;
import com.linkforge.iam.repo.UserRepository;
import com.linkforge.iam.repo.UserRoleRepository;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.id.SnowflakeIdGenerator;
import com.linkforge.platform.security.TenantGuard;
import com.linkforge.platform.security.Roles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserAdminService {

    private final SnowflakeIdGenerator idGenerator;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantGuard tenantGuard;

    public UserAdminService(
            SnowflakeIdGenerator idGenerator,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            TenantGuard tenantGuard
    ) {
        this.idGenerator = idGenerator;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantGuard = tenantGuard;
    }

    public List<UserDto> list(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        List<UserEntity> users = userRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(UserEntity::getId).toList();
        Map<Long, Set<String>> rolesByUserId = loadRolesByUserIds(userIds);

        return users.stream()
                .map(u -> new UserDto(
                        u.getId(),
                        u.getTenantId(),
                        u.getEmail(),
                        u.getStatus(),
                        rolesByUserId.getOrDefault(u.getId(), Set.of())
                ))
                .toList();
    }

    @Transactional
    public UserDto create(long tenantId, CreateUserRequest req) {
        tenantGuard.requireCurrentTenant(tenantId);
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }

        // MVP：为了简化登录（email 不需要选择租户），仍然约束全局唯一
        userRepository.findFirstByEmail(req.email()).ifPresent(u -> {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        });

        long userId = idGenerator.nextId();
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setTenantId(tenantId);
        u.setEmail(req.email());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setStatus(IamConstants.STATUS_ACTIVE);
        userRepository.save(u);

        Set<String> roles = req.roles() == null || req.roles().isEmpty() ? Set.of(Roles.USER) : req.roles();
        for (String role : roles) {
            userRoleRepository.save(new UserRoleEntity(new UserRoleId(userId, role)));
        }

        return new UserDto(userId, tenantId, u.getEmail(), u.getStatus(), roles);
    }

    @Transactional
    public UserDto disable(long tenantId, long userId) {
        tenantGuard.requireCurrentTenant(tenantId);
        UserEntity u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!tenantIdEquals(u.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!IamConstants.STATUS_DISABLED.equals(u.getStatus())) {
            u.setStatus(IamConstants.STATUS_DISABLED);
            userRepository.save(u);
        }
        Set<String> roles = loadRolesByUserId(userId);
        return new UserDto(u.getId(), tenantId, u.getEmail(), u.getStatus(), roles);
    }

    @Transactional
    public UserDto enable(long tenantId, long userId) {
        tenantGuard.requireCurrentTenant(tenantId);
        UserEntity u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!tenantIdEquals(u.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!IamConstants.STATUS_ACTIVE.equals(u.getStatus())) {
            u.setStatus(IamConstants.STATUS_ACTIVE);
            userRepository.save(u);
        }
        Set<String> roles = loadRolesByUserId(userId);
        return new UserDto(u.getId(), tenantId, u.getEmail(), u.getStatus(), roles);
    }

    @Transactional
    public UserDto resetPassword(long tenantId, long userId, String newPassword) {
        tenantGuard.requireCurrentTenant(tenantId);
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能为空");
        }
        if (newPassword.length() < 8 || newPassword.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度需为 8-64");
        }

        UserEntity u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!tenantIdEquals(u.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(u);

        Set<String> roles = loadRolesByUserId(userId);
        return new UserDto(u.getId(), tenantId, u.getEmail(), u.getStatus(), roles);
    }

    private Map<Long, Set<String>> loadRolesByUserIds(List<Long> userIds) {
        Map<Long, Set<String>> map = new HashMap<>();
        for (UserRoleEntity r : userRoleRepository.findAllByUserIdIn(userIds)) {
            map.computeIfAbsent(r.getId().getUserId(), k -> new java.util.HashSet<>())
                    .add(r.getId().getRoleCode());
        }
        return map;
    }

    private Set<String> loadRolesByUserId(long userId) {
        Set<String> roles = userRoleRepository.findAllByUserId(userId).stream()
                .map(r -> r.getId().getRoleCode())
                .collect(java.util.stream.Collectors.toSet());
        return roles.isEmpty() ? Set.of(Roles.USER) : roles;
    }

    private static boolean tenantIdEquals(Long actual, long expected) {
        return actual != null && actual == expected;
    }

    public record CreateUserRequest(String email, String password, Set<String> roles) {
    }

    public record UserDto(long id, long tenantId, String email, String status, Set<String> roles) {
    }
}

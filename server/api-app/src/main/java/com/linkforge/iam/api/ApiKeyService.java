package com.linkforge.iam.api;

import com.linkforge.iam.entity.ApiKeyEntity;
import com.linkforge.iam.repo.ApiKeyRepository;
import com.linkforge.iam.service.IamConstants;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.id.SnowflakeIdGenerator;
import com.linkforge.platform.security.TenantGuard;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class ApiKeyService {

    private static final String API_KEY_PREFIX = "lfk";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SnowflakeIdGenerator idGenerator;
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantGuard tenantGuard;

    public ApiKeyService(
            SnowflakeIdGenerator idGenerator,
            ApiKeyRepository apiKeyRepository,
            PasswordEncoder passwordEncoder,
            TenantGuard tenantGuard
    ) {
        this.idGenerator = idGenerator;
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantGuard = tenantGuard;
    }

    @Transactional
    public CreatedApiKey create(long tenantId, String name) {
        tenantGuard.requireCurrentTenant(tenantId);
        long id = idGenerator.nextId();
        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + id + "_" + secret;

        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setName(name);
        e.setKeyHash(passwordEncoder.encode(secret));
        e.setStatus(IamConstants.STATUS_ACTIVE);
        apiKeyRepository.save(e);

        return new CreatedApiKey(id, name, key);
    }

    public ApiKeyAuthResult authenticate(String apiKey) {
        Parsed parsed = parse(apiKey);
        ApiKeyEntity e = apiKeyRepository.findById(parsed.id)
                .orElseThrow(() -> new ApiKeyAuthException(ErrorCode.API_KEY_INVALID));

        if (!IamConstants.STATUS_ACTIVE.equals(e.getStatus())) {
            throw new ApiKeyAuthException(ErrorCode.API_KEY_DISABLED);
        }
        if (!passwordEncoder.matches(parsed.secret, e.getKeyHash())) {
            throw new ApiKeyAuthException(ErrorCode.API_KEY_INVALID);
        }

        // MVP：同步更新 last_used_at（若后续成为性能瓶颈可异步化）
        e.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(e);

        return new ApiKeyAuthResult(e.getTenantId(), e.getId());
    }

    public List<ApiKeyInfo> list(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        return apiKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(e -> new ApiKeyInfo(e.getId(), e.getName(), e.getStatus(), e.getLastUsedAt(), e.getCreatedAt()))
                .toList();
    }

    @Transactional
    public ApiKeyInfo disable(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ApiKeyEntity e = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在"));
        if (!tenantIdEquals(e.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!IamConstants.STATUS_DISABLED.equals(e.getStatus())) {
            e.setStatus(IamConstants.STATUS_DISABLED);
            apiKeyRepository.save(e);
        }
        return new ApiKeyInfo(e.getId(), e.getName(), e.getStatus(), e.getLastUsedAt(), e.getCreatedAt());
    }

    @Transactional
    public ApiKeyInfo enable(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ApiKeyEntity e = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在"));
        if (!tenantIdEquals(e.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!IamConstants.STATUS_ACTIVE.equals(e.getStatus())) {
            e.setStatus(IamConstants.STATUS_ACTIVE);
            apiKeyRepository.save(e);
        }
        return new ApiKeyInfo(e.getId(), e.getName(), e.getStatus(), e.getLastUsedAt(), e.getCreatedAt());
    }

    @Transactional
    public CreatedApiKey rotate(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ApiKeyEntity e = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在"));
        if (!tenantIdEquals(e.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }

        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + e.getId() + "_" + secret;
        e.setKeyHash(passwordEncoder.encode(secret));
        e.setStatus(IamConstants.STATUS_ACTIVE);
        apiKeyRepository.save(e);

        return new CreatedApiKey(e.getId(), e.getName(), key);
    }

    public record CreatedApiKey(long id, String name, String apiKey) {
    }

    public record ApiKeyAuthResult(long tenantId, long apiKeyId) {
    }

    public record ApiKeyInfo(long id, String name, String status, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
    }

    public static class ApiKeyAuthException extends RuntimeException {
        private final ErrorCode errorCode;

        public ApiKeyAuthException(ErrorCode errorCode) {
            super(errorCode.getDefaultMessage());
            this.errorCode = errorCode;
        }

        public ErrorCode errorCode() {
            return errorCode;
        }
    }

    private static class Parsed {
        private final long id;
        private final String secret;

        private Parsed(long id, String secret) {
            this.id = id;
            this.secret = secret;
        }
    }

    private static Parsed parse(String apiKey) {
        if (apiKey == null) {
            throw new ApiKeyAuthException(ErrorCode.API_KEY_INVALID);
        }
        String[] parts = apiKey.split("_", 3);
        if (parts.length != 3) {
            throw new ApiKeyAuthException(ErrorCode.API_KEY_INVALID);
        }
        if (!API_KEY_PREFIX.equals(parts[0])) {
            throw new ApiKeyAuthException(ErrorCode.API_KEY_INVALID);
        }
        long id;
        try {
            id = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new ApiKeyAuthException(ErrorCode.API_KEY_INVALID);
        }
        String secret = parts[2];
        if (secret.isBlank()) {
            throw new ApiKeyAuthException(ErrorCode.API_KEY_INVALID);
        }
        return new Parsed(id, secret);
    }

    private static String randomSecret() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static boolean tenantIdEquals(Long actual, long expected) {
        return actual != null && actual == expected;
    }
}

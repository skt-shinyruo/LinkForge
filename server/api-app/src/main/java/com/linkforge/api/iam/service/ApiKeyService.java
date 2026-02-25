package com.linkforge.api.iam.service;

import com.linkforge.api.iam.entity.ApiKeyEntity;
import com.linkforge.api.iam.repo.ApiKeyRepository;
import com.linkforge.api.iam.service.IamConstants;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.config.AppProperties;
import com.linkforge.platform.id.SnowflakeIdGenerator;
import com.linkforge.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String API_KEY_PREFIX = "lfk";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SnowflakeIdGenerator idGenerator;
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantGuard tenantGuard;
    private final AppProperties properties;

    public ApiKeyService(
            SnowflakeIdGenerator idGenerator,
            ApiKeyRepository apiKeyRepository,
            PasswordEncoder passwordEncoder,
            TenantGuard tenantGuard,
            AppProperties properties
    ) {
        this.idGenerator = idGenerator;
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantGuard = tenantGuard;
        this.properties = properties;
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

        // OpenAPI 高调用路径：last_used_at 采用节流写回，避免 DB 写热点
        tryUpdateLastUsedAtThrottled(e);

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

    private void tryUpdateLastUsedAtThrottled(ApiKeyEntity e) {
        if (e == null) {
            return;
        }
        long intervalSeconds = 300;
        try {
            if (properties != null
                    && properties.getSecurity() != null
                    && properties.getSecurity().getApiKey() != null) {
                intervalSeconds = properties.getSecurity().getApiKey().getLastUsedUpdateIntervalSeconds();
            }
        } catch (Exception ignore) {
            // ignore
        }
        if (intervalSeconds < 0) {
            intervalSeconds = 0;
        }
        if (intervalSeconds == 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = e.getLastUsedAt();
        if (last != null && !last.plusSeconds(intervalSeconds).isBefore(now)) {
            return;
        }
        try {
            e.setLastUsedAt(now);
            apiKeyRepository.save(e);
        } catch (Exception ex) {
            // best-effort：避免影响主链路鉴权
            log.debug("update api_key last_used_at failed: id={}, err={}", e.getId(), ex.getMessage());
        }
    }

    private static boolean tenantIdEquals(Long actual, long expected) {
        return actual != null && actual == expected;
    }
}

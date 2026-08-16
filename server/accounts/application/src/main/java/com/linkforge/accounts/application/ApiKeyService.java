package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.ApiKeyAuthCache;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.observability.OperationalMetrics;
import com.linkforge.foundation.security.ApiKeyAuthenticationException;
import com.linkforge.foundation.security.ApiKeyAuthenticationFailure;
import com.linkforge.foundation.security.ApiKeyAuthenticationResult;
import com.linkforge.foundation.security.ApiKeyAuthenticator;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

/**
 * API Key 生命周期与 OpenAPI 认证的应用服务。
 *
 * <p>Key 的 wire format 为 {@code lfk_<id>_<secret>}：持久化层只保存 secret 哈希，完整明文仅在
 * 创建或轮换的返回值中出现一次。每个可用 Key 必须绑定租户下已存在的应用；历史上
 * {@code applicationId == null} 的记录在认证阶段按无效 Key 拒绝。</p>
 *
 * <p>认证缓存只提供“已禁用”的快速拒绝。缓存未命中、缓存值格式无效或不可用均回源数据库，启用
 * 状态不以缓存作为事实源。管理操作先提交持久化状态，再通过 {@link PostCommitHookPort} 发布缓存更新，
 * 避免回滚事务把未提交状态暴露给认证链路。</p>
 */
@Service
public class ApiKeyService implements ApiKeyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String API_KEY_PREFIX = "lfk";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_API_KEY_LEN = 256;
    private static final int MAX_API_KEY_SECRET_LEN = 128;

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsApiKeyStore apiKeyStore;
    private final ApiKeySecretCodec secretCodec;
    private final SecurityProperties securityProperties;
    private final ApiKeyAuthCache authCache;
    private final Clock clock;
    private final PostCommitHookPort postCommitHookPort;
    private final ApplicationScopePort applicationScopePort;
    private final OperationalMetrics metrics;

    public ApiKeyService(
            SnowflakeIdGenerator idGenerator,
            AccountsApiKeyStore apiKeyStore,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties securityProperties,
            ApiKeyAuthCache authCache,
            Clock clock,
            PostCommitHookPort postCommitHookPort,
            ApplicationScopePort applicationScopePort
    ) {
        this(
                idGenerator,
                apiKeyStore,
                passwordHasher,
                securityProperties,
                authCache,
                clock,
                postCommitHookPort,
                applicationScopePort,
                OperationalMetrics.noop()
        );
    }

    @Autowired
    public ApiKeyService(
            SnowflakeIdGenerator idGenerator,
            AccountsApiKeyStore apiKeyStore,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties securityProperties,
            ApiKeyAuthCache authCache,
            Clock clock,
            PostCommitHookPort postCommitHookPort,
            ApplicationScopePort applicationScopePort,
            OperationalMetrics metrics
    ) {
        this.idGenerator = idGenerator;
        this.apiKeyStore = apiKeyStore;
        this.secretCodec = new ApiKeySecretCodec(passwordHasher, securityProperties);
        this.securityProperties = securityProperties;
        this.authCache = authCache;
        this.clock = clock;
        this.postCommitHookPort = postCommitHookPort;
        this.applicationScopePort = applicationScopePort;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * 已废弃语义的兼容入口；API Key 必须显式绑定应用，因此稳定返回参数错误。
     */
    @Transactional
    public CreatedApiKeyResult create(long tenantId, String name) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationId 不能为空");
    }

    /**
     * 为租户内应用创建 API Key。
     *
     * <p>应用归属由跨上下文端口校验。返回的明文 Key 无法从哈希恢复，调用方必须只在本次响应中
     * 展示或保存。</p>
     */
    @Transactional
    public CreatedApiKeyResult create(long tenantId, long applicationId, String name) {
        applicationScopePort.requireApplicationExists(tenantId, applicationId);
        long id = idGenerator.nextId();
        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + id + "_" + secret;
        ApiKeySecretCodec.EncodedSecret encodedSecret = secretCodec.encodeCurrent(secret);

        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                id,
                tenantId,
                applicationId,
                name,
                encodedSecret.hash(),
                encodedSecret.keyId(),
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        apiKeyStore.insert(apiKey);

        return new CreatedApiKeyResult(id, name, key);
    }

    /**
     * 校验 API Key 格式、状态、应用绑定和 secret 哈希。
     *
     * <p>禁用缓存命中可直接拒绝；其他情况始终读取持久化记录。认证成功后的
     * {@code lastUsedAt} 更新是限流且 best-effort 的审计信号，失败不会把有效请求变成认证失败。
     * 时间按 UTC 写入无时区的 {@link LocalDateTime} 字段。</p>
     *
     * @throws ApiKeyAuthException Key 格式/secret/应用绑定无效，或 Key 已禁用
     */
    public ApiKeyAuthResult authenticate(String apiKey) {
        long startedAt = System.nanoTime();
        try {
            ApiKeyAuthResult result = authenticateInternal(apiKey);
            metrics.increment("linkforge.auth.api_key.requests", "result", "success");
            metrics.record(
                    "linkforge.auth.api_key.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    "success"
            );
            return result;
        } catch (ApiKeyAuthException ex) {
            String result = OpenApiErrorCode.API_KEY_DISABLED.equals(ex.errorCode()) ? "disabled" : "invalid";
            metrics.increment("linkforge.auth.api_key.requests", "result", result);
            metrics.record(
                    "linkforge.auth.api_key.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    result
            );
            throw ex;
        } catch (RuntimeException ex) {
            metrics.increment("linkforge.auth.api_key.requests", "result", "failure");
            metrics.record(
                    "linkforge.auth.api_key.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    "failure"
            );
            throw ex;
        }
    }

    private ApiKeyAuthResult authenticateInternal(String apiKey) {
        Parsed parsed = parse(apiKey);

        long authCacheTtlSeconds = authCacheTtlSeconds();
        ApiKeyAuthCache.Entry cached = authCacheTtlSeconds > 0
                ? authCache.read(parsed.id)
                : null;
        if (cached != null && !AccountsConstants.STATUS_ACTIVE.equals(cached.status())) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
        }

        long databaseStartedAt = System.nanoTime();
        AccountsApiKeyStore.ApiKey apiKeyRecord;
        try {
            apiKeyRecord = apiKeyStore.findById(parsed.id);
            metrics.record(
                    "linkforge.auth.api_key.database_lookup",
                    Duration.ofNanos(System.nanoTime() - databaseStartedAt),
                    "result",
                    apiKeyRecord == null ? "miss" : "hit"
            );
        } catch (RuntimeException ex) {
            metrics.record(
                    "linkforge.auth.api_key.database_lookup",
                    Duration.ofNanos(System.nanoTime() - databaseStartedAt),
                    "result",
                    "failure"
            );
            throw ex;
        }
        if (apiKeyRecord == null) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(apiKeyRecord.status())) {
            if (authCacheTtlSeconds > 0) {
                authCache.putDisabled(
                        parsed.id,
                        apiKeyRecord.tenantId() == null ? 0L : apiKeyRecord.tenantId(),
                        apiKeyRecord.applicationId(),
                        authCacheTtlSeconds
                );
            }
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
        }
        if (!secretCodec.matches(parsed.secret, apiKeyRecord.keyHash(), apiKeyRecord.keyId())) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (apiKeyRecord.applicationId() == null) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        try {
            applicationScopePort.requireApplicationExists(apiKeyRecord.tenantId(), apiKeyRecord.applicationId());
        } catch (BusinessException ex) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        upgradeLegacySecretHash(apiKeyRecord, parsed.secret);

        tryUpdateLastUsedAtThrottled(parsed.id, apiKeyRecord.lastUsedAt(), true);

        return new ApiKeyAuthResult(apiKeyRecord.tenantId(), apiKeyRecord.applicationId(), apiKeyRecord.id());
    }

    /**
     * 安全过滤器使用的认证入口，将 Accounts 错误收敛为稳定的认证失败分类。
     */
    @Override
    @Transactional
    public ApiKeyAuthenticationResult authenticateApiKey(String apiKey) {
        try {
            ApiKeyAuthResult result = authenticate(apiKey);
            return new ApiKeyAuthenticationResult(result.tenantId(), result.applicationId(), result.apiKeyId());
        } catch (ApiKeyAuthException e) {
            throw new ApiKeyAuthenticationException(toAuthenticationFailure(e.errorCode()));
        }
    }

    private static ApiKeyAuthenticationFailure toAuthenticationFailure(AppErrorCode errorCode) {
        if (OpenApiErrorCode.API_KEY_DISABLED.equals(errorCode)) {
            return ApiKeyAuthenticationFailure.DISABLED;
        }
        return ApiKeyAuthenticationFailure.INVALID;
    }

    /**
     * 列出租户的全部 API Key 元数据；结果不包含 secret 或其哈希。
     */
    public List<ApiKeyInfoResult> list(long tenantId) {
        return list(tenantId, null);
    }

    /**
     * 按可选应用过滤租户的 API Key；{@code applicationId == null} 表示不过滤。
     */
    public List<ApiKeyInfoResult> list(long tenantId, Long applicationId) {
        return apiKeyStore.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(e -> applicationId == null || applicationId.equals(e.applicationId()))
                .map(e -> new ApiKeyInfoResult(e.id(), e.applicationId(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()))
                .toList();
    }

    /**
     * 禁用租户内的 API Key。
     *
     * <p>重复禁用不重复写状态，属于幂等操作。不存在和跨租户访问都返回相同的 not-found，避免通过
     * ID 探测其他租户资源。事务提交后写入禁用缓存，使后续认证可以快速拒绝。</p>
     */
    @Transactional
    public ApiKeyInfoResult disable(long tenantId, long apiKeyId) {
        AccountsApiKeyStore.ApiKey apiKey = apiKeyStore.findById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(apiKey.tenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!AccountsConstants.STATUS_DISABLED.equals(apiKey.status())) {
            apiKey = withStatus(apiKey, AccountsConstants.STATUS_DISABLED);
            apiKeyStore.update(apiKey);
        }
        long authCacheTtlSeconds = authCacheTtlSeconds();
        putDisabledAfterCommit(
                apiKeyId,
                apiKey.tenantId() == null ? 0L : apiKey.tenantId(),
                apiKey.applicationId(),
                authCacheTtlSeconds
        );
        return new ApiKeyInfoResult(apiKey.id(), apiKey.applicationId(), apiKey.name(), apiKey.status(), apiKey.lastUsedAt(), apiKey.createdAt());
    }

    /**
     * 启用租户内的 API Key；重复启用按幂等成功处理，提交后驱逐可能残留的禁用缓存。
     */
    @Transactional
    public ApiKeyInfoResult enable(long tenantId, long apiKeyId) {
        AccountsApiKeyStore.ApiKey apiKey = apiKeyStore.findById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(apiKey.tenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(apiKey.status())) {
            apiKey = withStatus(apiKey, AccountsConstants.STATUS_ACTIVE);
            apiKeyStore.update(apiKey);
        }
        evictAfterCommit(apiKeyId);
        return new ApiKeyInfoResult(apiKey.id(), apiKey.applicationId(), apiKey.name(), apiKey.status(), apiKey.lastUsedAt(), apiKey.createdAt());
    }

    /**
     * 原 ID 不变地轮换 secret，并把 Key 恢复为启用状态。
     *
     * <p>轮换不是幂等操作，每次调用都会生成新的单次可见明文；事务提交后旧 secret 失效，并驱逐
     * 认证缓存。不存在和跨租户资源使用相同的 not-found 语义。</p>
     */
    @Transactional
    public CreatedApiKeyResult rotate(long tenantId, long apiKeyId) {
        AccountsApiKeyStore.ApiKey apiKey = apiKeyStore.findById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(apiKey.tenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }

        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + apiKey.id() + "_" + secret;
        ApiKeySecretCodec.EncodedSecret encodedSecret = secretCodec.encodeCurrent(secret);
        apiKeyStore.update(withKeyHashAndStatus(
                apiKey,
                encodedSecret.hash(),
                encodedSecret.keyId(),
                AccountsConstants.STATUS_ACTIVE
        ));

        evictAfterCommit(apiKeyId);

        return new CreatedApiKeyResult(apiKey.id(), apiKey.name(), key);
    }

    /**
     * Accounts 内部保留精确 OpenAPI 错误码的认证异常；跨入安全框架边界时会收敛为
     * {@link ApiKeyAuthenticationFailure}，避免基础设施依赖业务错误类型。
     *
     * <p>异常不包含输入 Key 或 secret，调用方也不得把原始凭据追加到日志。</p>
     */
    public static class ApiKeyAuthException extends RuntimeException {
        private final AppErrorCode errorCode;

        public ApiKeyAuthException(AppErrorCode errorCode) {
            super(errorCode.getDefaultMessage());
            this.errorCode = errorCode;
        }

        public AppErrorCode errorCode() {
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
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (apiKey.length() > MAX_API_KEY_LEN) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        String[] parts = apiKey.split("_", 3);
        if (parts.length != 3) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (!API_KEY_PREFIX.equals(parts[0])) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        long id;
        try {
            id = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        String secret = parts[2];
        if (secret.isBlank()) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (secret.length() > MAX_API_KEY_SECRET_LEN) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        return new Parsed(id, secret);
    }

    private static String randomSecret() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * 尝试刷新最近使用时间，不参与认证正确性。
     *
     * <p>缓存令牌有三态：获得时写库，未获得时跳过，缓存不可用时用数据库时间提示做本地判断后
     * 降级写库。写库失败会尽力释放令牌并记录 debug 日志，不向认证调用方传播。</p>
     */
    private void tryUpdateLastUsedAtThrottled(long apiKeyId, LocalDateTime lastUsedAtHint, boolean allowWriteWhenHintMissing) {
        long intervalSeconds = 300;
        try {
            if (securityProperties != null && securityProperties.getApiKey() != null) {
                intervalSeconds = securityProperties.getApiKey().getLastUsedUpdateIntervalSeconds();
            }
        } catch (Exception ignore) {
        }
        if (intervalSeconds < 0) {
            intervalSeconds = 0;
        }
        if (intervalSeconds == 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        ApiKeyAuthCache.LastUsedTokenResult tokenResult = authCache.tryAcquireLastUsedToken(apiKeyId, intervalSeconds);
        if (tokenResult == ApiKeyAuthCache.LastUsedTokenResult.ACQUIRED) {
            try {
                apiKeyStore.updateLastUsedAt(apiKeyId, now);
            } catch (Exception ex) {
                authCache.releaseLastUsedToken(apiKeyId);
                log.debug("update api_key last_used_at failed: id={}, err={}", apiKeyId, ex.getMessage());
            }
            return;
        }
        if (tokenResult == ApiKeyAuthCache.LastUsedTokenResult.NOT_ACQUIRED) {
            return;
        }

        if (lastUsedAtHint == null && !allowWriteWhenHintMissing) {
            return;
        }

        if (lastUsedAtHint != null && !lastUsedAtHint.plusSeconds(intervalSeconds).isBefore(now)) {
            return;
        }
        try {
            apiKeyStore.updateLastUsedAt(apiKeyId, now);
        } catch (Exception ex) {
            log.debug("update api_key last_used_at failed: id={}, err={}", apiKeyId, ex.getMessage());
        }
    }

    private static boolean tenantIdEquals(Long actual, long expected) {
        return actual != null && actual == expected;
    }

    private void upgradeLegacySecretHash(AccountsApiKeyStore.ApiKey apiKey, String secret) {
        if (!secretCodec.needsUpgrade(apiKey.keyHash(), apiKey.keyId())) {
            return;
        }
        try {
            ApiKeySecretCodec.EncodedSecret encodedSecret = secretCodec.encodeCurrent(secret);
            apiKeyStore.updateKeyHashIfCurrent(
                    apiKey.id(),
                    apiKey.keyHash(),
                    apiKey.keyId(),
                    encodedSecret.hash(),
                    encodedSecret.keyId()
            );
        } catch (Exception ex) {
            // 兼容升级不参与认证正确性；后续成功请求会再次尝试。
            log.debug("upgrade legacy api_key hash failed: id={}, err={}", apiKey.id(), ex.getMessage());
        }
    }

    private static AccountsApiKeyStore.ApiKey withStatus(AccountsApiKeyStore.ApiKey apiKey, String status) {
        return new AccountsApiKeyStore.ApiKey(
                apiKey.id(),
                apiKey.tenantId(),
                apiKey.applicationId(),
                apiKey.name(),
                apiKey.keyHash(),
                apiKey.keyId(),
                status,
                apiKey.lastUsedAt(),
                apiKey.createdAt()
        );
    }

    private static AccountsApiKeyStore.ApiKey withKeyHashAndStatus(
            AccountsApiKeyStore.ApiKey apiKey,
            String keyHash,
            String keyId,
            String status
    ) {
        return new AccountsApiKeyStore.ApiKey(
                apiKey.id(),
                apiKey.tenantId(),
                apiKey.applicationId(),
                apiKey.name(),
                keyHash,
                keyId,
                status,
                apiKey.lastUsedAt(),
                apiKey.createdAt()
        );
    }

    private long authCacheTtlSeconds() {
        long ttlSeconds = 60;
        try {
            if (securityProperties != null && securityProperties.getApiKey() != null) {
                ttlSeconds = securityProperties.getApiKey().getAuthCacheTtlSeconds();
            }
        } catch (Exception ignore) {
        }
        if (ttlSeconds < 0) {
            ttlSeconds = 0;
        }
        return ttlSeconds;
    }

    private void putDisabledAfterCommit(long apiKeyId, long tenantId, Long applicationId, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        runAfterCommit(() -> authCache.putDisabled(apiKeyId, tenantId, applicationId, ttlSeconds));
    }

    private void evictAfterCommit(long apiKeyId) {
        runAfterCommit(() -> authCache.evict(apiKeyId));
    }

    private void runAfterCommit(Runnable action) {
        if (postCommitHookPort == null) {
            action.run();
            return;
        }
        postCommitHookPort.run(action);
    }
}

package com.linkforge.accounts;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.foundation.security.StandardRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApiKeyPersistenceIntegrationTest extends AccountsPersistenceIntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    ApiKeyService apiKeyService;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void apiKeyService_shouldUseApplicationPorts_andPortBeansShouldUseAdapters() {
        assertConstructorUsesTypes(
                ApiKeyService.class,
                "com.linkforge.accounts.application.port.AccountsApiKeyStore",
                "com.linkforge.accounts.application.port.AccountsPasswordHasher",
                "com.linkforge.accounts.application.port.ApiKeyAuthCache"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsApiKeyStore",
                "com.linkforge.accounts.infrastructure.persistence.AccountsApiKeyStoreMybatisAdapter"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsPasswordHasher",
                "com.linkforge.accounts.infrastructure.security.SpringAccountsPasswordHasher"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.ApiKeyAuthCache",
                "com.linkforge.accounts.infrastructure.cache.RedisApiKeyAuthCache"
        );
    }

    @Test
    void apiKeyAuthCache_shouldPersistSerializedPayloads_ttl_andThrottleTokens() {
        Object cache = applicationContext.getBean(loadClass("com.linkforge.accounts.application.port.ApiKeyAuthCache"));

        invoke(cache, "putActive", 301L, 41L, null, "digest-1", 60L);
        assertThat(redis.opsForValue().get("auth:api_key:301")).isEqualTo("v2|41||active|digest-1");
        assertThat(redis.getExpire("auth:api_key:301")).isPositive();
        assertThat(invoke(cache, "read", 301L).toString()).contains("tenantId=41", "status=active", "secretDigest=digest-1");

        invoke(cache, "putDisabled", 301L, 41L, null, 60L);
        assertThat(redis.opsForValue().get("auth:api_key:301")).isEqualTo("v2|41||disabled|");
        assertThat(invoke(cache, "read", 301L).toString()).contains("tenantId=41", "status=disabled");

        Object firstAcquire = invoke(cache, "tryAcquireLastUsedToken", 301L, 300L);
        Object secondAcquire = invoke(cache, "tryAcquireLastUsedToken", 301L, 300L);
        assertThat(firstAcquire.toString()).isEqualTo("ACQUIRED");
        assertThat(secondAcquire.toString()).isEqualTo("NOT_ACQUIRED");
        assertThat(redis.getExpire("auth:api_key:last_used:301")).isPositive();

        invoke(cache, "releaseLastUsedToken", 301L);
        assertThat(redis.opsForValue().get("auth:api_key:last_used:301")).isNull();

        invoke(cache, "evict", 301L);
        assertThat(redis.opsForValue().get("auth:api_key:301")).isNull();
    }

    @Test
    void create_authenticate_rotate_and_list_shouldPersistApiKeys() throws InterruptedException {
        String tenantName = uniqueTenantName();
        String email = uniqueEmail("api-owner");
        String password = "password123";

        AuthService.AuthResult registered = authService.register(tenantName, email, password);
        authenticateAs(registered.principal());

        long applicationId = provisionApplication(registered.principal().getTenantId(), "accounts-api-key-app");

        ApiKeyService.CreatedApiKey firstKey = apiKeyService.create(registered.principal().getTenantId(), applicationId, "first");
        pauseForCreatedAtOrdering();
        ApiKeyService.CreatedApiKey secondKey = apiKeyService.create(registered.principal().getTenantId(), applicationId, "second");

        List<ApiKeyService.ApiKeyInfo> listed = apiKeyService.list(registered.principal().getTenantId());
        assertThat(listed).extracting(ApiKeyService.ApiKeyInfo::id)
                .containsExactly(secondKey.id(), firstKey.id());
        assertThat(listed).extracting(ApiKeyService.ApiKeyInfo::applicationId)
                .containsOnly(applicationId);
        assertThat(listed).extracting(ApiKeyService.ApiKeyInfo::status)
                .containsOnly(AccountsConstants.STATUS_ACTIVE);
        assertThat(listed).allSatisfy(info -> assertThat(info.createdAt()).isNotNull());
        assertThat(listed).filteredOn(info -> info.id() == firstKey.id())
                .singleElement()
                .extracting(ApiKeyService.ApiKeyInfo::lastUsedAt)
                .isNull();

        ApiKeyService.ApiKeyAuthResult authenticated = apiKeyService.authenticate(firstKey.apiKey());
        assertThat(authenticated.tenantId()).isEqualTo(registered.principal().getTenantId());
        assertThat(authenticated.applicationId()).isEqualTo(applicationId);
        assertThat(authenticated.apiKeyId()).isEqualTo(firstKey.id());

        ApiKeyService.CreatedApiKey rotated = apiKeyService.rotate(registered.principal().getTenantId(), firstKey.id());
        assertThat(rotated.id()).isEqualTo(firstKey.id());
        assertThat(rotated.apiKey()).startsWith("lfk_" + firstKey.id() + "_");

        assertThatThrownBy(() -> apiKeyService.authenticate(firstKey.apiKey()))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(throwable -> ((ApiKeyService.ApiKeyAuthException) throwable).errorCode())
                .isEqualTo(com.linkforge.contract.openapi.OpenApiErrorCode.API_KEY_INVALID);

        ApiKeyService.ApiKeyInfo disabled = apiKeyService.disable(registered.principal().getTenantId(), firstKey.id());
        assertThat(disabled.status()).isEqualTo(AccountsConstants.STATUS_DISABLED);

        ApiKeyService.ApiKeyInfo enabled = apiKeyService.enable(registered.principal().getTenantId(), firstKey.id());
        assertThat(enabled.status()).isEqualTo(AccountsConstants.STATUS_ACTIVE);

        ApiKeyService.ApiKeyAuthResult authenticatedRotated = apiKeyService.authenticate(rotated.apiKey());
        assertThat(authenticatedRotated.applicationId()).isEqualTo(applicationId);
        assertThat(authenticatedRotated.apiKeyId()).isEqualTo(firstKey.id());

        List<ApiKeyService.ApiKeyInfo> afterAuth = apiKeyService.list(registered.principal().getTenantId());
        assertThat(afterAuth).filteredOn(info -> info.id() == firstKey.id())
                .singleElement()
                .satisfies(info -> assertThat(info.lastUsedAt()).isNotNull());
        assertThat(registered.principal().getRoles()).containsExactlyInAnyOrder(StandardRoles.TENANT_ADMIN);
    }

    private long provisionApplication(long tenantId, String applicationKey) {
        long applicationId = Math.abs(System.nanoTime()) + 5_000;
        jdbcTemplate.update(
                """
                        INSERT INTO applications (id, tenant_id, application_key, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """,
                applicationId,
                tenantId,
                applicationKey,
                applicationKey
        );
        jdbcTemplate.update(
                """
                        INSERT INTO application_policies (application_id, default_domain_scope, default_redirect_status_code, preview_enabled)
                        VALUES (?, 'TENANT_SHARED', 302, 0)
                        """,
                applicationId
        );
        jdbcTemplate.update(
                """
                        INSERT INTO application_quotas (application_id, monthly_link_limit, monthly_click_limit)
                        VALUES (?, 10000, 1000000)
                        """,
                applicationId
        );
        return applicationId;
    }
}

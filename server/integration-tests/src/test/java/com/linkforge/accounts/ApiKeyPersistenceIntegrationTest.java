package com.linkforge.accounts;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.domain.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void apiKeyService_shouldUseMapperPersistence() {
        assertConstructorUsesMapperTypes(
                ApiKeyService.class,
                "com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper"
        );
    }

    @Test
    void create_authenticate_rotate_and_list_shouldPersistApiKeys() throws InterruptedException {
        String tenantName = uniqueTenantName();
        String email = uniqueEmail("api-owner");
        String password = "password123";

        AuthService.AuthResult registered = authService.register(tenantName, email, password);
        authenticateAs(registered.principal());

        ApiKeyService.CreatedApiKey firstKey = apiKeyService.create(registered.principal().getTenantId(), "first");
        pauseForCreatedAtOrdering();
        ApiKeyService.CreatedApiKey secondKey = apiKeyService.create(registered.principal().getTenantId(), "second");

        List<ApiKeyService.ApiKeyInfo> listed = apiKeyService.list(registered.principal().getTenantId());
        assertThat(listed).extracting(ApiKeyService.ApiKeyInfo::id)
                .containsExactly(secondKey.id(), firstKey.id());
        assertThat(listed).extracting(ApiKeyService.ApiKeyInfo::status)
                .containsOnly(AccountsConstants.STATUS_ACTIVE);
        assertThat(listed).allSatisfy(info -> assertThat(info.createdAt()).isNotNull());
        assertThat(listed).filteredOn(info -> info.id() == firstKey.id())
                .singleElement()
                .extracting(ApiKeyService.ApiKeyInfo::lastUsedAt)
                .isNull();

        ApiKeyService.ApiKeyAuthResult authenticated = apiKeyService.authenticate(firstKey.apiKey());
        assertThat(authenticated.tenantId()).isEqualTo(registered.principal().getTenantId());
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
        assertThat(authenticatedRotated.apiKeyId()).isEqualTo(firstKey.id());

        List<ApiKeyService.ApiKeyInfo> afterAuth = apiKeyService.list(registered.principal().getTenantId());
        assertThat(afterAuth).filteredOn(info -> info.id() == firstKey.id())
                .singleElement()
                .satisfies(info -> assertThat(info.lastUsedAt()).isNotNull());
        assertThat(registered.principal().getRoles()).containsExactlyInAnyOrder(Roles.TENANT_ADMIN);
    }
}

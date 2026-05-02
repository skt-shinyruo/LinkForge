package com.linkforge.accounts;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.application.UserAdminService;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.StandardRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserAdminSafetyIntegrationTest extends AccountsPersistenceIntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    UserAdminService userAdminService;

    @Autowired
    AccountsUserStore userStore;

    @Test
    void disable_shouldRejectSelfDisable_forTenantAdmin() {
        AuthService.AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("owner"), "password123");
        authenticateAs(owner.principal());

        assertThatThrownBy(() -> userAdminService.disable(
                owner.principal().getTenantId(),
                owner.principal().getUserId(),
                owner.principal().getUserId()
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        assertThat(userStore.findById(owner.principal().getUserId()).status()).isEqualTo(AccountsConstants.STATUS_ACTIVE);
    }

    @Test
    void disable_shouldRejectDisablingLastActiveTenantAdmin_evenWhenActorDiffers() {
        AuthService.AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("owner"), "password123");
        authenticateAs(owner.principal());

        UserAdminService.UserDto member = userAdminService.create(
                owner.principal().getTenantId(),
                new UserAdminService.CreateUserRequest(uniqueEmail("member"), "password123", Set.of(StandardRoles.USER))
        );

        assertThatThrownBy(() -> userAdminService.disable(
                owner.principal().getTenantId(),
                member.id(),
                owner.principal().getUserId()
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        assertThat(userStore.findById(owner.principal().getUserId()).status()).isEqualTo(AccountsConstants.STATUS_ACTIVE);
    }

    @Test
    void disable_shouldAllowDisablingTenantAdmin_whenAnotherActiveTenantAdminRemains() {
        AuthService.AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("owner"), "password123");
        authenticateAs(owner.principal());

        UserAdminService.UserDto secondAdmin = userAdminService.create(
                owner.principal().getTenantId(),
                new UserAdminService.CreateUserRequest(uniqueEmail("admin"), "password123", Set.of(StandardRoles.TENANT_ADMIN))
        );

        UserAdminService.UserDto disabled = userAdminService.disable(
                owner.principal().getTenantId(),
                owner.principal().getUserId(),
                secondAdmin.id()
        );

        assertThat(disabled.status()).isEqualTo(AccountsConstants.STATUS_DISABLED);
        assertThat(userStore.findById(secondAdmin.id()).status()).isEqualTo(AccountsConstants.STATUS_DISABLED);
    }
}

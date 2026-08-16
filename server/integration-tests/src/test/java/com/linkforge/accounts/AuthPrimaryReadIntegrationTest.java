package com.linkforge.accounts;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.AuthResult;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.application.UserAdminService;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.testsupport.SharedReadWriteIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class AuthPrimaryReadIntegrationTest extends SharedReadWriteIntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    UserAdminService userAdminService;

    @Autowired
    AccountsPasswordHasher passwordHasher;

    @Test
    void resetPassword_shouldBeVisibleToImmediateLogin_whileReplicaRetainsOldHash() {
        long suffix = System.nanoTime();
        long tenantId = suffix + 100L;
        long userId = suffix + 101L;
        String email = "primary-reset-" + suffix + "@example.test";
        String oldPassword = "old-password123";
        String newPassword = "new-password123";
        String oldHash = passwordHasher.encode(oldPassword);
        insertTenantAndUser(jdbc(PRIMARY), tenantId, userId, email, oldHash, "active", StandardRoles.USER);
        insertTenantAndUser(jdbc(REPLICA), tenantId, userId, email, oldHash, "active", StandardRoles.USER);

        userAdminService.resetPassword(tenantId, userId, newPassword);

        AuthResult loggedIn = authService.login(email, newPassword);
        assertThat(loggedIn.principal().getTokenVersion()).isEqualTo(1);
        assertInvalidCredentials(() -> authService.login(email, oldPassword));
    }

    @Test
    void disable_shouldBeVisibleToImmediateLogin_whileReplicaRetainsActiveUser() {
        long suffix = System.nanoTime();
        long tenantId = suffix + 200L;
        long ownerId = suffix + 201L;
        long memberId = suffix + 202L;
        String ownerEmail = "primary-owner-" + suffix + "@example.test";
        String memberEmail = "primary-member-" + suffix + "@example.test";
        String password = "password123";
        String passwordHash = passwordHasher.encode(password);
        for (JdbcTemplate jdbc : List.of(jdbc(PRIMARY), jdbc(REPLICA))) {
            insertTenant(jdbc, tenantId);
            insertUser(jdbc, tenantId, ownerId, ownerEmail, passwordHash, "active", StandardRoles.TENANT_ADMIN);
            insertUser(jdbc, tenantId, memberId, memberEmail, passwordHash, "active", StandardRoles.USER);
        }

        userAdminService.disable(tenantId, ownerId, memberId);

        assertInvalidCredentials(() -> authService.login(memberEmail, password));
    }

    private static void assertInvalidCredentials(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AccountsErrorCode.INVALID_CREDENTIALS));
    }

    private static JdbcTemplate jdbc(MySQLContainer<?> mysql) {
        return new JdbcTemplate(new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
        ));
    }

    private static void insertTenantAndUser(
            JdbcTemplate jdbc,
            long tenantId,
            long userId,
            String email,
            String passwordHash,
            String status,
            String role
    ) {
        insertTenant(jdbc, tenantId);
        insertUser(jdbc, tenantId, userId, email, passwordHash, status, role);
    }

    private static void insertTenant(JdbcTemplate jdbc, long tenantId) {
        jdbc.update(
                "INSERT INTO tenants (id, name, status) VALUES (?, ?, 'active')",
                tenantId,
                "tenant-" + tenantId
        );
    }

    private static void insertUser(
            JdbcTemplate jdbc,
            long tenantId,
            long userId,
            String email,
            String passwordHash,
            String status,
            String role
    ) {
        jdbc.update(
                "INSERT INTO users (id, tenant_id, email, password_hash, status, token_version) VALUES (?, ?, ?, ?, ?, 0)",
                userId, tenantId, email, passwordHash, status
        );
        jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", userId, role);
    }
}

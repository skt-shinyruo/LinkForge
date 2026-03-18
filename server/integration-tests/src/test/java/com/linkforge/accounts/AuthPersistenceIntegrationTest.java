package com.linkforge.accounts;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.application.UserAdminService;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleId;
import com.linkforge.foundation.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AuthPersistenceIntegrationTest extends AccountsPersistenceIntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    UserAdminService userAdminService;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void accountsServices_shouldDependOnApplicationPorts_andPortBeansShouldUseAdapters() {
        assertConstructorUsesTypes(
                AuthService.class,
                "com.linkforge.accounts.application.port.AccountsTenantStore",
                "com.linkforge.accounts.application.port.AccountsUserStore",
                "com.linkforge.accounts.application.port.AccountsUserRoleStore",
                "com.linkforge.accounts.application.port.AccountsTokenIssuer"
        );
        assertConstructorUsesTypes(
                UserAdminService.class,
                "com.linkforge.accounts.application.port.AccountsUserStore",
                "com.linkforge.accounts.application.port.AccountsUserRoleStore"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsTenantStore",
                "com.linkforge.accounts.infrastructure.persistence.AccountsTenantStoreMybatisAdapter"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsUserStore",
                "com.linkforge.accounts.infrastructure.persistence.AccountsUserStoreMybatisAdapter"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsUserRoleStore",
                "com.linkforge.accounts.infrastructure.persistence.AccountsUserRoleStoreMybatisAdapter"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsApiKeyStore",
                "com.linkforge.accounts.infrastructure.persistence.AccountsApiKeyStoreMybatisAdapter"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsTokenIssuer",
                "com.linkforge.accounts.infrastructure.security.AccountsJwtTokenIssuer"
        );

        assertRepositoryClassRemoved("com.linkforge.accounts.infrastructure.persistence.repo.TenantRepository");
        assertRepositoryClassRemoved("com.linkforge.accounts.infrastructure.persistence.repo.UserRepository");
        assertRepositoryClassRemoved("com.linkforge.accounts.infrastructure.persistence.repo.UserRoleRepository");
        assertRepositoryClassRemoved("com.linkforge.accounts.infrastructure.persistence.repo.ApiKeyRepository");

        assertHasNoJakartaPersistenceAnnotations(TenantEntity.class);
        assertHasNoJakartaPersistenceAnnotations(UserEntity.class);
        assertHasNoJakartaPersistenceAnnotations(UserRoleEntity.class);
        assertHasNoJakartaPersistenceAnnotations(UserRoleId.class);
        assertHasNoJakartaPersistenceAnnotations(ApiKeyEntity.class);
    }

    @Test
    void register_login_and_user_admin_list_shouldPersistTenantUserAndRoles() throws InterruptedException {
        String tenantName = uniqueTenantName();
        String ownerEmail = uniqueEmail("owner");
        String memberEmail = uniqueEmail("member");
        String password = "password123";

        AuthService.AuthResult registered = authService.register(tenantName, ownerEmail, password);
        long tenantId = registered.principal().getTenantId();

        assertThat(registered.principal().getEmail()).isEqualTo(ownerEmail);
        assertThat(registered.principal().getRoles()).containsExactlyInAnyOrder(Roles.TENANT_ADMIN);

        AuthService.AuthResult loggedIn = authService.login(ownerEmail, password);
        assertThat(loggedIn.principal().getTenantId()).isEqualTo(tenantId);
        assertThat(loggedIn.principal().getRoles()).containsExactlyInAnyOrder(Roles.TENANT_ADMIN);

        authenticateAs(loggedIn.principal());
        pauseForCreatedAtOrdering();

        UserAdminService.UserDto createdUser = userAdminService.create(
                tenantId,
                new UserAdminService.CreateUserRequest(memberEmail, password, Set.of())
        );

        assertThat(createdUser.tenantId()).isEqualTo(tenantId);
        assertThat(createdUser.email()).isEqualTo(memberEmail);
        assertThat(createdUser.roles()).containsExactlyInAnyOrder(Roles.USER);

        List<UserAdminService.UserDto> users = userAdminService.list(tenantId);

        assertThat(users).hasSize(2);
        assertThat(users).extracting(UserAdminService.UserDto::email)
                .containsExactly(memberEmail, ownerEmail);
        assertThat(users.get(0).roles()).containsExactlyInAnyOrder(Roles.USER);
        assertThat(users.get(1).roles()).containsExactlyInAnyOrder(Roles.TENANT_ADMIN);
    }
}

abstract class AccountsPersistenceIntegrationTestSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.4-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)))
            .withStartupAttempts(3);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("app.security.jwt.secret", () -> "test-secret-please-change-but-long-enough-32-bytes");
        registry.add("app.analytics.salt", () -> "test-analytics-salt");
        registry.add("app.analytics.dimensions.enabled", () -> "false");
        registry.add("app.analytics.events.enabled", () -> "false");
        registry.add("app.analytics.events.sample-rate", () -> "1");
        registry.add("APP_ANALYTICS_EVENT_INGEST_DELAY_MS", () -> "9999999");
        registry.add("APP_ANALYTICS_EVENT_RETENTION_DELAY_MS", () -> "9999999");
        registry.add("APP_ANALYTICS_DIM_FLUSH_DELAY_MS", () -> "9999999");
        registry.add("APP_ANALYTICS_FLUSH_DELAY_MS", () -> "9999999");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected static void authenticateAs(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }

    protected static void pauseForCreatedAtOrdering() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(1100);
    }

    protected static String uniqueTenantName() {
        return "tenant-" + UUID.randomUUID();
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected static void assertConstructorUsesTypes(Class<?> targetType, String... expectedTypeNames) {
        Constructor<?> constructor = Arrays.stream(targetType.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains(expectedTypeNames)
                .noneMatch(typeName -> typeName.contains(".persistence.repo."))
                .noneMatch(typeName -> typeName.contains(".infrastructure.persistence.mapper."))
                .noneMatch(typeName -> typeName.contains(".infrastructure.persistence.entity."))
                .noneMatch(typeName -> typeName.contains(".infrastructure.security.JwtService"));
    }

    protected static void assertConstructorUsesMapperTypes(Class<?> targetType, String... expectedTypeNames) {
        assertConstructorUsesTypes(targetType, expectedTypeNames);
    }

    protected static void assertHasNoJakartaPersistenceAnnotations(Class<?> targetType) {
        List<String> annotationTypeNames = Stream.concat(
                        Arrays.stream(targetType.getDeclaredAnnotations()),
                        Arrays.stream(targetType.getDeclaredFields())
                                .flatMap(AccountsPersistenceIntegrationTestSupport::fieldAnnotations)
                )
                .map(annotation -> annotation.annotationType().getName())
                .toList();

        assertThat(annotationTypeNames)
                .noneMatch(typeName -> typeName.startsWith("jakarta.persistence."));
    }

    protected static void assertDirectMapperBean(ApplicationContext applicationContext, Class<?> mapperType) {
        assertThat(applicationContext.getBean(mapperType).getClass().getName())
                .doesNotContain(".persistence.repo.");
    }

    protected static void assertPortBean(ApplicationContext applicationContext, String portTypeName, String adapterTypeName) {
        assertThat(applicationContext.getBean(loadClass(portTypeName)).getClass())
                .isAssignableTo(loadClass(adapterTypeName));
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Expected class to exist: " + className, e);
        }
    }

    protected static void assertRepositoryClassRemoved(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static Stream<Annotation> fieldAnnotations(Field field) {
        return Arrays.stream(field.getDeclaredAnnotations());
    }
}

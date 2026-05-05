package com.linkforge.accounts;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.AuthResult;
import com.linkforge.accounts.application.CreateUserCommand;
import com.linkforge.accounts.application.UserResult;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.application.UserAdminService;
import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleId;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.StandardRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthPersistenceIntegrationTest extends AccountsPersistenceIntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    UserAdminService userAdminService;

    @Autowired
    AccountsUserStore userStore;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void accountsServices_shouldDependOnApplicationPorts_andPortBeansShouldUseAdapters() {
        assertConstructorUsesTypes(
                AuthService.class,
                "com.linkforge.accounts.application.port.AccountsTenantStore",
                "com.linkforge.accounts.application.port.AccountsUserStore",
                "com.linkforge.accounts.application.port.AccountsUserRoleStore",
                "com.linkforge.accounts.application.port.AccountsPasswordHasher",
                "com.linkforge.accounts.application.port.AccountsTokenIssuer",
                "com.linkforge.accounts.application.port.AccountStatusCache"
        );
        assertConstructorUsesTypes(
                UserAdminService.class,
                "com.linkforge.accounts.application.port.AccountsUserStore",
                "com.linkforge.accounts.application.port.AccountsUserRoleStore",
                "com.linkforge.accounts.application.port.AccountsPasswordHasher",
                "com.linkforge.accounts.application.port.AccountStatusCache"
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
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountsPasswordHasher",
                "com.linkforge.accounts.infrastructure.security.SpringAccountsPasswordHasher"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.AccountStatusCache",
                "com.linkforge.accounts.infrastructure.cache.RedisAccountStatusCache"
        );
        assertPortBean(
                applicationContext,
                "com.linkforge.accounts.application.port.ApiKeyAuthCache",
                "com.linkforge.accounts.infrastructure.cache.RedisApiKeyAuthCache"
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
    void accountStatusCache_shouldPersistTenantAndUserAuthState_withExpectedKeysAndTtl() {
        AccountStatusCache cache = applicationContext.getBean(AccountStatusCache.class);

        cache.writeTenantStatus(101L, "active", Duration.ofSeconds(30));
        cache.writeUserAuthState(202L, 101L, "disabled", 7, Duration.ofSeconds(30));

        assertThat(redis.opsForValue().get("auth:tenant_status:101")).isEqualTo("active");
        assertThat(redis.opsForValue().get("auth:user_status:202")).isEqualTo("v1|101|disabled|7");
        assertThat(redis.getExpire("auth:tenant_status:101")).isPositive();
        assertThat(redis.getExpire("auth:user_status:202")).isPositive();
        assertThat(cache.readTenantStatus(101L)).isEqualTo("active");
        assertThat(cache.readUserAuthState(202L))
                .isEqualTo(new AccountStatusCache.UserAuthState(101L, "disabled", 7));
    }

    @Test
    void jwtService_shouldNotDependOnAccountsUserStore_forTokenParsing() {
        Constructor<?> constructor = Arrays.stream(JwtService.class.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .doesNotContain("com.linkforge.accounts.application.port.AccountsUserStore");
    }

    @Test
    void register_login_and_user_admin_list_shouldPersistTenantUserAndRoles() throws InterruptedException {
        String tenantName = uniqueTenantName();
        String ownerEmail = uniqueEmail("owner");
        String memberEmail = uniqueEmail("member");
        String password = "password123";

        AuthResult registered = authService.register(tenantName, ownerEmail, password);
        long tenantId = registered.principal().getTenantId();

        assertThat(registered.principal().getEmail()).isEqualTo(ownerEmail);
        assertThat(registered.principal().getRoles()).containsExactlyInAnyOrder(StandardRoles.TENANT_ADMIN);

        AuthResult loggedIn = authService.login(ownerEmail, password);
        assertThat(loggedIn.principal().getTenantId()).isEqualTo(tenantId);
        assertThat(loggedIn.principal().getRoles()).containsExactlyInAnyOrder(StandardRoles.TENANT_ADMIN);

        authenticateAs(loggedIn.principal());
        pauseForCreatedAtOrdering();

        UserResult createdUser = userAdminService.create(
                tenantId,
                new CreateUserCommand(memberEmail, password, Set.of())
        );

        assertThat(createdUser.tenantId()).isEqualTo(tenantId);
        assertThat(createdUser.email()).isEqualTo(memberEmail);
        assertThat(createdUser.roles()).containsExactlyInAnyOrder(StandardRoles.USER);

        List<UserResult> users = userAdminService.list(tenantId);

        assertThat(users).hasSize(2);
        assertThat(users).extracting(UserResult::email)
                .containsExactly(memberEmail, ownerEmail);
        assertThat(users.get(0).roles()).containsExactlyInAnyOrder(StandardRoles.USER);
        assertThat(users.get(1).roles()).containsExactlyInAnyOrder(StandardRoles.TENANT_ADMIN);
    }

    @Test
    void disable_should_invalidate_cached_active_user_immediately() throws Exception {
        String tenantName = uniqueTenantName();
        String ownerEmail = uniqueEmail("owner");
        String memberEmail = uniqueEmail("member");
        String password = "password123";

        AuthResult owner = authService.register(tenantName, ownerEmail, password);
        long tenantId = owner.principal().getTenantId();

        authenticateAs(owner.principal());
        UserResult member = userAdminService.create(
                tenantId,
                new CreateUserCommand(memberEmail, password, Set.of())
        );

        AuthResult memberLogin = authService.login(memberEmail, password);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + memberLogin.token()))
                .andExpect(status().isOk());

        authenticateAs(owner.principal());
        userAdminService.disable(tenantId, owner.principal().getUserId(), member.id());

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + memberLogin.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void enable_should_clear_cached_disabled_user_immediately() throws Exception {
        String tenantName = uniqueTenantName();
        String ownerEmail = uniqueEmail("owner");
        String memberEmail = uniqueEmail("member");
        String password = "password123";

        AuthResult owner = authService.register(tenantName, ownerEmail, password);
        long tenantId = owner.principal().getTenantId();

        authenticateAs(owner.principal());
        UserResult member = userAdminService.create(
                tenantId,
                new CreateUserCommand(memberEmail, password, Set.of())
        );
        AuthResult memberLogin = authService.login(memberEmail, password);

        userAdminService.disable(tenantId, owner.principal().getUserId(), member.id());

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + memberLogin.token()))
                .andExpect(status().isForbidden());

        authenticateAs(owner.principal());
        userAdminService.enable(tenantId, member.id());

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + memberLogin.token()))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_should_invalidate_cached_active_user_immediately_andPersistTokenVersion() throws Exception {
        String tenantName = uniqueTenantName();
        String ownerEmail = uniqueEmail("owner");
        String oldPassword = "password123";
        String newPassword = "new-password123";

        AuthResult registered = authService.register(tenantName, ownerEmail, oldPassword);
        long tenantId = registered.principal().getTenantId();
        long userId = registered.principal().getUserId();

        assertThat(registered.principal().getTokenVersion()).isZero();
        assertThat(userStore.findById(userId).tokenVersion()).isZero();

        String userCacheKey = "auth:user_status:" + userId;
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + registered.token()))
                .andExpect(status().isOk());
        String cachedBeforeReset = redis.opsForValue().get(userCacheKey);
        assertThat(cachedBeforeReset).isNotBlank();

        authenticateAs(registered.principal());
        userAdminService.resetPassword(tenantId, userId, newPassword);

        assertThat(userStore.findById(userId).tokenVersion()).isEqualTo(1);
        assertThat(redis.opsForValue().get(userCacheKey)).isNotEqualTo(cachedBeforeReset);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + registered.token()))
                .andExpect(status().isUnauthorized());

        AuthResult loggedIn = authService.login(ownerEmail, newPassword);
        assertThat(loggedIn.principal().getTokenVersion()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + loggedIn.token()))
                .andExpect(status().isOk());
    }

    @Test
    void logout_should_invalidate_cached_active_user_immediately() throws Exception {
        String tenantName = uniqueTenantName();
        String ownerEmail = uniqueEmail("owner");
        String password = "password123";

        AuthResult registered = authService.register(tenantName, ownerEmail, password);
        long userId = registered.principal().getUserId();
        String userCacheKey = "auth:user_status:" + userId;

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + registered.token()))
                .andExpect(status().isOk());
        String cachedBeforeLogout = redis.opsForValue().get(userCacheKey);
        assertThat(cachedBeforeLogout).isNotBlank();

        authService.logout(userId);

        assertThat(userStore.findById(userId).tokenVersion()).isEqualTo(1);
        assertThat(redis.opsForValue().get(userCacheKey)).isNotEqualTo(cachedBeforeLogout);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + registered.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operationalForeignKeys_shouldCoverOnlyLiveOperationalTables() {
        List<ForeignKeyRef> refs = jdbcTemplate.query(
                """
                        SELECT table_name, column_name, referenced_table_name, referenced_column_name
                        FROM information_schema.KEY_COLUMN_USAGE
                        WHERE table_schema = DATABASE()
                          AND referenced_table_name IS NOT NULL
                        ORDER BY table_name, column_name
                        """,
                (rs, rowNum) -> new ForeignKeyRef(
                        rs.getString("table_name"),
                        rs.getString("column_name"),
                        rs.getString("referenced_table_name"),
                        rs.getString("referenced_column_name")
                )
        );

        assertThat(refs).containsExactlyInAnyOrder(
                new ForeignKeyRef("users", "tenant_id", "tenants", "id"),
                new ForeignKeyRef("api_keys", "tenant_id", "tenants", "id"),
                new ForeignKeyRef("short_links", "tenant_id", "tenants", "id"),
                new ForeignKeyRef("tags", "tenant_id", "tenants", "id"),
                new ForeignKeyRef("user_roles", "user_id", "users", "id"),
                new ForeignKeyRef("link_tags", "link_id", "short_links", "id"),
                new ForeignKeyRef("link_tags", "tag_id", "tags", "id")
        );
    }

    private record ForeignKeyRef(String tableName, String columnName, String referencedTableName, String referencedColumnName) {
    }

}

abstract class AccountsPersistenceIntegrationTestSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6.2-alpine")
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

    protected static Object invoke(Object target, String methodName, Object... args) {
        try {
            Class<?>[] argTypes = Arrays.stream(args)
                    .map(arg -> arg == null ? Object.class : arg.getClass())
                    .toArray(Class<?>[]::new);
            return Arrays.stream(target.getClass().getMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .filter(method -> method.getParameterCount() == argTypes.length)
                    .findFirst()
                    .orElseThrow()
                    .invoke(target, args);
        } catch (Exception e) {
            throw new AssertionError("Failed to invoke method " + methodName + " on " + target.getClass().getName(), e);
        }
    }

    protected static Class<?> loadClass(String className) {
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

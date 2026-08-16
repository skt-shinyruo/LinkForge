package com.linkforge.accounts;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.AuthResult;
import com.linkforge.accounts.application.AccountStatusService;
import com.linkforge.accounts.application.CreateUserCommand;
import com.linkforge.accounts.application.UserResult;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.application.UserAdminService;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.AccountsConstants;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.StandardRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserAdminSafetyIntegrationTest extends AccountsPersistenceIntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    UserAdminService userAdminService;

    @Autowired
    AccountsUserStore userStore;

    @Autowired
    AccountsTenantStore tenantStore;

    @Autowired
    AccountStatusService accountStatusService;

    @Autowired
    AccountStatusCache accountStatusCache;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void disable_shouldRejectSelfDisable_forTenantAdmin() {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("owner"), "password123");
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
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("owner"), "password123");
        authenticateAs(owner.principal());

        UserResult member = userAdminService.create(
                owner.principal().getTenantId(),
                new CreateUserCommand(uniqueEmail("member"), "password123", Set.of(StandardRoles.USER))
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
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("owner"), "password123");
        authenticateAs(owner.principal());

        UserResult secondAdmin = userAdminService.create(
                owner.principal().getTenantId(),
                new CreateUserCommand(uniqueEmail("admin"), "password123", Set.of(StandardRoles.TENANT_ADMIN))
        );

        UserResult disabled = userAdminService.disable(
                owner.principal().getTenantId(),
                owner.principal().getUserId(),
                secondAdmin.id()
        );

        assertThat(disabled.status()).isEqualTo(AccountsConstants.STATUS_DISABLED);
        assertThat(userStore.findById(secondAdmin.id()).status()).isEqualTo(AccountsConstants.STATUS_DISABLED);
    }

    @Test
    void concurrentLogout_shouldPreserveEveryCommittedTokenVersionAdvance() throws Exception {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("logout-owner"), "password123");

        runConcurrently(
                () -> {
                    authService.logout(owner.principal().getUserId());
                    return null;
                },
                () -> {
                    authService.logout(owner.principal().getUserId());
                    return null;
                }
        );

        assertThat(userStore.findById(owner.principal().getUserId()).tokenVersion()).isEqualTo(2);
    }

    @Test
    void concurrentPasswordReset_shouldPreserveEveryAdvance_andOneCommittedPassword() throws Exception {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("reset-owner"), "password123");
        long tenantId = owner.principal().getTenantId();
        long userId = owner.principal().getUserId();
        String firstPassword = "first-new-password";
        String secondPassword = "second-new-password";

        runConcurrently(
                () -> userAdminService.resetPassword(tenantId, userId, firstPassword),
                () -> userAdminService.resetPassword(tenantId, userId, secondPassword)
        );

        assertThat(userStore.findById(userId).tokenVersion()).isEqualTo(2);
        AtomicInteger successfulPasswords = new AtomicInteger();
        for (String candidate : Set.of(firstPassword, secondPassword)) {
            try {
                authService.login(owner.principal().getEmail(), candidate);
                successfulPasswords.incrementAndGet();
            } catch (BusinessException ignored) {
                // 只有最后提交的事务所写密码仍是当前凭据。
            }
        }
        assertThat(successfulPasswords).hasValue(1);
    }

    @Test
    void concurrentLogoutResetAndDisable_shouldNotOverwriteSecurityFields() throws Exception {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("mixed-owner"), "password123");
        UserResult member = userAdminService.create(
                owner.principal().getTenantId(),
                new CreateUserCommand(uniqueEmail("mixed-member"), "password123", Set.of(StandardRoles.USER))
        );
        String newPassword = "mixed-new-password";

        runConcurrently(
                () -> {
                    authService.logout(member.id());
                    return null;
                },
                () -> userAdminService.resetPassword(owner.principal().getTenantId(), member.id(), newPassword),
                () -> userAdminService.disable(owner.principal().getTenantId(), owner.principal().getUserId(), member.id())
        );

        AccountsUserStore.UserData finalUser = userStore.findById(member.id());
        assertThat(finalUser.tokenVersion()).isEqualTo(2);
        assertThat(finalUser.status()).isEqualTo(AccountsConstants.STATUS_DISABLED);

        userAdminService.enable(owner.principal().getTenantId(), member.id());
        assertThat(authService.login(member.email(), newPassword).principal().getTokenVersion()).isEqualTo(2);
    }

    @Test
    void concurrentEnableAndDisable_shouldOnlyRaceOnStatus() throws Exception {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("status-owner"), "password123");
        UserResult member = userAdminService.create(
                owner.principal().getTenantId(),
                new CreateUserCommand(uniqueEmail("status-member"), "password123", Set.of(StandardRoles.USER))
        );
        AccountsUserStore.UserData before = userStore.findById(member.id());

        runConcurrently(
                () -> userAdminService.enable(owner.principal().getTenantId(), member.id()),
                () -> userAdminService.disable(owner.principal().getTenantId(), owner.principal().getUserId(), member.id())
        );

        AccountsUserStore.UserData after = userStore.findById(member.id());
        assertThat(after.status()).isIn(AccountsConstants.STATUS_ACTIVE, AccountsConstants.STATUS_DISABLED);
        assertThat(after.passwordHash()).isEqualTo(before.passwordHash());
        assertThat(after.tokenVersion()).isEqualTo(before.tokenVersion());
    }

    @Test
    void preCommitCacheMiss_shouldNotLeaveOldTokenVersionCachedAfterLogoutCommits() {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("cache-owner"), "password123");
        UserResult member = userAdminService.create(
                owner.principal().getTenantId(),
                new CreateUserCommand(uniqueEmail("cache-member"), "password123", Set.of(StandardRoles.USER))
        );
        accountStatusCache.evictUserStatus(member.id());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            authService.logout(member.id());
            CompletableFuture.runAsync(() -> accountStatusService.requireActiveUserAndTenant(
                    member.id(),
                    owner.principal().getTenantId(),
                    0
            )).join();
            assertThat(accountStatusCache.readUserAuthState(member.id()))
                    .isEqualTo(new AccountStatusCache.UserAuthState(
                            owner.principal().getTenantId(),
                            AccountsConstants.STATUS_ACTIVE,
                            0
                    ));
        });

        assertThat(accountStatusCache.readUserAuthState(member.id())).isNull();
        assertThatThrownBy(() -> accountStatusService.requireActiveUserAndTenant(
                member.id(),
                owner.principal().getTenantId(),
                0
        )).isInstanceOf(BusinessException.class);
        assertThat(userStore.findById(member.id()).tokenVersion()).isEqualTo(1);
    }

    @Test
    void cacheMissThatFinishesAfterLogoutCommit_shouldBeRejectedByGenerationFence() throws Exception {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("late-cache-owner"), "password123");
        UserResult member = userAdminService.create(
                owner.principal().getTenantId(),
                new CreateUserCommand(uniqueEmail("late-cache-member"), "password123", Set.of(StandardRoles.USER))
        );
        accountStatusService.requireActiveTenant(owner.principal().getTenantId());
        assertThat(accountStatusCache.evictUserStatus(member.id())).isTrue();

        CountDownLatch writeReached = new CountDownLatch(1);
        CountDownLatch allowWrite = new CountDownLatch(1);
        AccountStatusService delayedMissService = new AccountStatusService(
                tenantStore,
                userStore,
                new BlockingUserWriteAccountStatusCache(accountStatusCache, writeReached, allowWrite)
        );

        CompletableFuture<Void> miss = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        delayedMissService.requireActiveUserAndTenant(
                                member.id(), owner.principal().getTenantId(), 0
                        )
                )
        );

        assertThat(writeReached.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            authService.logout(member.id());
        } finally {
            allowWrite.countDown();
        }
        miss.get(10, TimeUnit.SECONDS);

        assertThat(accountStatusCache.readUserAuthState(member.id())).isNull();
        assertThatThrownBy(() -> accountStatusService.requireActiveUserAndTenant(
                member.id(), owner.principal().getTenantId(), 0
        )).isInstanceOf(BusinessException.class);
        assertThat(userStore.findById(member.id()).tokenVersion()).isEqualTo(1);
    }

    @Test
    void concurrentMutualAdminDisable_shouldLeaveOneActiveAdmin() throws Exception {
        AuthResult owner = authService.register(uniqueTenantName(), uniqueEmail("race-owner"), "password123");
        UserResult secondAdmin = userAdminService.create(
                owner.principal().getTenantId(),
                new CreateUserCommand(uniqueEmail("race-admin"), "password123", Set.of(StandardRoles.TENANT_ADMIN))
        );
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger stableRejections = new AtomicInteger();

        runConcurrently(
                () -> {
                    recordDisableOutcome(
                            owner.principal().getTenantId(), owner.principal().getUserId(), secondAdmin.id(),
                            successes, stableRejections
                    );
                    return null;
                },
                () -> {
                    recordDisableOutcome(
                            owner.principal().getTenantId(), secondAdmin.id(), owner.principal().getUserId(),
                            successes, stableRejections
                    );
                    return null;
                }
        );

        assertThat(successes).hasValue(1);
        assertThat(stableRejections).hasValue(1);
        long activeAdmins = userAdminService.list(owner.principal().getTenantId()).stream()
                .filter(user -> AccountsConstants.STATUS_ACTIVE.equals(user.status()))
                .filter(user -> user.roles().contains(StandardRoles.TENANT_ADMIN))
                .count();
        assertThat(activeAdmins).isEqualTo(1);
    }

    private void recordDisableOutcome(
            long tenantId,
            long actorUserId,
            long targetUserId,
            AtomicInteger successes,
            AtomicInteger stableRejections
    ) {
        try {
            userAdminService.disable(tenantId, actorUserId, targetUserId);
            successes.incrementAndGet();
        } catch (BusinessException ex) {
            if (ErrorCode.BAD_REQUEST.equals(ex.getErrorCode())) {
                stableRejections.incrementAndGet();
                return;
            }
            throw ex;
        }
    }

    private static final class BlockingUserWriteAccountStatusCache implements AccountStatusCache {
        private final AccountStatusCache delegate;
        private final CountDownLatch writeReached;
        private final CountDownLatch allowWrite;

        private BlockingUserWriteAccountStatusCache(
                AccountStatusCache delegate,
                CountDownLatch writeReached,
                CountDownLatch allowWrite
        ) {
            this.delegate = delegate;
            this.writeReached = writeReached;
            this.allowWrite = allowWrite;
        }

        @Override
        public String readTenantStatus(long tenantId) {
            return delegate.readTenantStatus(tenantId);
        }

        @Override
        public UserAuthState readUserAuthState(long userId) {
            return delegate.readUserAuthState(userId);
        }

        @Override
        public Long readTenantGeneration(long tenantId) {
            return delegate.readTenantGeneration(tenantId);
        }

        @Override
        public Long readUserGeneration(long userId) {
            return delegate.readUserGeneration(userId);
        }

        @Override
        public boolean writeTenantStatusIfGenerationMatches(
                long tenantId,
                long expectedGeneration,
                String status,
                Duration ttl
        ) {
            return delegate.writeTenantStatusIfGenerationMatches(tenantId, expectedGeneration, status, ttl);
        }

        @Override
        public boolean writeUserAuthStateIfGenerationMatches(
                long userId,
                long expectedGeneration,
                long tenantId,
                String status,
                int tokenVersion,
                Duration ttl
        ) {
            writeReached.countDown();
            try {
                if (!allowWrite.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("cache write was not released in time");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting to write cache", ex);
            }
            return delegate.writeUserAuthStateIfGenerationMatches(
                    userId, expectedGeneration, tenantId, status, tokenVersion, ttl
            );
        }

        @Override
        public boolean evictTenantStatus(long tenantId) {
            return delegate.evictTenantStatus(tenantId);
        }

        @Override
        public boolean evictUserStatus(long userId) {
            return delegate.evictUserStatus(userId);
        }
    }

    @SafeVarargs
    private static void runConcurrently(Callable<?>... operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.length);
        CountDownLatch ready = new CountDownLatch(operations.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[operations.length];
            for (int i = 0; i < operations.length; i++) {
                Callable<?> operation = operations[i];
                futures[i] = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("concurrent operation did not start in time");
                    }
                    return operation.call();
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

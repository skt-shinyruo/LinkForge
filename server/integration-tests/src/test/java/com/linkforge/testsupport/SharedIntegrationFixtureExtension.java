package com.linkforge.testsupport;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.locks.ReentrantLock;

/** 串行化 opt-in 测试，并在每个测试边界前后重置共享数据库与 Redis。 */
public final class SharedIntegrationFixtureExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(SharedIntegrationFixtureExtension.class);
    private static final String LOCK_HELD = "lock-held";
    private static final ReentrantLock FIXTURE_LOCK = new ReentrantLock(true);

    @Override
    public void beforeEach(ExtensionContext context) {
        FIXTURE_LOCK.lock();
        try {
            SharedIntegrationTopology.resetFixtures();
            context.getStore(NAMESPACE).put(LOCK_HELD, Boolean.TRUE);
        } catch (RuntimeException ex) {
            FIXTURE_LOCK.unlock();
            throw ex;
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Boolean lockHeld = context.getStore(NAMESPACE).remove(LOCK_HELD, Boolean.class);
        if (!Boolean.TRUE.equals(lockHeld)) {
            return;
        }
        try {
            SharedIntegrationTopology.resetFixtures();
        } finally {
            FIXTURE_LOCK.unlock();
        }
    }
}

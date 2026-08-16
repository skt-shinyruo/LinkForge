package com.linkforge.platform.application.port;

/** Platform 自有的 legacy binding 租户级事务锁。 */
public interface LegacyBindingLockRepository {

    /**
     * 确保锁行存在并持有至当前事务结束；同一租户的调用必须串行通过该方法。
     */
    void lockTenant(long tenantId);
}

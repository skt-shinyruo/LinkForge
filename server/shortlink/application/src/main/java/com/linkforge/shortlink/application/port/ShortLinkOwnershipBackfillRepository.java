package com.linkforge.shortlink.application.port;

/**
 * 为历史无 scope 短链回填应用与域名归属的持久化端口。
 *
 * <p>实现只应更新指定租户中 {@code applicationId} 与 {@code domainId} 均未绑定的历史记录，不得覆盖
 * 已有任一归属。相同参数重复执行应自然收敛为零行更新，便于迁移任务安全重试。</p>
 */
public interface ShortLinkOwnershipBackfillRepository {

    /**
     * 将租户的历史无 scope 短链绑定到给定 Legacy 应用和域名。
     *
     * @return 本次实际回填的行数
     */
    int backfillTenant(long tenantId, long applicationId, long domainId);
}

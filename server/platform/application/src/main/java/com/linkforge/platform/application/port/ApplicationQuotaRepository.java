package com.linkforge.platform.application.port;

import com.linkforge.platform.domain.ApplicationQuota;

import java.util.Optional;

/**
 * 应用月度额度配置的持久化端口。
 *
 * <p>每个应用至多一行额度配置，事务由调用它的应用服务管理。该端口只保存配置上限，
 * 不负责统计实时用量，也不定义超额时的 fail-open/fail-closed 策略。</p>
 */
public interface ApplicationQuotaRepository {

    /**
     * 插入应用额度；同一应用重复插入时传播唯一约束异常。
     */
    void insert(ApplicationQuota quota);

    /**
     * 查询应用显式额度；缺失时返回空值，不在仓储层合成默认额度。
     */
    Optional<ApplicationQuota> findByApplicationId(long applicationId);
}

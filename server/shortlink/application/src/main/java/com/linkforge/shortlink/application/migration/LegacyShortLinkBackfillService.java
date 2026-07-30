package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将历史上没有应用和域名归属的短链回填到租户的兼容默认绑定。
 *
 * <p>服务先通过 Platform 发布契约获取或创建租户默认应用与专属域名，再用同一组 ID 批量更新该租户中
 * {@code application_id IS NULL AND domain_id IS NULL} 的短链。已有任一 scope 的行不会被覆盖；相同租户
 * 重复执行时，默认绑定会被复用，回填 SQL 自然收敛为零行更新，因此串行重跑是幂等的。</p>
 *
 * <p>当前默认事务传播下，兼容绑定开通与批量回填加入本方法事务，任一持久化失败都会向上传播并回滚本地写入。
 * 默认资源的首次开通采用先查后插入，并发调用可能由唯一约束淘汰其中一方；失败方不会在本次调用内自动重试，
 * 应在新事务中重跑。回填直接执行批量 SQL，不逐个加载聚合，也不产生短链领域事件或缓存失效任务。</p>
 */
@Service
public class LegacyShortLinkBackfillService {

    private final LegacyApplicationProvisioningPort legacyApplicationProvisioningPort;
    private final ShortLinkOwnershipBackfillRepository backfillRepository;

    public LegacyShortLinkBackfillService(
            LegacyApplicationProvisioningPort legacyApplicationProvisioningPort,
            ShortLinkOwnershipBackfillRepository backfillRepository
    ) {
        this.legacyApplicationProvisioningPort = legacyApplicationProvisioningPort;
        this.backfillRepository = backfillRepository;
    }

    /**
     * 为单个租户确保兼容默认绑定，并回填所有仍为双空 scope 的历史短链。
     *
     * @param tenantId 待迁移租户；租户合法性和存在性由下游 Platform/持久化约束判定
     * @return 本次使用的稳定绑定 ID 以及实际受影响的短链行数
     * @throws RuntimeException 默认绑定开通或批量更新失败时原样向上传播
     */
    @Transactional
    public BackfillResult backfillTenant(long tenantId) {
        LegacyApplicationBindingView binding = legacyApplicationProvisioningPort.ensureLegacyDefaultBinding(tenantId);
        int updated = backfillRepository.backfillTenant(tenantId, binding.applicationId(), binding.domainId());
        return new BackfillResult(tenantId, binding.applicationId(), binding.domainId(), updated);
    }
}

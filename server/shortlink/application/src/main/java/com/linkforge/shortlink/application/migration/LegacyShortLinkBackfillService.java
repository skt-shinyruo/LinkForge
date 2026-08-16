package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.shortlink.application.port.LegacyShortLinkBackfillStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将历史上没有应用和域名归属的短链回填到租户的兼容默认绑定。
 *
 * <p>服务先通过 Platform 发布契约取得稳定默认应用与专属域名，再由 durable checkpoint 以 keyset 分批发现
 * legacy link。每条 work item 只调用 {@link ShortLinkOwnershipReconciliationService}，因此作用域授权、聚合
 * CAS、quota、事件和缓存失效与在线单链路拥有完全相同的正确性规则。</p>
 *
 * <p>批次本身没有大事务：checkpoint、每条 reconciliation、每条结果分别提交。崩溃最多留下 PENDING work
 * item；重启后的幂等 reconciliation 会补记终态，不会跳过失败项，也不会重复业务副作用。</p>
 */
@Service
public class LegacyShortLinkBackfillService {

    private final LegacyApplicationProvisioningPort legacyApplicationProvisioningPort;
    private final LegacyShortLinkBackfillStore backfillStore;
    private final ShortLinkOwnershipReconciliationService reconciliationService;

    public LegacyShortLinkBackfillService(
            LegacyApplicationProvisioningPort legacyApplicationProvisioningPort,
            LegacyShortLinkBackfillStore backfillStore,
            ShortLinkOwnershipReconciliationService reconciliationService
    ) {
        this.legacyApplicationProvisioningPort = legacyApplicationProvisioningPort;
        this.backfillStore = backfillStore;
        this.reconciliationService = reconciliationService;
    }

    /**
     * 为一个租户发现并 reconcile 最多 {@code batchSize} 条 legacy link。
     *
     * <p>Checkpoint 发现、每条 link reconciliation 和每次结果写入分别提交。若进程在 reconciliation 后、
     * 记录结果前崩溃，会留下 durable PENDING 项；重试会观察到目标 ownership 并记录 ALREADY_RECONCILED，
     * 不重复 quota、事件或缓存失效副作用。</p>
     */
    public LegacyShortLinkBackfillBatchResult reconcileNextBatch(long tenantId, int batchSize) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        LegacyApplicationBindingView binding = legacyApplicationProvisioningPort.ensureLegacyDefaultBinding(tenantId);
        List<LegacyShortLinkBackfillStore.WorkItem> workItems = backfillStore.takeBatch(
                tenantId,
                binding.applicationId(),
                binding.domainId(),
                batchSize
        );

        int reconciled = 0;
        int alreadyReconciled = 0;
        int retryable = 0;
        int permanentFailure = 0;
        int notFound = 0;
        for (LegacyShortLinkBackfillStore.WorkItem item : workItems) {
            ShortLinkOwnershipReconciliationResult result;
            try {
                result = reconciliationService.reconcile(
                        item.tenantId(),
                        item.linkId(),
                        item.applicationId(),
                        item.domainId()
                );
            } catch (RuntimeException ex) {
                retryable++;
                backfillStore.recordOutcome(
                        item.tenantId(),
                        item.linkId(),
                        LegacyShortLinkBackfillStore.Outcome.RETRYABLE,
                        describe(ex)
                );
                continue;
            }

            LegacyShortLinkBackfillStore.Outcome outcome;
            String detail = null;
            switch (result.status()) {
                case RECONCILED -> {
                    outcome = LegacyShortLinkBackfillStore.Outcome.RECONCILED;
                    reconciled++;
                }
                case ALREADY_RECONCILED -> {
                    outcome = LegacyShortLinkBackfillStore.Outcome.ALREADY_RECONCILED;
                    alreadyReconciled++;
                }
                case RETRYABLE_CONFLICT -> {
                    outcome = LegacyShortLinkBackfillStore.Outcome.RETRYABLE;
                    detail = "optimistic ownership conflict";
                    retryable++;
                }
                case OWNERSHIP_CONFLICT -> {
                    outcome = LegacyShortLinkBackfillStore.Outcome.PERMANENT_FAILURE;
                    detail = "link is already owned by another scope";
                    permanentFailure++;
                }
                case NOT_FOUND -> {
                    outcome = LegacyShortLinkBackfillStore.Outcome.NOT_FOUND;
                    notFound++;
                }
                default -> throw new IllegalStateException("unsupported ownership reconciliation result");
            }
            backfillStore.recordOutcome(item.tenantId(), item.linkId(), outcome, detail);
        }

        LegacyShortLinkBackfillProgress progress = backfillStore.progress(tenantId);
        return new LegacyShortLinkBackfillBatchResult(
                tenantId,
                binding.applicationId(),
                binding.domainId(),
                workItems.size(),
                reconciled,
                alreadyReconciled,
                retryable,
                permanentFailure,
                notFound,
                progress
        );
    }

    private static String describe(RuntimeException ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}

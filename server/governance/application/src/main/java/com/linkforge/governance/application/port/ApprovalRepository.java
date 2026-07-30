package com.linkforge.governance.application.port;

import com.linkforge.governance.domain.ApprovalRequest;

import java.util.List;
import java.util.Optional;

/**
 * 审批请求持久化端口。
 *
 * <p>所有读写都必须以 {@code tenantId} 作为隔离条件。状态推进方法是并发正确性的边界：实现必须用单条条件更新
 * 完成比较并交换，不能采用“先查后改”。写方法应参与调用方事务，使审批状态、业务执行结果和审计记录可以共同提交或回滚。</p>
 */
public interface ApprovalRepository {

    /** 插入新的审批请求；ID 由应用层预先分配，重复 ID 应作为写入失败上抛。 */
    void insert(ApprovalRequest request);

    /** 按租户和请求 ID 查询；跨租户或不存在均返回空。 */
    Optional<ApprovalRequest> findByTenantIdAndId(long tenantId, long requestId);

    /** 返回租户内审批请求，约定按创建时间、ID 倒序排列。 */
    List<ApprovalRequest> listByTenantId(long tenantId);

    /**
     * 原子抢占待审批请求并记录批准决定。
     *
     * @return 仅当目标属于指定租户且当前状态为 {@code PENDING_APPROVAL}、本次确实更新一行时返回 {@code true}；
     * 请求不存在、租户不匹配、状态已变化或重复调用均返回 {@code false}
     */
    boolean markApprovedIfPending(
            long tenantId,
            long requestId,
            long approverUserId,
            String approverEmail,
            String decisionReason,
            java.time.LocalDateTime decidedAt
    );

    /**
     * 在执行器成功返回后，原子地将已批准请求推进为已执行。
     *
     * @return 仅当目标属于指定租户且当前状态为 {@code APPROVED}、本次确实更新一行时返回 {@code true}；否则返回 {@code false}
     */
    boolean markExecutedIfApproved(
            long tenantId,
            long requestId,
            java.time.LocalDateTime executedAt
    );
}

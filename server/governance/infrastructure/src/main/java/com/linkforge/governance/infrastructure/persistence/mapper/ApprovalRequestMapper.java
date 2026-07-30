package com.linkforge.governance.infrastructure.persistence.mapper;

import com.linkforge.governance.infrastructure.persistence.entity.ApprovalRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批请求 SQL 映射边界。
 *
 * <p>查询和更新必须始终携带 {@code tenantId}。两个状态推进方法是乐观并发控制点，调用方必须
 * 检查返回的受影响行数，不能把零行更新当作重复成功。</p>
 */
@Mapper
public interface ApprovalRequestMapper {

    /** 插入完整审批快照；约束冲突和数据库错误直接上抛。 */
    int insert(ApprovalRequestEntity entity);

    /** 按租户与请求 ID 精确读取；未命中时返回 {@code null}。 */
    ApprovalRequestEntity findByTenantIdAndId(@Param("tenantId") long tenantId, @Param("requestId") long requestId);

    /** 返回租户全部审批记录，按创建时间和 ID 倒序；当前接口不分页。 */
    List<ApprovalRequestEntity> listByTenantId(@Param("tenantId") long tenantId);

    /**
     * 将 {@code PENDING_APPROVAL} 原子推进为 {@code APPROVED}。
     *
     * <p>CAS 条件只包含租户、请求 ID 和当前状态，不包含执行器 ID；返回 {@code 1} 表示认领成功，
     * 返回 {@code 0} 表示不存在、租户不匹配或并发状态变化。</p>
     */
    int markApprovedIfPending(
            @Param("tenantId") long tenantId,
            @Param("requestId") long requestId,
            @Param("approverUserId") long approverUserId,
            @Param("approverEmail") String approverEmail,
            @Param("decisionReason") String decisionReason,
            @Param("decidedAt") LocalDateTime decidedAt
    );

    /**
     * 将 {@code APPROVED} 原子推进为 {@code EXECUTED}；零行更新必须视为状态记录失败。
     */
    int markExecutedIfApproved(
            @Param("tenantId") long tenantId,
            @Param("requestId") long requestId,
            @Param("executedAt") LocalDateTime executedAt
    );
}

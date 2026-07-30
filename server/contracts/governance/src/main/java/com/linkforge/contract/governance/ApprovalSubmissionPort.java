package com.linkforge.contract.governance;

import java.time.LocalDateTime;

/**
 * 业务上下文向 Governance 提交敏感操作审批的窄口径发布契约。
 *
 * <p>当前官方实现只公开短链目标地址变更和访问明细导出两类提交，并在 Governance 的本地事务内创建
 * {@code PENDING_APPROVAL} 请求及 {@code SUBMIT_REQUEST} 审计事实。该原子性只覆盖参与该数据库事务的本地写入；
 * 本端口不定义跨服务投递、异步副作用或 exactly-once 保证。</p>
 *
 * <p>调用方负责在进入本端口前完成资源可见性、应用范围、URL 和时间窗口校验。本端口的官方实现会校验
 * {@link ApprovalRequester} 的用户 ID、邮箱和租户与外层 {@code tenantId} 一致，但不会重新查询短链归属或替
 * 调用方纠正业务字段。没有去重键：同一有效输入重复提交会创建独立审批请求，调用方若需要幂等性必须在上层处理。</p>
 */
public interface ApprovalSubmissionPort {

    /**
     * 提交公开短链目标地址变更审批。
     *
     * <p>官方实现把当前地址和申请地址编码为同一 {@code linkId} 的
     * {@link LinkDestinationChangeApprovalPayload} V1 JSON before/after snapshot。它不会立即修改短链；批准后是否
     * 执行、如何处理陈旧快照和重试由 {@link ApprovalExecutionPort} 及其实现决定。</p>
     *
     * @param tenantId 调用方已经授权的租户作用域，必须与 {@code request.requester().tenantId()} 一致
     * @param request 不可为 {@code null} 的提交输入；调用方必须已校验短链归属、应用范围和两个目标地址
     * @return 新创建的待审批安全视图，不包含前后快照
     * @throws RuntimeException 官方实现会将无效或跨租户申请人转换为业务错误；基础设施失败会向上传播
     */
    ApprovalRequestView requestLinkDestinationChangeApproval(
            long tenantId,
            LinkDestinationChangeApprovalRequest request
    );

    /**
     * 提交访问明细导出审批。
     *
     * <p>官方实现把 {@code linkId} 和 UTC 时间范围编码为
     * {@link AnalyticsDetailExportApprovalPayload} V1 JSON after snapshot，并固定 before snapshot 为 {@code null}。
     * 当前审批通过只记录决策；没有注册执行器时状态会停留在 {@code APPROVED}，本方法不生成或返回导出文件。</p>
     *
     * @param tenantId 调用方已经授权的租户作用域，必须与 {@code request.requester().tenantId()} 一致
     * @param request 不可为 {@code null} 的提交输入；调用方必须已校验链接归属、应用范围和 UTC 时间窗口
     * @return 新创建的待审批安全视图，不包含 payload
     * @throws RuntimeException 官方实现会将无效或跨租户申请人转换为业务错误；基础设施失败会向上传播
     */
    ApprovalRequestView requestAnalyticsDetailExportApproval(
            long tenantId,
            AnalyticsDetailExportApprovalRequest request
    );

    /**
     * 提交短链目标地址变更审批的显式输入。
     *
     * <p>该 record 不做 URL、链接或租户校验。官方实现会把两个 URL 原样放入版本化快照，随后由 Governance 校验
     * 申请人，真正执行前再由 Shortlink 执行器校验当前链接状态和 before snapshot。因此调用方不得把成功创建
     * 此对象误认为目标地址已经变更。</p>
     *
     * @param linkId 待变更的短链 ID；调用方必须先确认其在外层租户内可见
     * @param targetApplicationId 可空的预期应用范围；需要应用隔离的调用方必须传入并先验证归属
     * @param currentOriginalUrl 审批时的当前目标地址，作为 before snapshot；应为已经校验/规范化的 URL 文本
     * @param requestedOriginalUrl 申请的新目标地址，作为 after snapshot；应为已经校验/规范化的 URL 文本
     * @param requester 从已认证上下文取得的申请人快照，不能来自不可信请求体
     * @param requestedAt 申请时间，按 UTC {@code LocalDateTime} 解释；为 {@code null} 时由 Governance 服务时钟补齐
     */
    record LinkDestinationChangeApprovalRequest(
            long linkId,
            Long targetApplicationId,
            String currentOriginalUrl,
            String requestedOriginalUrl,
            ApprovalRequester requester,
            LocalDateTime requestedAt
    ) {
    }

    /**
     * 提交访问明细导出审批的显式输入。
     *
     * <p>该 record 允许 Java {@code null} 以保持 payload 的历史可空能力，但正常调用应提供已经校验的
     * {@code from}/{@code to}。端口会把非空时间按 {@link LocalDateTime#toString()} 写入 JSON，不会转换时区或
     * 检查 {@code from <= to}；调用方必须将无 offset 的时间解释为 UTC 并在调用前校验窗口。</p>
     *
     * @param linkId 待导出明细的短链 ID；调用方必须先确认其在外层租户内可见
     * @param targetApplicationId 可空的预期应用范围；需要应用隔离时必须由调用方校验
     * @param from 可空的 UTC 时间窗口起点
     * @param to 可空的 UTC 时间窗口终点，正常流程要求不早于 {@code from}
     * @param requester 从已认证上下文取得的申请人快照，不能来自不可信请求体
     * @param requestedAt 申请时间，按 UTC {@code LocalDateTime} 解释；为 {@code null} 时由 Governance 服务时钟补齐
     */
    record AnalyticsDetailExportApprovalRequest(
            long linkId,
            Long targetApplicationId,
            LocalDateTime from,
            LocalDateTime to,
            ApprovalRequester requester,
            LocalDateTime requestedAt
    ) {
    }
}

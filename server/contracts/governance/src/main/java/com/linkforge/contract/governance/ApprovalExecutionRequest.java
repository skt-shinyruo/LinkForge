package com.linkforge.contract.governance;

/**
 * Governance 交给业务执行器的已批准审批请求快照。
 *
 * <p>在官方审批流程中，本对象只会在请求已经由条件更新从
 * {@code PENDING_APPROVAL} 抢占到 {@code APPROVED} 后构造，并在同一次批准调用中传给唯一匹配的
 * {@link ApprovalExecutionPort}。它不是资源授权凭据：执行器仍必须使用 {@code tenantId} 查询资源，重新
 * 校验目标资源、应用范围和操作自身的前置条件。</p>
 *
 * <p>{@code beforeSnapshot}/{@code afterSnapshot} 是原样保存的不透明文本。由
 * {@link ApprovalSubmissionPort} 新建的短链和统计审批使用带 {@code type}/{@code version} 的 JSON；历史
 * 外部域名审批可以是自由文本，统计明细导出的 {@code beforeSnapshot} 可以为 {@code null}。执行器必须先按
 * {@code operation} 选择其支持的格式和版本，不能把所有历史记录都反序列化为同一种 payload。</p>
 *
 * <p>record 的公开构造器没有校验能力，测试、重放工具或第三方实现直接构造时同样必须维持这些不变量。本对象不
 * 携带执行时间；执行时间由 {@link ApprovalExecutionPort#execute(ApprovalExecutionRequest,
 * java.time.LocalDateTime)} 的第二个参数单独传递，并按 UTC {@code LocalDateTime} 解释。</p>
 *
 * @param id 审批请求 ID，用于审计关联和执行器自身的幂等/去重键；不是目标业务资源 ID
 * @param tenantId 审批所属租户，也是执行器所有资源读取和写入必须携带的隔离条件
 * @param operation 稳定的操作类别，决定允许的快照格式、业务前置条件和执行器选择
 * @param targetApplicationId 可空的目标应用范围；无应用范围的操作或历史数据可为 {@code null}，不能把
 *                            {@code null} 统一解释为任意应用均可执行
 * @param beforeSnapshot 操作前快照，可为 {@code null}；其格式和必填性由 {@code operation} 定义
 * @param afterSnapshot 操作后或请求目标快照，可为 {@code null}；其格式和必填性由 {@code operation} 定义
 */
public record ApprovalExecutionRequest(
        long id,
        long tenantId,
        SensitiveOperation operation,
        Long targetApplicationId,
        String beforeSnapshot,
        String afterSnapshot
) {
}

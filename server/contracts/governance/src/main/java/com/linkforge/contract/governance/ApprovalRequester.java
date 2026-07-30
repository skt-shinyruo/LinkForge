package com.linkforge.contract.governance;

/**
 * 提交审批时捕获的已认证用户快照。
 *
 * <p>该类型不是登录凭据，也不携带角色；它只把调用链已认证的租户、用户 ID 和邮箱复制到审批请求及提交审计。
 * 调用方不得从 HTTP 请求体等不可信输入构造它。官方提交适配器会将其转换为内部主体，并拒绝空主体、非正用户 ID、
 * 空白邮箱以及 {@code tenantId} 与外层调用作用域不一致的情况。</p>
 *
 * <p>record 自身不执行校验，因此直接构造不会产生认证或授权效果。邮箱是历史快照，不会随着用户资料后续修改而
 * 回写已创建的审批记录。</p>
 *
 * @param tenantId 申请人所属租户；必须与 {@link ApprovalSubmissionPort} 方法的外层 {@code tenantId} 相同
 * @param userId 已认证用户 ID；官方实现要求为正数
 * @param email 申请时的用户邮箱快照；官方实现要求非 {@code null} 且非空白
 */
public record ApprovalRequester(long tenantId, long userId, String email) {
}

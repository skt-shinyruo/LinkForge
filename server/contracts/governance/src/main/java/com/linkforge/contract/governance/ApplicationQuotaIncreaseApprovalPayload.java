package com.linkforge.contract.governance;

/**
 * 应用额度提升审批使用的 V1 结构化 payload。
 *
 * <p>官方 Governance 审批矩阵只会在 {@code type=applicationQuotaIncrease} 且
 * {@code version=1} 时解析本类型，并要求 {@code monthlyLinkLimit} 非 {@code null} 后才决定审批权限。
 * {@code monthlyClickLimit} 可以为 {@code null}，表示本次 payload 不携带点击额度变更；它不表示清零。
 * record 构造器和 {@link #v1(Long, Long)} 不做这些业务校验，因此不能把成功构造当作可批准或可执行。</p>
 *
 * <p>额度的数值范围、单位、当前值比较和实际写入均由相应的审批规则/执行器定义。本 payload 本身不改变额度，
 * 不加入事务，也不提供重试或 exactly-once 语义。</p>
 *
 * @param type 应为 {@link ApprovalPayloadTypes#APPLICATION_QUOTA_INCREASE}；消费方必须显式比较
 * @param version 应为 {@link ApprovalPayloadTypes#VERSION_1}；未知版本不得按 V1 猜测解释
 * @param monthlyLinkLimit 申请的月短链额度；当前审批矩阵要求非 {@code null}
 * @param monthlyClickLimit 可空的月点击额度变更；{@code null} 表示该字段未随本次申请提供
 */
public record ApplicationQuotaIncreaseApprovalPayload(
        String type,
        int version,
        Long monthlyLinkLimit,
        Long monthlyClickLimit
) {

    /**
     * 创建 type 和 version 已固定的 V1 payload。
     *
     * <p>该工厂只固定 wire 标识并原样保存两个数值，不验证非空、非负或权限上限；提交/审批/执行环节仍需按照
     * 各自规则校验。</p>
     *
     * @param monthlyLinkLimit 申请的月短链额度，可在构造阶段为 {@code null}
     * @param monthlyClickLimit 申请的月点击额度变更，可为 {@code null}
     * @return 带固定 {@code applicationQuotaIncrease/v1} 标识的 payload
     */
    public static ApplicationQuotaIncreaseApprovalPayload v1(Long monthlyLinkLimit, Long monthlyClickLimit) {
        return new ApplicationQuotaIncreaseApprovalPayload(
                ApprovalPayloadTypes.APPLICATION_QUOTA_INCREASE,
                ApprovalPayloadTypes.VERSION_1,
                monthlyLinkLimit,
                monthlyClickLimit
        );
    }
}

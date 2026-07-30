package com.linkforge.contract.governance;

/**
 * 公开短链目标地址变更审批使用的 V1 结构化 snapshot。
 *
 * <p>{@link ApprovalSubmissionPort} 会用同一 {@code linkId} 的两个本对象分别形成 before 和 after snapshot，
 * 并使用 {@link ApprovalPayloadCodec} 保存为 JSON。执行器在批准后必须同时验证 type/version、两个链接 ID 一致、
 * 当前资源的租户/应用范围，以及当前目标地址仍与 before snapshot 相同；已批准不等于可以跳过并发和陈旧写保护。</p>
 *
 * <p>该 record 只承载 wire 数据，不解析 URL 或检查链接存在性。公开构造器和 {@link #v1(long, String)} 都会原样
 * 保留值；当前 Shortlink 执行器会拒绝非正链接 ID、空白 URL、未知 type/version 和历史自由文本快照。</p>
 *
 * @param type 应为 {@link ApprovalPayloadTypes#LINK_DESTINATION_CHANGE}；消费方必须显式校验
 * @param version 应为 {@link ApprovalPayloadTypes#VERSION_1}；未知版本不得按 V1 解释
 * @param linkId 变更目标短链 ID；本 record 不校验其存在性、正数性或租户归属
 * @param originalUrl 审批时的原始目标地址文本；before 表示当时地址，after 表示申请地址，不能为空由执行器校验
 */
public record LinkDestinationChangeApprovalPayload(
        String type,
        int version,
        long linkId,
        String originalUrl
) {

    /**
     * 创建 type 和 version 已固定的 V1 payload。
     *
     * <p>该工厂不规范化或验证 URL，也不查询短链；调用方应在提交前完成输入和资源范围校验，执行器仍会在真正修改前
     * 重新检查当前状态。</p>
     *
     * @param linkId 目标短链 ID
     * @param originalUrl 要写入 snapshot 的原始目标地址文本
     * @return 带固定 {@code linkDestinationChange/v1} 标识的 payload
     */
    public static LinkDestinationChangeApprovalPayload v1(long linkId, String originalUrl) {
        return new LinkDestinationChangeApprovalPayload(
                ApprovalPayloadTypes.LINK_DESTINATION_CHANGE,
                ApprovalPayloadTypes.VERSION_1,
                linkId,
                originalUrl
        );
    }
}

package com.linkforge.contract.governance;

import java.time.LocalDateTime;

/**
 * 访问明细导出审批使用的 V1 结构化 after snapshot。
 *
 * <p>{@link ApprovalSubmissionPort} 对该操作固定保存 {@code beforeSnapshot=null}，并把本对象编码为
 * {@code afterSnapshot}。它描述申请导出的链接和时间范围，而不是导出任务或文件：当前没有对应的自动执行器，
 * 因此审批通过只表示决策完成，不能据此推断文件已经生成。</p>
 *
 * <p>时间字段是没有 offset 的 ISO-8601 {@link LocalDateTime#toString()} 结果，按 UTC 解释。该 record 和
 * {@link #v1(long, LocalDateTime, LocalDateTime)} 都不校验链接归属、时间先后或 {@code null}；官方 Analytics
 * 调用方会先校验资源范围和窗口。直接读取历史 JSON 时，消费方必须继续校验 type/version 和业务字段。</p>
 *
 * @param type 应为 {@link ApprovalPayloadTypes#ANALYTICS_DETAIL_EXPORT}；record 构造器不自动强制
 * @param version 应为 {@link ApprovalPayloadTypes#VERSION_1}；不支持的版本必须由消费方拒绝
 * @param linkId 待导出明细的短链 ID；资源存在性和租户归属不在本 record 中校验
 * @param from 可空的 UTC 起始时间文本；通常由 {@link #v1(long, LocalDateTime, LocalDateTime)} 生成
 * @param to 可空的 UTC 结束时间文本；通常由 {@link #v1(long, LocalDateTime, LocalDateTime)} 生成
 */
public record AnalyticsDetailExportApprovalPayload(
        String type,
        int version,
        long linkId,
        String from,
        String to
) {

    /**
     * 创建 type 和 version 已固定的 V1 payload。
     *
     * <p>本方法不转换时区：调用方传入的 {@link LocalDateTime} 必须已表达 UTC。{@code from}/{@code to} 为
     * {@code null} 时会保留为 JSON {@code null}，以便 codec 与历史可空字段兼容；正常导出申请应由调用方在
     * 此前补齐并验证时间范围。</p>
     *
     * @param linkId 待导出明细的短链 ID
     * @param from 按 UTC 解释的起始时间，可为空
     * @param to 按 UTC 解释的结束时间，可为空
     * @return 带固定 {@code analyticsDetailExport/v1} 标识的 payload
     */
    public static AnalyticsDetailExportApprovalPayload v1(long linkId, LocalDateTime from, LocalDateTime to) {
        return new AnalyticsDetailExportApprovalPayload(
                ApprovalPayloadTypes.ANALYTICS_DETAIL_EXPORT,
                ApprovalPayloadTypes.VERSION_1,
                linkId,
                from == null ? null : from.toString(),
                to == null ? null : to.toString()
        );
    }
}

package com.linkforge.contract.governance;

/**
 * 结构化审批 JSON 的稳定类型和版本标识。
 *
 * <p>这些常量是持久化快照和跨上下文执行之间的 wire contract，不是 {@link SensitiveOperation} 枚举名称。
 * {@link ApprovalPayloadCodec} 不会自动验证它们；生产者负责写入，消费者负责在反序列化后显式比较。改变既有
 * V1 的字段语义或 token 会破坏历史审批记录，应通过新增版本和相应 payload 类演进。</p>
 */
public final class ApprovalPayloadTypes {

    /** 当前已发布 payload 的首个结构版本；每种 type 都必须单独校验其支持的版本。 */
    public static final int VERSION_1 = 1;

    /** {@link LinkDestinationChangeApprovalPayload} 使用的目标地址变更 type token。 */
    public static final String LINK_DESTINATION_CHANGE = "linkDestinationChange";

    /** {@link AnalyticsDetailExportApprovalPayload} 使用的访问明细导出 type token。 */
    public static final String ANALYTICS_DETAIL_EXPORT = "analyticsDetailExport";

    /** {@link ApplicationQuotaIncreaseApprovalPayload} 使用的应用额度提升 type token。 */
    public static final String APPLICATION_QUOTA_INCREASE = "applicationQuotaIncrease";

    private ApprovalPayloadTypes() {
    }
}

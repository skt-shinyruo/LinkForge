package com.linkforge.contract.shortlink;

/**
 * Shortlink 写入 {@code integration_events.event_type} 的稳定事件类型字符串。
 *
 * <p>这些值不是 payload 内的 JSON 字段。消费者必须先根据该字符串选择对应的 V1 record，再反序列化
 * {@code payload_json}；不得通过猜测 JSON 字段来识别事件类型。已发布字符串属于跨上下文 wire contract，
 * 不能改名或复用到不同 payload。发生不兼容变更时应新增类型和版本，并在滚动升级期间同时处理旧类型。</p>
 */
public final class ShortLinkEventTypes {
    private ShortLinkEventTypes() {}

    /** 创建后完整快照的 V1 类型：{@value}。 */
    public static final String SHORT_LINK_CREATED_V1 = "shortlink.ShortLinkCreated.v1";

    /** 更新后完整快照的 V1 类型：{@value}。 */
    public static final String SHORT_LINK_UPDATED_V1 = "shortlink.ShortLinkUpdated.v1";

    /** 归档后完整快照的 V1 类型：{@value}。 */
    public static final String SHORT_LINK_ARCHIVED_V1 = "shortlink.ShortLinkArchived.v1";

    /** 恢复后完整快照的 V1 类型：{@value}。 */
    public static final String SHORT_LINK_RESTORED_V1 = "shortlink.ShortLinkRestored.v1";

    /** 删除前历史快照的 V1 类型：{@value}。 */
    public static final String SHORT_LINK_DELETED_V1 = "shortlink.ShortLinkDeleted.v1";
}

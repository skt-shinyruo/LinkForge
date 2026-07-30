package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;

import java.time.Instant;

/**
 * 短链更新 V1 事件。
 *
 * <p>外层 {@code integration_events.event_type} 固定为 {@link ShortLinkEventTypes#SHORT_LINK_UPDATED_V1}；
 * record component 名即已发布 JSON 字段名，V1 中不得重命名、删除或改变含义。{@code eventId} 可用于消费者
 * 幂等去重，但事件表重放和 checkpoint 重试会重复投递同一 ID，基础设施不承诺全链路 exactly-once。消费者必须
 * 先按 event type 选择本 DTO，再反序列化 payload。标准生产者将该记录与短链写入同一数据库事务的 durable
 * outbox；业务事务回滚时不应向消费者交付该事件。</p>
 *
 * @param eventId 单次持久化事件的稳定唯一 ID；重复投递时保持不变，供消费者去重
 * @param occurredAtUtc 事件发生时刻的 UTC {@link Instant}；生产者必须提供非空值
 * @param tenantId 所属租户 ID；必须与 {@code snapshot.tenantId} 一致
 * @param linkId 全局短链 ID；必须与 {@code snapshot.linkId} 一致
 * @param code 大小写敏感短码；必须与 {@code snapshot.code} 一致
 * @param snapshot 更新后的完整公开事实；生产者必须提供非空值
 */
public record ShortLinkUpdatedV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}

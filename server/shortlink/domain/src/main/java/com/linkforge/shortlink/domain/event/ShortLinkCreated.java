package com.linkforge.shortlink.domain.event;

/**
 * 新短链聚合创建时记录的领域事件。
 *
 * <p>仅 {@code ShortLink.create(...)} 记录本事件，持久化恢复不会重放。事件没有独立发生时间，应用层发布时使用
 * 创建命令提供的 UTC 时间，并以已构造完成的聚合补齐公开事件快照。</p>
 *
 * @param linkId 新短链 ID
 * @param tenantId 所属租户 ID
 * @param domainId 可选域名 ID
 * @param code 创建时的短码
 */
public record ShortLinkCreated(
        long linkId,
        long tenantId,
        Long domainId,
        String code
) implements ShortLinkDomainEvent {
}

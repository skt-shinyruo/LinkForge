package com.linkforge.shortlink.domain;

import java.time.LocalDateTime;

/**
 * 一次短链目标地址修订的不可变数据快照。
 *
 * <p>时间字段使用 UTC 语义的 {@link LocalDateTime}。该类型只承载修订数据，不负责校验 URL、操作者或持久化顺序；
 * 调用方必须在创建快照前完成相应校验。{@code requestedBy} 允许为空，以兼容没有用户主体的修订来源。</p>
 *
 * @param linkId 被修订的短链 ID
 * @param originalUrl 该修订记录的原始地址文本
 * @param requestedBy 发起者 ID；没有用户主体时可为空
 * @param createdAtUtc 修订记录创建时间，业务语义为 UTC
 */
public record ShortLinkRevision(
        long linkId,
        String originalUrl,
        Long requestedBy,
        LocalDateTime createdAtUtc
) {
}

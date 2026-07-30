package com.linkforge.shortlink.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 创建短链的应用层输入。
 *
 * <p>{@code enabled=null} 由聚合解释为启用，{@code previewEnabled=null} 解释为关闭；
 * {@code applicationId} 与 {@code domainId} 必须同时提供或同时为空。空白 {@code customCode}
 * 表示使用生成短码，其他字符串和值对象字段由命令处理器统一归一化和校验。</p>
 *
 * <p>{@link Instant} 进入领域层前会按 UTC 转换为不携带时区的数据库时间。该输入不携带幂等键，
 * 重复提交自动短码请求会创建不同短链。</p>
 */
public record CreateLinkRequest(
        String originalUrl,
        String note,
        Instant expiresAt,
        Boolean enabled,
        String customCode,
        Set<String> tags,
        Integer redirectStatusCode,
        Boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        List<String> queryForwardAllowlist,
        Long applicationId,
        Long domainId,
        String lifecycleState
) {
}

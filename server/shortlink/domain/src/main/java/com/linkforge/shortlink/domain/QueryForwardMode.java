package com.linkforge.shortlink.domain;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_QUERY_FORWARD_MODE;

/**
 * 请求查询参数向目标地址的透传模式。
 *
 * <ul>
 *   <li>{@link #OFF}：不透传请求查询参数；</li>
 *   <li>{@link #ALLOWLIST}：只透传命中白名单的参数；</li>
 *   <li>{@link #ALL}：透传非保留参数，冲突与长度限制仍由重定向策略处理。</li>
 * </ul>
 *
 * <p>聚合中的 {@code null} 表示没有短链级覆盖，重定向链路可继承全局模式，并不等同于 {@code OFF}。</p>
 */
public enum QueryForwardMode {
    OFF,
    ALLOWLIST,
    ALL;

    /**
     * 解析忽略大小写的外部文本。
     *
     * @param raw 原始值；空值或纯空白返回 {@code null}
     * @throws ShortLinkDomainException 非空文本不属于三个受支持值时抛出
     */
    public static QueryForwardMode parseNullable(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isBlank()) {
            return null;
        }
        String upper = t.toUpperCase();
        return switch (upper) {
            case "OFF" -> OFF;
            case "ALLOWLIST" -> ALLOWLIST;
            case "ALL" -> ALL;
            default -> throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_MODE, "queryForwardMode 仅支持 OFF/ALLOWLIST/ALL");
        };
    }
}

package com.linkforge.shortlink.domain;

/**
 * 短链创建主体的类型。
 *
 * <p>持久化使用大写枚举名（例如 {@code USER}、{@code API_KEY}）。类型只解释 {@link ShortLink#createdBy()} 的
 * ID 空间，主体是否存在、是否属于租户以及是否有权限由应用层校验。</p>
 */
public enum CreatedByType {
    USER,
    API_KEY;

    /**
     * 宽容解析持久化值，空值、未知值或格式错误均回退到调用方提供的默认类型。
     *
     * <p>该行为用于兼容历史数据，不适合作为外部请求的严格枚举校验。</p>
     */
    public static CreatedByType parseOrDefault(String raw, CreatedByType defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return CreatedByType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

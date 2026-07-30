package com.linkforge.shortlink.domain;

/**
 * 短链的发布阶段。
 *
 * <p>{@link #DRAFT}、{@link #PRE_RELEASE}、{@link #ACTIVE}、{@link #DISABLED} 是可直接设置的阶段，领域层当前不限制
 * 它们之间的转换路径。重定向链路只把 {@code ACTIVE} 视为可用阶段，其余阶段均不可跳转。归档由
 * {@link ShortLink#archivedAtUtc()} 独立表达，不是本枚举中的一个阶段。</p>
 */
public enum ShortLinkLifecycleState {
    DRAFT,
    PRE_RELEASE,
    ACTIVE,
    DISABLED;

    /**
     * 解析外部或持久化文本；空值和纯空白兼容为 {@link #ACTIVE}。
     *
     * <p>解析忽略首尾空白和大小写；未知值由 {@link Enum#valueOf(Class, String)} 抛出
     * {@link IllegalArgumentException}，应用层负责转换成稳定的参数错误。</p>
     */
    public static ShortLinkLifecycleState parseNullable(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            return ACTIVE;
        }
        return ShortLinkLifecycleState.valueOf(raw.trim().toUpperCase());
    }
}

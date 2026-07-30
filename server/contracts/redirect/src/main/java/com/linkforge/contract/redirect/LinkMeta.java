package com.linkforge.contract.redirect;

import java.time.LocalDateTime;

/**
 * Shortlink 事实映射到 Redirect 的跳转快照。
 *
 * <p>{@code expiresAt} 的语义时区是 UTC，尽管类型为不携带时区的 {@link LocalDateTime}；{@code null}
 * 表示未设置过期时间。code 保持大小写敏感。{@code enabled}、过期时间和 lifecycle 由 Redirect 组合为最终
 * 可用性，获取到此快照本身不代表可以跳转。</p>
 *
 * <p>这是跨上下文的事实快照，而非校验器：构造时不会验证 ID、code、URL、host 或策略值。它既可来自
 * Shortlink 权威读的映射，也可来自缓存；缓存副本可能陈旧，不能被当作另一条权威读取通道。可空策略字段
 * 交由 Redirect 的全局配置接管；{@code hostname} 的 {@code null} 或空白值代表 legacy/unscoped 链接，且
 * 本类型不负责 host 归一化。非空 hostname 也不是 domain scope 的充分证据：权威读在基础域名回退到旧链接时
 * 会填入请求 host。application/domain 归属也可为空以兼容旧数据。旧构造器和 canonical constructor 的
 * {@code null}/空白 lifecycle 都兼容归一为 ACTIVE，不能据此推断持久化记录曾显式存储 ACTIVE。</p>
 *
 * @param id 发布方提供的全局短链 ID；本类型不校验其取值
 * @param tenantId 发布方提供的所属租户 ID；本类型不校验其取值
 * @param code 大小写敏感的短码；本类型不改变其大小写
 * @param originalUrl 由发布方校验过的目标 URL；本类型不再次校验
 * @param enabled 管理开关，仍需结合 lifecycle 和 expiresAt 判断
 * @param expiresAt UTC 到期时间，null 表示未设置
 * @param redirectStatusCode 单链重定向状态覆盖；仅 301/302 生效，null 或其他值使用全局默认
 * @param previewEnabled 是否允许预览/确认链路
 * @param unavailableLandingUrl 不可用时的单链落地页覆盖；仅有效 HTTP(S) URL 生效，其他值回退全局配置
 * @param queryForwardMode 查询参数透传模式覆盖；null 或空白时 Redirect 使用全局配置，未知值按安全默认处理
 * @param queryForwardAllowlist 逗号序列化的单链透传白名单；null 或空白表示没有单链条目
 * @param hostname 关联 host；null 或空白表示 legacy/unscoped 快照，本类型不归一化该值；非空也不能单独
 *                 证明链接有 domain scope，因为权威读会为基础域名回退的 legacy 链接补写请求 host
 * @param applicationId 所属应用；legacy 数据可为空
 * @param domainId 所属域名；legacy 数据可为空
 * @param lifecycleState 当前生命周期，非 ACTIVE 由 Redirect 判定为不可跳转
 */
public record LinkMeta(
        long id,
        long tenantId,
        String code,
        String originalUrl,
        boolean enabled,
        LocalDateTime expiresAt,
        Integer redirectStatusCode,
        boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        String queryForwardAllowlist,
        String hostname,
        Long applicationId,
        Long domainId,
        String lifecycleState
) {

    /**
     * Redirect 认可为可参与跳转决策的生命周期名称。
     *
     * <p>它只描述 lifecycle 维度；{@code enabled}、过期时间和额度仍由 Redirect 单独判定。</p>
     */
    public static final String ACTIVE_LIFECYCLE_STATE = "ACTIVE";

    /**
     * 创建快照并仅归一 lifecycle 状态。
     *
     * <p>该构造器将 {@code null}、空白 lifecycle 归一为 {@link #ACTIVE_LIFECYCLE_STATE}，其余非空白值先
     * trim 再转大写；不验证或归一其他字段。发布方必须在跨上下文边界前维护 URL、code 和归属等不变量。</p>
     */
    public LinkMeta {
        lifecycleState = normalizeLifecycleState(lifecycleState);
    }

    /**
     * 使用 ACTIVE lifecycle 创建兼容快照，并保留 application/domain 归属。
     *
     * <p>所有其余参数与 record component 的语义相同。该重载表达旧发布者没有 lifecycle 字段，而不是
     * 证明持久化记录显式存储了 ACTIVE。</p>
     */
    public LinkMeta(
            long id,
            long tenantId,
            String code,
            String originalUrl,
            boolean enabled,
            LocalDateTime expiresAt,
            Integer redirectStatusCode,
            boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            String queryForwardAllowlist,
            String hostname,
            Long applicationId,
            Long domainId
    ) {
        this(
                id,
                tenantId,
                code,
                originalUrl,
                enabled,
                expiresAt,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                hostname,
                applicationId,
                domainId,
                ACTIVE_LIFECYCLE_STATE
        );
    }

    /**
     * 使用 ACTIVE lifecycle 且无 application/domain 归属创建 legacy 兼容快照。
     *
     * <p>该重载固定 {@code applicationId} 和 {@code domainId} 为 {@code null}；它不根据 hostname 推断
     * 归属，也不改变 code、URL 或 host 的原始值。</p>
     */
    public LinkMeta(
            long id,
            long tenantId,
            String code,
            String originalUrl,
            boolean enabled,
            LocalDateTime expiresAt,
            Integer redirectStatusCode,
            boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            String queryForwardAllowlist,
            String hostname
    ) {
        this(
                id,
                tenantId,
                code,
                originalUrl,
                enabled,
                expiresAt,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                hostname,
                null,
                null,
                ACTIVE_LIFECYCLE_STATE
        );
    }

    /**
     * 判断快照是否处于 Redirect 认可的 ACTIVE lifecycle。
     *
     * @return {@code lifecycleState} 恰为 {@link #ACTIVE_LIFECYCLE_STATE} 时为 {@code true}；enabled、
     * 过期时间和额度仍需由 Redirect 单独判断
     */
    public boolean activeLifecycle() {
        return ACTIVE_LIFECYCLE_STATE.equals(lifecycleState);
    }

    private static String normalizeLifecycleState(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            return ACTIVE_LIFECYCLE_STATE;
        }
        return raw.trim().toUpperCase();
    }
}

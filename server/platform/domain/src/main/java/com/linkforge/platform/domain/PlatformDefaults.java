package com.linkforge.platform.domain;

/**
 * 平台创建应用及其附属策略、额度时使用的集中默认值与输入边界。
 *
 * <p>其中状态、额度和策略默认值会写入持久化记录，调整只影响此后创建的应用，不会自动迁移历史
 * 数据。{@code applicationKey} 的长度常量同时作为 HTTP 层和应用层校验边界；其余列宽常量用于接口层
 * 参数校验，均必须与数据库列宽保持一致。</p>
 */
public final class PlatformDefaults {

    /** 新应用的启用状态。 */
    public static final String APPLICATION_STATUS_ACTIVE = "ACTIVE";
    /** 应用键最大字符数，与 {@code applications.application_key} 列宽一致。 */
    public static final int APPLICATION_KEY_MAX_LENGTH = 64;
    /** 应用展示名称最大字符数，与 {@code applications.display_name} 列宽一致。 */
    public static final int APPLICATION_DISPLAY_NAME_MAX_LENGTH = 128;
    /** 新应用每个 UTC 自然月默认允许创建的短链数。 */
    public static final long MONTHLY_LINK_LIMIT = 10_000L;
    /** 新应用每个 UTC 自然月默认允许的点击数。 */
    public static final long MONTHLY_CLICK_LIMIT = 1_000_000L;
    /** 初始化应用策略记录时写入的默认 HTTP 重定向状态码。 */
    public static final int REDIRECT_STATUS_CODE = 302;
    /** 初始化应用策略记录时默认不启用确认预览页。 */
    public static final boolean PREVIEW_ENABLED = false;
    /** 初始化应用策略记录时写入租户共享域名范围。 */
    public static final DomainScope DEFAULT_DOMAIN_SCOPE = DomainScope.TENANT_SHARED;

    private PlatformDefaults() {
    }
}

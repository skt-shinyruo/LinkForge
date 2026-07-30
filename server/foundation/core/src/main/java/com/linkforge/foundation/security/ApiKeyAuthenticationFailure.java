package com.linkforge.foundation.security;

/** API Key 认证可稳定区分的失败状态，不包含格式、哈希或租户等内部诊断。 */
public enum ApiKeyAuthenticationFailure {
    /** Key 缺失、格式错误、secret 不匹配、历史未绑定应用或其他不可用认证条件。 */
    INVALID,
    /** Key 已被管理端禁用，可映射为与无效 Key 不同的公开错误码。 */
    DISABLED
}

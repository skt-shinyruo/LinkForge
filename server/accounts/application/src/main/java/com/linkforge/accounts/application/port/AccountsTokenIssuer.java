package com.linkforge.accounts.application.port;

import java.util.Set;

/**
 * 登录成功后的访问令牌签发端口。
 *
 * <p>令牌携带签发时的邮箱、角色集合和 {@code tokenVersion} 快照，但不替代后续的租户/用户状态检查。
 * 签发不写业务数据库；由于令牌通常包含签发时间和过期时间，相同输入也不保证得到相同字符串。</p>
 */
public interface AccountsTokenIssuer {

    /**
     * 为已完成凭证和状态校验的用户签发令牌。
     *
     * @param roles 非空角色集合；其顺序不构成协议语义
     * @param tokenVersion 当前用户令牌版本，用于后续吊销校验
     * @return 可作为 Bearer 凭证的完整令牌字符串
     */
    String issueToken(long userId, long tenantId, String email, Set<String> roles, int tokenVersion);
}

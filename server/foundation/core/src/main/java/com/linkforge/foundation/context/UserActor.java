package com.linkforge.foundation.context;

import java.util.Set;

/**
 * 由管理 JWT 主体转换得到的用户 actor。
 *
 * <p>{@code roles} 是当前认证快照，用于应用层授权而不是数据库角色的实时事实源；会话撤销由外层
 * {@code tokenVersion} 和账户状态校验完成。email 可为空以兼容最小 JWT claim，但不能据此绕过用户 ID
 * 与租户边界。</p>
 */
public record UserActor(long tenantId, long userId, String email, Set<String> roles) implements ApplicationActor {
}

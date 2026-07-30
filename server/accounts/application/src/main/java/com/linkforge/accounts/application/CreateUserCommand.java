package com.linkforge.accounts.application;

import java.util.Set;

/**
 * 管理员创建用户的应用层命令；{@code roles} 为空或 {@code null} 时由服务归一化为普通用户角色。
 *
 * <p>{@code password} 是待哈希的短生命周期明文，不得记录日志、回传或直接持久化。</p>
 */
public record CreateUserCommand(String email, String password, Set<String> roles) {
}

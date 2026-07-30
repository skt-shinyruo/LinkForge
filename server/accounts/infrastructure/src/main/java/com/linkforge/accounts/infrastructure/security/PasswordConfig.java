package com.linkforge.accounts.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Accounts 凭据哈希的唯一装配点。
 *
 * <p>当前使用 Spring Security 默认强度的 BCrypt。变更算法或强度会影响新摘要格式和认证 CPU 成本，
 * 应同时设计旧摘要的渐进迁移，不能直接假设数据库中的历史摘要已重算。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

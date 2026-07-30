package com.linkforge.accounts.infrastructure.security;

import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 账户密码和 API Key secret 的单向哈希适配器。
 *
 * <p>编码和校验全部委托给 Spring Security 的 {@link PasswordEncoder}。默认装配为 BCrypt，
 * 每次编码都会生成随机盐，因此调用方不得通过比较两次编码结果判断相等，也不得持久化或记录明文。
 * 本适配器不做可逆加密、格式截断或失败降级。</p>
 */
@Component
public class SpringAccountsPasswordHasher implements AccountsPasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public SpringAccountsPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String encode(String raw) {
        return passwordEncoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }
}

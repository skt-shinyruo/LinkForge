package com.linkforge.accounts.infrastructure.security;

import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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

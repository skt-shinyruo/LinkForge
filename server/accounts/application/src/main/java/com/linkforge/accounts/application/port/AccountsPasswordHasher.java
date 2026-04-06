package com.linkforge.accounts.application.port;

public interface AccountsPasswordHasher {

    String encode(String raw);

    boolean matches(String raw, String encoded);
}

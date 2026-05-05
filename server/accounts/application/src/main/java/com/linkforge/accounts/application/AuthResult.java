package com.linkforge.accounts.application;

import com.linkforge.foundation.security.AuthPrincipal;

public record AuthResult(String token, AuthPrincipal principal) {
}

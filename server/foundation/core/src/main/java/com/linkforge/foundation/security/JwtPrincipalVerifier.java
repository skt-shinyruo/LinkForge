package com.linkforge.foundation.security;

public interface JwtPrincipalVerifier {

    AuthPrincipal parseToken(String token);
}

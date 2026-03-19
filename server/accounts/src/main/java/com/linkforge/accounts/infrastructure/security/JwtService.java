package com.linkforge.accounts.infrastructure.security;

import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class JwtService {

    private final SecurityProperties.Jwt jwt;
    private final SecretKey key;
    private final AccountsUserStore userStore;

    public JwtService(SecurityProperties properties, AccountsUserStore userStore) {
        this.jwt = properties == null ? null : properties.getJwt();
        this.userStore = userStore;
        String secret = jwt == null ? null : jwt.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret 不能为空，请通过环境变量 JWT_SECRET 配置");
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalArgumentException("JWT secret 太短（需要 >= 32 bytes），请通过环境变量 JWT_SECRET 配置");
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public String issueToken(long userId, long tenantId, String email, Set<String> roles, int tokenVersion) {
        long ttlSeconds = jwt == null ? 0 : jwt.getTtlSeconds();
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .issuer(jwt == null ? null : jwt.getIssuer())
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("tenantId", tenantId)
                .claim("email", email)
                .claim("roles", roles.stream().toList())
                .claim("tokenVersion", tokenVersion)
                .signWith(key)
                .compact();
    }

    public AuthPrincipal parseToken(String token) {
        Jws<Claims> jws = Jwts.parser()
                .requireIssuer(jwt == null ? null : jwt.getIssuer())
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);

        Claims c = jws.getPayload();
        long userId = Long.parseLong(c.getSubject());
        long tenantId = ((Number) c.get("tenantId")).longValue();
        String email = (String) c.get("email");
        List<String> roles = c.get("roles", List.class);
        Number tokenVersionClaim = c.get("tokenVersion", Number.class);
        int tokenVersion = tokenVersionClaim == null ? 0 : tokenVersionClaim.intValue();

        AccountsUserStore.UserData currentUser = userStore == null ? null : userStore.findById(userId);
        if (currentUser == null || currentUser.tenantId() == null || currentUser.tenantId() != tenantId) {
            throw new IllegalArgumentException("JWT user not found");
        }
        int currentTokenVersion = currentUser.tokenVersion() == null ? 0 : currentUser.tokenVersion();
        if (currentTokenVersion != tokenVersion) {
            throw new IllegalArgumentException("JWT token version stale");
        }

        return new AuthPrincipal(userId, tenantId, email, roles == null ? Set.of() : Set.copyOf(roles), tokenVersion);
    }
}

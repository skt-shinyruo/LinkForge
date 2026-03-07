package com.linkforge.accounts.infrastructure.security;

import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.config.AppProperties;
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

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        byte[] raw = properties.getSecurity().getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalArgumentException("JWT secret 太短（需要 >= 32 bytes），请通过环境变量 JWT_SECRET 配置");
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public String issueToken(long userId, long tenantId, String email, Set<String> roles) {
        long ttlSeconds = properties.getSecurity().getJwt().getTtlSeconds();
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("tenantId", tenantId)
                .claim("email", email)
                .claim("roles", roles.stream().toList())
                .signWith(key)
                .compact();
    }

    public AuthPrincipal parseToken(String token) {
        Jws<Claims> jws = Jwts.parser()
                .requireIssuer(properties.getSecurity().getJwt().getIssuer())
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);

        Claims c = jws.getPayload();
        long userId = Long.parseLong(c.getSubject());
        long tenantId = ((Number) c.get("tenantId")).longValue();
        String email = (String) c.get("email");
        List<String> roles = c.get("roles", List.class);
        return new AuthPrincipal(userId, tenantId, email, Set.copyOf(roles));
    }
}

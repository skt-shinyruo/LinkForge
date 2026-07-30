package com.linkforge.accounts.infrastructure.security;

import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.JwtPrincipalVerifier;
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

/**
 * Accounts 上下文的 JWT 签发与密码学验证边界。
 *
 * <p>构造时要求配置非空且 UTF-8 编码后至少 32 字节的 HMAC 密钥；配置错误会阻止 Bean 启动，
 * 避免以弱密钥运行。解析由 JJWT 校验签名、{@code exp} 和配置的 {@code iss}，异常原样交给
 * 上层认证过滤器统一映射，本类不把无效令牌降级为匿名的有效主体。</p>
 *
 * <p>{@code iat}/{@code exp} 从 {@link Instant} 生成并按 JWT NumericDate 表达，不依赖 JVM 本地时区。
 * 业务 claims 包含 {@code tenantId/email/roles/tokenVersion}，{@code sub} 为用户 ID。
 * JWT 只证明这些 claims 由服务签发；用户、租户当前状态及 tokenVersion 是否仍有效，必须由后续
 * {@code AccountStatusVerifier} 使用权威状态校验。</p>
 */
@Service
public class JwtService implements JwtPrincipalVerifier {

    private final SecurityProperties.Jwt jwt;
    private final SecretKey key;

    public JwtService(SecurityProperties properties) {
        this.jwt = properties == null ? null : properties.getJwt();
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

    /**
     * 使用当前时刻和配置的秒级 TTL 签发令牌。
     *
     * <p>{@code roles} 是必填集合；调用方负责传入已规范化的角色。TTL 不在此处修正，
     * 非正或过短配置会直接反映为不可用或立即过期的令牌，部署配置必须保证其为合理正值。</p>
     */
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

    /**
     * 校验并还原认证主体。
     *
     * <p>为兼容没有 {@code tokenVersion} 的既有令牌，缺失时按 {@code 0} 处理；角色缺失时按空集合处理。
     * 其他缺失、类型错误、签名错误、过期或 issuer 不匹配均由解析异常表示，不返回半有效主体。</p>
     */
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

        return new AuthPrincipal(userId, tenantId, email, roles == null ? Set.of() : Set.copyOf(roles), tokenVersion);
    }
}

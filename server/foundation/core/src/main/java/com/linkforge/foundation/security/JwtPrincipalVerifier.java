package com.linkforge.foundation.security;

/**
 * 将未受信任 JWT 文本验签并转换为 {@link AuthPrincipal} 的端口。
 *
 * <p>实现必须验证签名、过期时间、issuer 和所需 claims；成功只说明令牌密码学上有效，过滤器随后仍需通过
 * {@link AccountStatusVerifier} 核验用户/租户状态及 tokenVersion。</p>
 */
public interface JwtPrincipalVerifier {

    /** @throws RuntimeException token 缺失、畸形、验签失败或 claim 不满足要求时抛出 */
    AuthPrincipal parseToken(String token);
}

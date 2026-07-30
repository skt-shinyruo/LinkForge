package com.linkforge.app.security;

import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AccountStatusVerifier;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.JwtPrincipalVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理 API 的 JWT 认证过滤器。
 *
 * <p>按优先级读取 {@code Authorization: Bearer}，再在 Cookie 模式开启时读取指定 Cookie。Bearer JWT 无效或
 * 过长时立即返回 401；Cookie JWT 无效或过长时清除 Cookie 后继续链路，使公开登录/注册端点仍可用。验签成功后
 * 仍必须通过 {@link AccountStatusVerifier} 检查用户、租户和 tokenVersion，JWT 本身不是即时账户状态事实。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final int MAX_JWT_TOKEN_LEN = 4096;

    private final JwtPrincipalVerifier jwtService;
    private final AccountStatusVerifier accountStatusService;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final SecurityProperties securityProperties;

    private enum JwtTokenSource {
        BEARER_HEADER,
        COOKIE
    }

    private record ResolvedJwtToken(String token, JwtTokenSource source) {
    }

    public JwtAuthenticationFilter(
            JwtPrincipalVerifier jwtService,
            AccountStatusVerifier accountStatusService,
            ApiErrorResponseWriter errorResponseWriter,
            SecurityProperties securityProperties
    ) {
        this.jwtService = jwtService;
        this.accountStatusService = accountStatusService;
        this.errorResponseWriter = errorResponseWriter;
        this.securityProperties = securityProperties;
    }

    /**
     * 解析凭据、建立 SecurityContext，或按凭据来源执行对应的失败策略。
     *
     * <p>若上游已建立 Authentication，本过滤器不覆盖它，保证优先安全链和测试/代理注入的认证上下文可保留。</p>
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        ResolvedJwtToken resolved = resolveJwtToken(request);
        if (resolved != null && resolved.token() != null && !resolved.token().isBlank()) {
            String token = resolved.token();
            if (token.length() > MAX_JWT_TOKEN_LEN) {
                if (resolved.source() == JwtTokenSource.COOKIE) {
                    clearJwtCookieIfEnabled(response);
                    filterChain.doFilter(request, response);
                    return;
                }
                errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                return;
            }

            try {
                AuthPrincipal principal = jwtService.parseToken(token);
                accountStatusService.requireActiveUserAndTenant(
                        principal.getUserId(),
                        principal.getTenantId(),
                        principal.getTokenVersion()
                );
                Set<SimpleGrantedAuthority> authorities = principal.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toUnmodifiableSet());
                UsernamePasswordAuthenticationToken at =
                        new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
                SecurityContextHolder.getContext().setAuthentication(at);
            } catch (BusinessException e) {
                // 已认证但被禁用：保留 Cookie，并返回明确业务错误而非伪装为令牌损坏。
                errorResponseWriter.write(response, e.getErrorCode().getHttpStatus(), e.getErrorCode(), e.getMessage());
                return;
            } catch (Exception e) {
                if (resolved.source() == JwtTokenSource.COOKIE) {
                    // Cookie 模式清除坏令牌并继续，保证 permitAll 端点仍可访问。
                    clearJwtCookieIfEnabled(response);
                } else {
                    errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 解析 JWT 来源，优先 Bearer header；Cookie 仅在配置开启时参与。
     *
     * <p>本方法不验证 token 内容，空/无效 token 由调用方统一处理。</p>
     */
    private ResolvedJwtToken resolveJwtToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return new ResolvedJwtToken(auth.substring("Bearer ".length()).trim(), JwtTokenSource.BEARER_HEADER);
        }

        SecurityProperties.Jwt jwt = securityProperties == null ? null : securityProperties.getJwt();
        if (jwt == null || !jwt.isCookieEnabled()) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }

        String cookieName = jwt.getCookieName();
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "lf_token";
        }
        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName())) {
                return new ResolvedJwtToken(c.getValue(), JwtTokenSource.COOKIE);
            }
        }
        return null;
    }

    /** 删除配置的 JWT Cookie；未启用 Cookie 模式、响应为空时保持 no-op。 */
    private void clearJwtCookieIfEnabled(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        SecurityProperties.Jwt jwt = securityProperties == null ? null : securityProperties.getJwt();
        if (jwt == null || !jwt.isCookieEnabled()) {
            return;
        }

        String name = jwt.getCookieName();
        if (name == null || name.isBlank()) {
            name = "lf_token";
        }
        String sameSite = jwt.getCookieSameSite();
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        }

        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(jwt.isCookieSecure())
                .path("/")
                .sameSite(sameSite)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

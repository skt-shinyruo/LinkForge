package com.linkforge.app.security;

import com.linkforge.accounts.application.AccountStatusService;
import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AuthPrincipal;
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
 * JWT authentication filter for management APIs.
 *
 * <p>Accepted sources:</p>
 * <ul>
 *   <li>{@code Authorization: Bearer ...} header</li>
 *   <li>Cookie token (when cookie mode enabled)</li>
 * </ul>
 *
 * <p>Important behavior parity with the previous composite filter:</p>
 * <ul>
 *   <li>Invalid/oversized cookie JWT: clear cookie and continue chain (do not hard 401).</li>
 *   <li>Invalid/oversized Bearer JWT: strict 401.</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final int MAX_JWT_TOKEN_LEN = 4096;

    private final JwtService jwtService;
    private final AccountStatusService accountStatusService;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final SecurityProperties securityProperties;

    private enum JwtTokenSource {
        BEARER_HEADER,
        COOKIE
    }

    private record ResolvedJwtToken(String token, JwtTokenSource source) {
    }

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AccountStatusService accountStatusService,
            ApiErrorResponseWriter errorResponseWriter,
            SecurityProperties securityProperties
    ) {
        this.jwtService = jwtService;
        this.accountStatusService = accountStatusService;
        this.errorResponseWriter = errorResponseWriter;
        this.securityProperties = securityProperties;
    }

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
                accountStatusService.requireActiveUserAndTenant(principal.getUserId(), principal.getTenantId());
                Set<SimpleGrantedAuthority> authorities = principal.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toUnmodifiableSet());
                UsernamePasswordAuthenticationToken at =
                        new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
                SecurityContextHolder.getContext().setAuthentication(at);
            } catch (BusinessException e) {
                // Authenticated-but-disabled: do not clear cookie; return explicit 403 business code.
                errorResponseWriter.write(response, e.getErrorCode().getHttpStatus(), e.getErrorCode(), e.getMessage());
                return;
            } catch (Exception e) {
                if (resolved.source() == JwtTokenSource.COOKIE) {
                    // Cookie mode: clear and continue (permitAll endpoints must still work).
                    clearJwtCookieIfEnabled(response);
                } else {
                    errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

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

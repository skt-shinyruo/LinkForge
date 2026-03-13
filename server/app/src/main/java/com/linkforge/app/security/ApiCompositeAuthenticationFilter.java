package com.linkforge.app.security;

import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.openapi.OpenApiErrorCode;
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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Composite auth filter for API routes.
 *
 * <p>Rationale: avoid relying on Spring Security filter ordering when multiple authentication
 * mechanisms co-exist. This filter applies a deterministic policy:
 *
 * <ol>
 *   <li>Try JWT (Bearer / cookie) if present.</li>
 *   <li>If request is OpenAPI route and still unauthenticated, require X-API-Key.</li>
 * </ol>
 */
@Component
public class ApiCompositeAuthenticationFilter extends OncePerRequestFilter {

    private static final String OPENAPI_PREFIX = "/api/v1/open/";
    private static final String HEADER_API_KEY = "X-API-Key";
    private static final int MAX_JWT_TOKEN_LEN = 4096;

    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final SecurityProperties securityProperties;

    private enum JwtTokenSource {
        BEARER_HEADER,
        COOKIE
    }

    private record ResolvedJwtToken(String token, JwtTokenSource source) {
    }

    public ApiCompositeAuthenticationFilter(
            JwtService jwtService,
            ApiKeyService apiKeyService,
            ApiErrorResponseWriter errorResponseWriter,
            SecurityProperties securityProperties
    ) {
        this.jwtService = jwtService;
        this.apiKeyService = apiKeyService;
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

        // 1) JWT
        ResolvedJwtToken resolved = resolveJwtToken(request);
        if (resolved != null && resolved.token() != null && !resolved.token().isBlank()) {
            try {
                String token = resolved.token();
                if (token.length() > MAX_JWT_TOKEN_LEN) {
                    // Defensive: avoid letting an attacker force large JWT parsing / base64 decode work.
                    if (resolved.source() == JwtTokenSource.COOKIE) {
                        clearJwtCookieIfEnabled(response);
                    } else {
                        errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                        return;
                    }
                } else {
                    AuthPrincipal principal = jwtService.parseToken(token);
                    var authorities = principal.getRoles().stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .collect(java.util.stream.Collectors.toSet());
                    UsernamePasswordAuthenticationToken at =
                            new UsernamePasswordAuthenticationToken(principal, token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(at);
                }
            } catch (Exception e) {
                if (resolved.source() == JwtTokenSource.COOKIE) {
                    // Cookie mode: do not hard-fail here, otherwise an expired/bad cookie will block
                    // permitAll endpoints (login/logout/csrf) and cause a “locked out” UX.
                    clearJwtCookieIfEnabled(response);
                } else {
                    // Bearer header is an explicit auth attempt: keep strict 401 behavior.
                    errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                    return;
                }
            }
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) OpenAPI key (only for /api/v1/open/**)
        String path = pathWithinApp(request);
        if (path != null && path.startsWith(OPENAPI_PREFIX)) {
            String apiKey = request.getHeader(HEADER_API_KEY);
            if (apiKey == null || apiKey.isBlank()) {
                errorResponseWriter.write(
                        response,
                        OpenApiErrorCode.API_KEY_INVALID.getHttpStatus(),
                        OpenApiErrorCode.API_KEY_INVALID
                );
                return;
            }

            try {
                ApiKeyService.ApiKeyAuthResult r = apiKeyService.authenticate(apiKey.trim());
                AuthPrincipal principal = new AuthPrincipal(
                        0L,
                        r.tenantId(),
                        null,
                        Set.of(Roles.OPENAPI)
                );
                UsernamePasswordAuthenticationToken at = new UsernamePasswordAuthenticationToken(
                        principal,
                        apiKey,
                        Set.of(new SimpleGrantedAuthority("ROLE_" + Roles.OPENAPI))
                );
                SecurityContextHolder.getContext().setAuthentication(at);
            } catch (ApiKeyService.ApiKeyAuthException e) {
                AppErrorCode ec = e == null ? null : e.errorCode();
                if (ec == null) {
                    ec = OpenApiErrorCode.API_KEY_INVALID;
                }
                errorResponseWriter.write(response, ec.getHttpStatus(), ec);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String pathWithinApp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
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

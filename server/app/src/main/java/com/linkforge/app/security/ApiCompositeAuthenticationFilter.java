package com.linkforge.app.security;

import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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

    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final SecurityProperties securityProperties;

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
        String token = resolveJwtToken(request);
        if (token != null && !token.isBlank()) {
            try {
                AuthPrincipal principal = jwtService.parseToken(token);
                var authorities = principal.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(java.util.stream.Collectors.toSet());
                UsernamePasswordAuthenticationToken at =
                        new UsernamePasswordAuthenticationToken(principal, token, authorities);
                SecurityContextHolder.getContext().setAuthentication(at);
            } catch (Exception e) {
                errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
                return;
            }
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) OpenAPI key (only for /api/v1/open/**)
        String uri = request == null ? null : request.getRequestURI();
        if (uri != null && uri.startsWith(OPENAPI_PREFIX)) {
            String apiKey = request.getHeader(HEADER_API_KEY);
            if (apiKey == null || apiKey.isBlank()) {
                errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, OpenApiErrorCode.API_KEY_INVALID);
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
                errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, e.errorCode());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveJwtToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
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
                return c.getValue();
            }
        }
        return null;
    }
}


package com.linkforge.app.security;

import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.config.AppProperties;
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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final AppProperties properties;

    public JwtAuthenticationFilter(JwtService jwtService, ApiErrorResponseWriter errorResponseWriter, AppProperties properties) {
        this.jwtService = jwtService;
        this.errorResponseWriter = errorResponseWriter;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 若已由其他方式认证（例如 OpenAPI Key），则跳过 JWT 解析
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthPrincipal principal = jwtService.parseToken(token);
            Set<SimpleGrantedAuthority> authorities = principal.getRoles().stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(java.util.stream.Collectors.toSet());
            UsernamePasswordAuthenticationToken at =
                    new UsernamePasswordAuthenticationToken(principal, token, authorities);
            SecurityContextHolder.getContext().setAuthentication(at);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // Token 无效：直接返回 401，避免进入后续链路
            errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }

        if (!properties.getSecurity().getJwt().isCookieEnabled()) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String cookieName = properties.getSecurity().getJwt().getCookieName();
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

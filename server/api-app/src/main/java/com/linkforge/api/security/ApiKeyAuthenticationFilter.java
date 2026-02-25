package com.linkforge.api.security;

import com.linkforge.api.iam.service.ApiKeyService;
import com.linkforge.platform.api.ApiErrorResponseWriter;
import com.linkforge.platform.api.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ApiErrorResponseWriter errorResponseWriter) {
        this.apiKeyService = apiKeyService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/open/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // OpenAPI：若已存在 JWT 认证，也允许通过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.API_KEY_INVALID);
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
            filterChain.doFilter(request, response);
        } catch (ApiKeyService.ApiKeyAuthException e) {
            errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, e.errorCode());
        }
    }
}

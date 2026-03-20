package com.linkforge.app.security;

import com.linkforge.accounts.application.AccountStatusService;
import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * OpenAPI-only authentication filter.
 *
 * <p>Only used on OpenAPI routes (e.g. {@code /api/v1/open/**}). It MUST authenticate via {@code X-API-Key}
 * and must not accept JWT/cookie auth.</p>
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final AccountStatusService accountStatusService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public ApiKeyAuthenticationFilter(
            ApiKeyService apiKeyService,
            AccountStatusService accountStatusService,
            ApiErrorResponseWriter errorResponseWriter
    ) {
        this.apiKeyService = apiKeyService;
        this.accountStatusService = accountStatusService;
        this.errorResponseWriter = errorResponseWriter;
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

        String apiKey = request == null ? null : request.getHeader(HEADER_API_KEY);
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
            accountStatusService.requireActiveTenant(r.tenantId());

            AuthPrincipal principal = new AuthPrincipal(
                    0L,
                    r.tenantId(),
                    null,
                    Set.of(Roles.OPENAPI),
                    r.apiKeyId(),
                    r.applicationId()
            );
            UsernamePasswordAuthenticationToken at = new UsernamePasswordAuthenticationToken(
                    principal,
                    "N/A",
                    Set.of(new SimpleGrantedAuthority("ROLE_" + Roles.OPENAPI))
            );
            SecurityContextHolder.getContext().setAuthentication(at);
            filterChain.doFilter(request, response);
        } catch (ApiKeyService.ApiKeyAuthException e) {
            AppErrorCode ec = e == null ? null : e.errorCode();
            if (ec == null) {
                ec = OpenApiErrorCode.API_KEY_INVALID;
            }
            errorResponseWriter.write(response, ec.getHttpStatus(), ec);
        } catch (BusinessException e) {
            AppErrorCode ec = e.getErrorCode() == null ? OpenApiErrorCode.API_KEY_INVALID : e.getErrorCode();
            errorResponseWriter.write(response, ec.getHttpStatus(), ec, e.getMessage());
        } catch (Exception e) {
            errorResponseWriter.write(
                    response,
                    OpenApiErrorCode.API_KEY_INVALID.getHttpStatus(),
                    OpenApiErrorCode.API_KEY_INVALID
            );
        }
    }
}

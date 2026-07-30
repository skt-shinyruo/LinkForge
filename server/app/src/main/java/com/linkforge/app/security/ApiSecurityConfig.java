package com.linkforge.app.security;

import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AccountStatusVerifier;
import com.linkforge.foundation.security.JwtPrincipalVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 管理 API 的 JWT 安全过滤链。
 *
 * <p>链路只匹配 {@code /api/**}，而优先级更高的 OpenAPI 链先匹配 {@code /api/v1/open/**}；Redirect
 * {@code /r/**} 不进入任何管理认证过滤器。Cookie JWT 模式开启时启用双提交 CSRF，显式 Bearer header
 * 请求被排除，避免把浏览器自动携带 Cookie 和 API 客户端凭据混为同一种威胁模型。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfig {

    /**
     * 组装管理 API 的无状态过滤链与 JWT 认证过滤器。
     *
     * <p>注册、登录、登出和 CSRF token 获取是公开入口，其余 {@code /api/v1/**} 需要已认证主体；细粒度
     * 角色和租户判断继续由方法安全与应用服务执行。</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            JwtPrincipalVerifier jwtService,
            AccountStatusVerifier accountStatusService,
            ApiErrorResponseWriter errorResponseWriter,
            SecurityProperties securityProperties
    ) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
                errorResponseWriter,
                securityProperties
        );
        http
                // 单体中 Redirect 端点不应被管理 API 的认证失败格式或 CSRF 策略影响。
                .securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Cookie 模式必须具备 CSRF 双提交保护；显式 header 凭据不属于浏览器自动携带 Cookie 场景。
        if (securityProperties != null
                && securityProperties.getJwt() != null
                && securityProperties.getJwt().isCookieEnabled()) {
            CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
            repo.setCookiePath("/");
            // SPA 双提交模式使用原始 token，保证 cookie 值可直接作为 header 发送。
            CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(repo)
                    .csrfTokenRequestHandler(requestHandler)
                    // 仅 Bearer header 由该链处理；API Key 路径已由优先 OpenAPI 链独立处理。
                    .ignoringRequestMatchers((HttpServletRequest req) -> {
                        if (req == null) {
                            return false;
                        }
                        String auth = req.getHeader("Authorization");
                        if (auth == null || auth.isBlank()) {
                            return false;
                        }
                        if (!auth.startsWith("Bearer ")) {
                            return false;
                        }
                        String token = auth.substring("Bearer ".length()).trim();
                        return !token.isBlank();
                    })
            );
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        return http.build();
    }
}

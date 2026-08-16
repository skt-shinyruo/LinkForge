package com.linkforge.app.security;

import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 启用控制器与应用边界使用的 Spring 方法安全注解。
 *
 * <p>该配置本身不定义 HTTP 路由规则；JWT 与 API Key 的过滤链分别由专用配置类装配。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    AuthenticationEntryPoint restAuthenticationEntryPoint(ApiErrorResponseWriter writer) {
        return (request, response, exception) ->
                writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    @Bean
    AccessDeniedHandler restAccessDeniedHandler(ApiErrorResponseWriter writer) {
        return (request, response, exception) ->
                writer.write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
    }
}

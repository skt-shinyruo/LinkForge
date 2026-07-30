package com.linkforge.app.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 启用控制器与应用边界使用的 Spring 方法安全注解。
 *
 * <p>该配置本身不定义 HTTP 路由规则；JWT 与 API Key 的过滤链分别由专用配置类装配。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {
}

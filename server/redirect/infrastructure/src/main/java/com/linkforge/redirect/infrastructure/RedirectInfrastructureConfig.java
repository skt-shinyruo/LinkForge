package com.linkforge.redirect.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Redirect 基础设施组件扫描入口。
 *
 * <p>这里装配 Redis 缓存与限流实现；应用层仅依赖端口，因此 Redis 不可用时的降级语义仍由调用方显式
 * 决定。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RedirectInfrastructureConfig.class)
public class RedirectInfrastructureConfig {
}

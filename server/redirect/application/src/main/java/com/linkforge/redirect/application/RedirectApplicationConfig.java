package com.linkforge.redirect.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Redirect 应用层组件扫描入口。
 *
 * <p>该配置只装配用例与策略，不直接导入 Redis、HTTP 或其他上下文的实现；运行时组合由
 * {@code RedirectRuntimeModule} 统一完成。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RedirectApplicationConfig.class)
public class RedirectApplicationConfig {
}

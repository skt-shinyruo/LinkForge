package com.linkforge.redirect.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Redirect HTTP、边缘风控和启动校验的组件扫描入口。
 *
 * <p>该层负责把应用决策映射为协议响应，不拥有短链事实或缓存一致性策略。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RedirectInterfacesConfig.class)
public class RedirectInterfacesConfig {
}

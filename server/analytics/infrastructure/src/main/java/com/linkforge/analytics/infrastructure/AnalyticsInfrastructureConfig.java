package com.linkforge.analytics.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Analytics 基础设施模块的组件扫描入口。
 *
 * <p>它只声明本上下文的 Redis、MyBatis、任务和适配器组件，不承载业务规则；跨模块装配由 runtime
 * 模块完成。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AnalyticsInfrastructureConfig.class)
public class AnalyticsInfrastructureConfig {
}

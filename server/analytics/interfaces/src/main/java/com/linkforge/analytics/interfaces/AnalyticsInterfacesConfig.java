package com.linkforge.analytics.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Analytics HTTP 适配层的显式组件扫描入口。
 *
 * <p>运行时模块只导入该配置以发现报表 Controller 和其 Web 适配器所在包；它不负责创建
 * 查询、统计投影或 Redis/MyBatis 基础设施 Bean。保持扫描边界在 interfaces 模块内，避免
 * 应用层被 Web 运行时意外重复装配。
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AnalyticsInterfacesConfig.class)
public class AnalyticsInterfacesConfig {
}

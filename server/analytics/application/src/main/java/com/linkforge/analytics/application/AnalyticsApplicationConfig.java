package com.linkforge.analytics.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Analytics 应用层的 Spring 组合入口。
 *
 * <p>此扫描范围只装配用例编排、查询门面和跨上下文端口依赖；Redis Stream、MyBatis 和定时任务等
 * 技术实现由相应的 infrastructure 模块负责。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AnalyticsApplicationConfig.class)
public class AnalyticsApplicationConfig {
}

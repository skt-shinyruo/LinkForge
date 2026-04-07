package com.linkforge.analytics.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AnalyticsApplicationConfig.class)
public class AnalyticsApplicationConfig {
}

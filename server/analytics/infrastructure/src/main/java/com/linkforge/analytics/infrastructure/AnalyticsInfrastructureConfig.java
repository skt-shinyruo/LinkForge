package com.linkforge.analytics.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AnalyticsInfrastructureConfig.class)
public class AnalyticsInfrastructureConfig {
}

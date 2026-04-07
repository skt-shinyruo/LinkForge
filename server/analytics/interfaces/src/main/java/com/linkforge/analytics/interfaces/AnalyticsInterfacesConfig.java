package com.linkforge.analytics.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AnalyticsInterfacesConfig.class)
public class AnalyticsInterfacesConfig {
}

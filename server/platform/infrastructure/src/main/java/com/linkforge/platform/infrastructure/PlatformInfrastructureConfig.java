package com.linkforge.platform.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = PlatformInfrastructureConfig.class)
public class PlatformInfrastructureConfig {
}

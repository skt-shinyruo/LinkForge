package com.linkforge.redirect.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RedirectInfrastructureConfig.class)
public class RedirectInfrastructureConfig {
}

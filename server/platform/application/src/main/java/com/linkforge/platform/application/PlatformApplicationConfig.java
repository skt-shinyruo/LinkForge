package com.linkforge.platform.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = PlatformApplicationConfig.class)
public class PlatformApplicationConfig {
}

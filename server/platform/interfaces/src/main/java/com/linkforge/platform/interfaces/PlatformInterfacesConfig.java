package com.linkforge.platform.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = PlatformInterfacesConfig.class)
public class PlatformInterfacesConfig {
}

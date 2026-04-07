package com.linkforge.shortlink.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ShortlinkInterfacesConfig.class)
public class ShortlinkInterfacesConfig {
}

package com.linkforge.redirect.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RedirectApplicationConfig.class)
public class RedirectApplicationConfig {
}

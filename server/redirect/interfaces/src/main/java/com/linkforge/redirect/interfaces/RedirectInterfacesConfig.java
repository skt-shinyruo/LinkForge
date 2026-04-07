package com.linkforge.redirect.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RedirectInterfacesConfig.class)
public class RedirectInterfacesConfig {
}

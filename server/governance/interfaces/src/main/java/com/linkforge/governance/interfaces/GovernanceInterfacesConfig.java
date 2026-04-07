package com.linkforge.governance.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = GovernanceInterfacesConfig.class)
public class GovernanceInterfacesConfig {
}

package com.linkforge.governance.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = GovernanceApplicationConfig.class)
public class GovernanceApplicationConfig {
}

package com.linkforge.governance.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = GovernanceInfrastructureConfig.class)
public class GovernanceInfrastructureConfig {
}

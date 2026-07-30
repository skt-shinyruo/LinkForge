package com.linkforge.governance.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Governance HTTP 接口层的 Spring 组件入口。 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = GovernanceInterfacesConfig.class)
public class GovernanceInterfacesConfig {
}

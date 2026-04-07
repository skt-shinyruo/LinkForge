package com.linkforge.accounts.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AccountsInfrastructureConfig.class)
public class AccountsInfrastructureConfig {
}

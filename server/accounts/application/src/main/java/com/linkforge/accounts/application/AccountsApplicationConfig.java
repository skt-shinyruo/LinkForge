package com.linkforge.accounts.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AccountsApplicationConfig.class)
public class AccountsApplicationConfig {
}

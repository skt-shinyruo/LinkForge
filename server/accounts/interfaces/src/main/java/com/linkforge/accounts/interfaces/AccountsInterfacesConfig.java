package com.linkforge.accounts.interfaces;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AccountsInterfacesConfig.class)
public class AccountsInterfacesConfig {
}

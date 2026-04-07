package com.linkforge.accounts.interfaces;

import com.linkforge.accounts.application.AccountsApplicationConfig;
import com.linkforge.accounts.infrastructure.AccountsInfrastructureConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        AccountsApplicationConfig.class,
        AccountsInfrastructureConfig.class,
        AccountsInterfacesConfig.class
})
public class AccountsRuntimeModule {
}

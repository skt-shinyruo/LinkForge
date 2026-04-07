package com.linkforge.app.compose;

import com.linkforge.governance.application.GovernanceApplicationConfig;
import com.linkforge.governance.infrastructure.GovernanceInfrastructureConfig;
import com.linkforge.governance.interfaces.GovernanceInterfacesConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        GovernanceApplicationConfig.class,
        GovernanceInfrastructureConfig.class,
        GovernanceInterfacesConfig.class
})
public class GovernanceModule {
}

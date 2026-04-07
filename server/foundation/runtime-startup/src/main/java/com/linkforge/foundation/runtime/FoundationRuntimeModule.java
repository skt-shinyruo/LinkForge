package com.linkforge.foundation.runtime;

import com.linkforge.foundation.runtime.persistence.FoundationRuntimePersistenceModule;
import com.linkforge.foundation.runtime.security.FoundationRuntimeSecurityModule;
import com.linkforge.foundation.runtime.startup.FoundationRuntimeStartupModule;
import com.linkforge.foundation.runtime.tx.FoundationRuntimeTxModule;
import com.linkforge.foundation.runtime.web.FoundationRuntimeWebModule;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        FoundationRuntimeWebModule.class,
        FoundationRuntimeSecurityModule.class,
        FoundationRuntimePersistenceModule.class,
        FoundationRuntimeTxModule.class,
        FoundationRuntimeStartupModule.class
})
public class FoundationRuntimeModule {
}

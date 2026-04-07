package com.linkforge.foundation.runtime.tx;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        SpringPostCommitHookAdapter.class,
        SpringRequiresNewTransactionAdapter.class
})
public class FoundationRuntimeTxModule {
}

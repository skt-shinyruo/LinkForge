package com.linkforge.foundation.runtime.tx;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 将事务后钩子和独立事务端口作为 Foundation 运行时能力显式导出。 */
@Configuration(proxyBeanMethods = false)
@Import({
        SpringPostCommitHookAdapter.class,
        SpringRequiresNewTransactionAdapter.class
})
public class FoundationRuntimeTxModule {
}

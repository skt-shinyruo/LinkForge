package com.linkforge.foundation.runtime;

import com.linkforge.foundation.runtime.persistence.FoundationRuntimePersistenceModule;
import com.linkforge.foundation.runtime.security.FoundationRuntimeSecurityModule;
import com.linkforge.foundation.runtime.startup.FoundationRuntimeStartupModule;
import com.linkforge.foundation.runtime.tx.FoundationRuntimeTxModule;
import com.linkforge.foundation.runtime.web.FoundationRuntimeWebModule;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Foundation 运行时能力的显式组合根。
 *
 * <p>该模块不扫描业务上下文；它只组合 HTTP、安全、持久化、事务和启动时间基础设施，让应用入口可明确列出
 * 依赖的运行时能力。</p>
 */
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

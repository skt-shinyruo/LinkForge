package com.linkforge.redirect.runtime;

import com.linkforge.redirect.application.RedirectApplicationConfig;
import com.linkforge.redirect.infrastructure.RedirectInfrastructureConfig;
import com.linkforge.redirect.interfaces.RedirectInterfacesConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redirect 限界上下文的运行时组合根。
 *
 * <p>App 只导入此模块，而不是跨包扫描 Redirect 的内部实现；新增层级模块时应在这里显式登记，以保持
 * 装配边界可审计。</p>
 */
@Configuration(proxyBeanMethods = false)
@Import({
        RedirectApplicationConfig.class,
        RedirectInfrastructureConfig.class,
        RedirectInterfacesConfig.class
})
public class RedirectRuntimeModule {
}

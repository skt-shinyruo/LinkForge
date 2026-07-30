package com.linkforge.foundation.runtime.startup;

import com.linkforge.foundation.runtime.time.TimeConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Foundation 启动期通用 Bean 的显式导出模块。
 *
 * <p>当前只导出 UTC {@code Clock}，不执行配置校验，也不自动发现 {@link StartupCheck}；应用组合根负责
 * 收集各上下文检查并决定启动是否失败。</p>
 */
@Configuration(proxyBeanMethods = false)
@Import(TimeConfig.class)
public class FoundationRuntimeStartupModule {
}

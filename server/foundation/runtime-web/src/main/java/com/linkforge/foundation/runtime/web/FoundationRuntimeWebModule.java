package com.linkforge.foundation.runtime.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Foundation 的 HTTP 运行时导出模块。
 *
 * <p>通过显式导入而不是跨上下文包扫描注册 CORS 与请求关联基础设施，保持可执行层依赖可审计。</p>
 */
@Configuration(proxyBeanMethods = false)
@Import({
        CorsConfig.class,
        RequestIdFilter.class
})
public class FoundationRuntimeWebModule {
}

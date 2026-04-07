package com.linkforge.foundation.runtime.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        CorsConfig.class,
        RequestIdFilter.class
})
public class FoundationRuntimeWebModule {
}

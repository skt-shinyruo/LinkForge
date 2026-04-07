package com.linkforge.foundation.runtime.startup;

import com.linkforge.foundation.runtime.time.TimeConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(TimeConfig.class)
public class FoundationRuntimeStartupModule {
}

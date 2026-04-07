package com.linkforge.foundation.runtime.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Runtime MyBatis wiring for foundation-owned integration-event persistence.
 *
 * <p>This configuration stays under {@code foundation.runtime.persistence} because it registers
 * mapper scanning for live Spring infrastructure. The shared-library foundation packages remain
 * free of runtime bean registration so bounded-context dependencies stay explicit.
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.linkforge.foundation.runtime.persistence.mapper", annotationClass = Mapper.class)
public class IntegrationEventMybatisConfig {
}

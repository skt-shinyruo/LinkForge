package com.linkforge.foundation.eventing;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.linkforge.foundation.eventing.mapper", annotationClass = Mapper.class)
public class IntegrationEventMybatisConfig {
}


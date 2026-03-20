package com.linkforge.platform.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.linkforge.platform.infrastructure.persistence.mapper",
        annotationClass = Mapper.class
)
public class PlatformMybatisConfig {
}

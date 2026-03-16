package com.linkforge.redirect.infrastructure.projection;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.linkforge.redirect.infrastructure.projection", annotationClass = Mapper.class)
public class RedirectProjectionMybatisConfig {
}


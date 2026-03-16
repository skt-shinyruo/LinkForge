package com.linkforge.shortlink.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.linkforge.shortlink.infrastructure.persistence.mapper",
        annotationClass = Mapper.class
)
public class ShortlinkMybatisConfig {
}


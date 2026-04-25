package com.linkforge.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = {
                "com.linkforge.analytics.infrastructure.persistence.mapper",
                "com.linkforge.analytics.infrastructure.catalog"
        },
        annotationClass = Mapper.class
)
public class AnalyticsMybatisConfig {
}

package com.linkforge.accounts.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.linkforge.accounts.infrastructure.persistence.mapper",
        annotationClass = Mapper.class
)
public class AccountsMybatisConfig {
}

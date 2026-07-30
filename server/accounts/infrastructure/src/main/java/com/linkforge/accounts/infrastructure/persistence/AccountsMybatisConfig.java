package com.linkforge.accounts.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Accounts MyBatis Mapper 的显式扫描边界。
 *
 * <p>仅注册指定包内带 {@link Mapper} 的接口，避免把同包的普通端口或适配器误识别为 Mapper。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.linkforge.accounts.infrastructure.persistence.mapper",
        annotationClass = Mapper.class
)
public class AccountsMybatisConfig {
}

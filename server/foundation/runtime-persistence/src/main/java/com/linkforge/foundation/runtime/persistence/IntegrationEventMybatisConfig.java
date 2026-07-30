package com.linkforge.foundation.runtime.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Foundation 所有的集成事件持久化 MyBatis 装配。
 *
 * <p>扫描范围仅限本模块的 integration-event mapper，不承担 Accounts、Shortlink 等上下文 mapper 的扫描。
 * 这样共享 core 保持无 Spring Bean 注册，运行时基础设施依赖仍由应用组合根显式导入。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.linkforge.foundation.runtime.persistence.mapper", annotationClass = Mapper.class)
public class IntegrationEventMybatisConfig {
}

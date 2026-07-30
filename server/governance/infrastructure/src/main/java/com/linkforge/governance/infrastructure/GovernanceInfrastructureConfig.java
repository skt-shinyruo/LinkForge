package com.linkforge.governance.infrastructure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Governance 基础设施层的 Spring 组件入口。
 *
 * <p>该配置负责发现持久化适配器等基础设施组件；MyBatis mapper 的扫描规则由
 * {@code GovernanceMybatisConfig} 单独声明。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = GovernanceInfrastructureConfig.class)
public class GovernanceInfrastructureConfig {
}

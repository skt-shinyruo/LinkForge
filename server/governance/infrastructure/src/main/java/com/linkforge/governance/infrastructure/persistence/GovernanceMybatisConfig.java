package com.linkforge.governance.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Governance MyBatis mapper 的扫描边界。
 *
 * <p>只注册指定包中显式标注 {@link Mapper} 的接口；SQL 与结果映射仍由同命名空间的 XML
 * 提供，本配置不会扫描实体或应用层端口。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.linkforge.governance.infrastructure.persistence.mapper",
        annotationClass = Mapper.class
)
public class GovernanceMybatisConfig {
}

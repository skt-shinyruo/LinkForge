package com.linkforge.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 Analytics 的 MyBatis mapper。
 *
 * <p>除持久化 mapper 外，链接目录 mapper 位于 {@code catalog} 包，也必须在这里扫描；遗漏后会在运行期
 * 以缺少 Bean 的形式暴露，而不是由组件扫描自动发现。</p>
 */
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

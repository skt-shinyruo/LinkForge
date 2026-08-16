package com.linkforge.app.config;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用级 MyBatis mapper 扫描装配。
 *
 * <p>本配置故意不扫描业务 mapper：每个 bounded context 通过自己的 {@code @MapperScan} 导出 mapper，避免
 * 根应用把上下文内部持久化接口隐式混入。这里保留空 scanner 仅为兼容 MyBatis starter 的空包告警处理，
 * 不是等待未来迁移的临时扫描器。</p>
 */
@Configuration
public class MybatisConfig {

    /**
     * 注册不扫描任何包的兼容 scanner。
     *
     * <p>真实 mapper 注册分别由 Accounts、Shortlink、Analytics、Platform、Governance 和 Foundation
     * runtime 配置完成。</p>
     */
    @Bean
    static MapperScannerConfigurer mybatisMapperScannerConfigurer() {
        // 真实 mapper 由各上下文配置扫描；此处仅抑制 starter 对全局空扫描器的兼容告警。
        return new NoOpMapperScannerConfigurer();
    }

    private static final class NoOpMapperScannerConfigurer extends MapperScannerConfigurer {

        @Override
        public void afterPropertiesSet() {
            // 故意不配置包扫描，避免根应用越过上下文边界发现 mapper。
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            // 同上；实际 mapper Bean 已由各上下文的 @MapperScan 注册。
        }
    }
}

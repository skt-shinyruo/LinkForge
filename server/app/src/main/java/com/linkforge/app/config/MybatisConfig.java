package com.linkforge.app.config;

import org.apache.ibatis.type.InstantTypeHandler;
import org.apache.ibatis.type.JapaneseDateTypeHandler;
import org.apache.ibatis.type.LocalDateTimeTypeHandler;
import org.apache.ibatis.type.LocalDateTypeHandler;
import org.apache.ibatis.type.LocalTimeTypeHandler;
import org.apache.ibatis.type.MonthTypeHandler;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.apache.ibatis.type.OffsetTimeTypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.YearMonthTypeHandler;
import org.apache.ibatis.type.YearTypeHandler;
import org.apache.ibatis.type.ZonedDateTimeTypeHandler;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.JapaneseDate;

/**
 * 应用级 MyBatis 公共类型处理器装配。
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

    /** 注册 UTC instant 和常用 Java 时间类型的显式 TypeHandler。 */
    @Bean
    ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(Instant.class, InstantTypeHandler.class);
            registry.register(JapaneseDate.class, JapaneseDateTypeHandler.class);
            registry.register(LocalDate.class, LocalDateTypeHandler.class);
            registry.register(LocalDateTime.class, LocalDateTimeTypeHandler.class);
            registry.register(LocalTime.class, LocalTimeTypeHandler.class);
            registry.register(Month.class, MonthTypeHandler.class);
            registry.register(OffsetDateTime.class, OffsetDateTimeTypeHandler.class);
            registry.register(OffsetTime.class, OffsetTimeTypeHandler.class);
            registry.register(Year.class, YearTypeHandler.class);
            registry.register(YearMonth.class, YearMonthTypeHandler.class);
            registry.register(ZonedDateTime.class, ZonedDateTimeTypeHandler.class);
        };
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
